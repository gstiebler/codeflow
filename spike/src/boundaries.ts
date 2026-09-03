import type { Boundary, MethodFact, Model, NodeFact } from './types.ts';
import { stmtKey } from './load.ts';

/**
 * Where a value enters or leaves the process.
 *
 * These rules are the whole reason the view is worth anything. A dataflow tree with no boundaries
 * runs until the reader gives up; a tree that stops at "an HTTP request came in here" and "a row was
 * written there" is a claim about the system that a reviewer can check against the ticket. There are
 * seven rules and there should stay seven: each one was chosen because a sampled Fineract change
 * crossed it, and every rule that fires on nothing is a rule that costs reading time for free.
 *
 * Framework semantics are invisible to Joern - it sees an annotation, not a servlet - so all seven
 * are string-matching on annotations, receiver types and callee names. That is the honest ceiling of
 * a source-only tool, and it is also why the rules live here in a tested TypeScript file rather than
 * inside the Joern script where nothing could exercise them.
 */

const HTTP_VERBS = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'HEAD', 'OPTIONS'];

const REPOSITORY_READ = /^(find|get|count|exists|stream|read|search|retrieve)/;
const REPOSITORY_WRITE = /^(save|delete|remove|flush|persist|merge|insert|update)/;

const JDBC_READ = /^(query|stream)/;
const JDBC_WRITE = /^(update|execute|batchUpdate)/;

/**
 * Calls that throw with no `throw` in the source.
 *
 * Keyed by the callee's simple name and the receiver's simple type, because the CPG is built
 * no-delombok and so a great many receivers arrive as `<unresolvedNamespace>.Optional`. Short on
 * purpose: every entry is a way Fineract code actually fails, and the first was found by reading the
 * commit this spike has to score on.
 */
const IMPLICIT_THROWS: { receiver: RegExp; name: RegExp; throws: string }[] = [
  { receiver: /(^|\.)Optional$/, name: /^(get|orElseThrow)$/, throws: 'NoSuchElementException' },
  { receiver: /(^|\.)Iterator$/, name: /^next$/, throws: 'NoSuchElementException' },
  { receiver: /(^|\.)(List|ArrayList|LinkedList)$/, name: /^get$/, throws: 'IndexOutOfBoundsException' },
  { receiver: /(^|\.)Objects$/, name: /^requireNonNull$/, throws: 'NullPointerException' },
];

/**
 * The simple name at the end of a dotted or generic type, e.g. `a.b.Optional<X>` to `Optional`.
 *
 * The generic suffix is anchored to the end rather than stripped from the first `<`, because half the
 * receivers in a no-delombok CPG are literally `<unresolvedNamespace>.Optional` and an unanchored
 * strip erases the type name along with the marker.
 */
function simpleName(type: string): string {
  return type.replace(/<.*>$/, '').replace(/^.*\./, '');
}

function annotationValue(m: { annotations: { name: string; value?: string }[] }, name: string): string | undefined {
  return m.annotations.find((a) => a.name === name)?.value?.replace(/^"|"$/g, '');
}

/** `POST /loans/{id}/charge`, from the class-level @Path, the method-level @Path, and the verb. */
function endpointLabel(m: MethodFact, classPath: string | undefined): string | undefined {
  const verb = m.annotations.map((a) => a.name).find((n) => HTTP_VERBS.includes(n));
  if (!verb) return undefined;
  const parts = [classPath ?? '', annotationValue(m, 'Path') ?? ''].map((p) => p.replace(/^\/|\/$/g, '')).filter(Boolean);
  return `${verb} /${parts.join('/')}`;
}

function thrownType(code: string): string {
  const constructed = /throw\s+new\s+([\w.]+)/.exec(code);
  return constructed ? `throws ${simpleName(constructed[1]!)}` : 'rethrows';
}

/** The first string literal among a call's arguments - the JSON key of a `*ValueOfParameterNamed`. */
function firstLiteral(m: Model, node: NodeFact): string | undefined {
  for (const id of node.args ?? []) {
    const arg = m.byId.get(id);
    if (arg?.label === 'LITERAL' && arg.code.startsWith('"')) return arg.code.replace(/^"|"$/g, '');
  }
  return undefined;
}

export function boundaries(m: Model): Boundary[] {
  const found: Boundary[] = [];
  const seen = new Set<string>();
  const add = (node: string, dir: Boundary['dir'], kind: Boundary['kind'], label: string) => {
    const key = `${dir}:${kind}:${label}`;
    if (seen.has(`${key}@${node}`)) return;
    seen.add(`${key}@${node}`);
    found.push({ key, node, stmt: m.stmtOfNode.get(node) ?? node, dir, kind, label });
  };

  // 1 and 2. HTTP endpoints. The declaration is where the request's values enter; every return is
  //          where the response leaves. Both carry the same label, so a diff pairs them by name.
  const classPathOf = new Map(m.graph.types.map((t) => [t.fullName, annotationValue(t, 'Path')]));
  for (const method of m.graph.methods) {
    const label = endpointLabel(method, classPathOf.get(method.typeFullName));
    if (!label) continue;
    // On the parameters rather than the signature: the request's values enter one per parameter, and
    // at node granularity the signature itself carries nothing. Parameter 0 is `this`, which is the
    // resource bean, not anything the caller sent.
    for (const p of method.params) if (p.index > 0) add(p.id, 'in', 'net', label);
    for (const r of method.returns) add(r, 'out', 'net', `${label} returns`);
  }
  void stmtKey;

  for (const node of m.graph.nodes) {
    if (node.label !== 'CALL' || !node.name) continue;
    const at = node.id;
    const produced = node.id;
    const receiver = simpleName(node.receiverType ?? '');

    // 3 and 4. Spring Data repositories: the persistent store, seen through a proxy Joern cannot
    //          look inside, which is exactly why the interface name has to stand in for the table.
    if (receiver.endsWith('Repository')) {
      if (REPOSITORY_WRITE.test(node.name)) add(at, 'out', 'db', `${receiver}.${node.name}`);
      else if (REPOSITORY_READ.test(node.name)) add(produced, 'in', 'db', `${receiver}.${node.name}`);
    }

    // 5. Hand-written SQL.
    if (receiver.endsWith('JdbcTemplate') || receiver.endsWith('NamedParameterJdbcTemplate')) {
      if (JDBC_WRITE.test(node.name)) add(at, 'out', 'db', `jdbc.${node.name}`);
      else if (JDBC_READ.test(node.name)) add(produced, 'in', 'db', `jdbc.${node.name}`);
    }

    // 6. Exceptions are outputs. Explicit first, then the JDK calls that throw on their own.
    if (node.name === '<operator>.throw') {
      add(at, 'out', 'throw', thrownType(node.code));
    } else {
      const implicit = IMPLICIT_THROWS.find((r) => r.receiver.test(receiver) && r.name.test(node.name!));
      if (implicit) add(at, 'out', 'throw', `throws ${implicit.throws}`);
    }

    // 7. Fineract reads its request bodies through JsonCommand, one string-keyed getter per field.
    //    Without this the whole write path looks like it has no input at all.
    if (/ValueOfParameterNamed/.test(node.name)) {
      const key = firstLiteral(m, node);
      if (key) add(produced, 'in', 'json', `json ${key}`);
    }
  }

  return found;
}
