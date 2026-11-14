import type { Graph, MethodFact, Model, NodeFact, Stmt, ValueEdge } from './types.ts';

/**
 * Joern's CPG, turned into the value graph everything downstream walks.
 *
 * **The graph is at node granularity; statements are for display only.** That was learned the
 * expensive way. The first version made a statement the unit, because a statement is what the panel
 * shows - and a Java statement is a line that routinely touches several independent values at once.
 * `Loan t = svc.apply(loan, amount)` both consumes `loan` and produces `t`; a signature holds every
 * parameter; a call holds every argument. Merge any of those and the values merge with them, so
 * after a few hops every input in the corpus "reaches" every output, the pair list says nothing, and
 * a diff of it says less. Three separate patches went in for three separate merges before it was
 * clear they were one bug. Keeping Joern's nodes and folding down to lines at render time has no such
 * cases.
 *
 * Four edge kinds, and four is the whole model (callbacks, below, reuse `arg` and `ret`):
 *
 * 1. **def-use**, straight from the CPG's REACHING_DEF edges.
 * 2. **composition**, every expression flowing into the expression that contains it - except across a
 *    call whose callee is in the corpus, where the arguments go to the callee's parameters and the
 *    callee's returns come back to the call. That exception is what makes an argument the callee
 *    ignores stop at the callee.
 * 3. **fields**, every write of a member reaching every read of it. Joern's own dataflow does not do
 *    this: measured on a Fineract module, a setter-then-getter pair returns zero flows, which is
 *    exactly the shape the rate-factor bug had.
 * 4. **guards**, from the CPG's control-dependence edges.
 *
 * **Callbacks** are part of rule 2, not a fifth rule. Joern models a lambda as a method of its own,
 * `<lambda>N`, and a call that takes one carries a METHOD_REF argument naming it; measured, the
 * lambda's METHOD_REF has `typeFullName` equal to the lambda itself, not the functional interface,
 * so the interface is read off the parameter the lambda is passed to instead. Three cases:
 *
 * - *Into a jar* (`list.forEach(x -> ..)`): the jar's body is invisible, so the receiver and the
 *   other non-callback arguments flow to every declared parameter of the callback (index >= 1; slot
 *   0 is the captured `this`), and the callback's returns flow back to the call. A reference to a
 *   method with no body, `repo::save`, is a node of its own that the same arguments flow into, which
 *   is what lets a boundary sit on it.
 * - *Into the corpus* (`runner.run(v, x -> ..)`): nothing here, the callee's own parameter carries
 *   the callback, and the type of that parameter is recorded against the callback.
 * - *Dispatch* (`cb.call(v)` where `call` has no body): arguments flow to the parameters of every
 *   callback recorded for that interface type, and their returns back. Over-approximate, as the rest
 *   of this file is; it reaches only types that were seen carrying a callback.
 */

const MAX_CODE = 160;

/** `file:line`. What a node is *shown* as; never what it is identified by. */
export function stmtKey(nodeOrFile: NodeFact | string, line?: number): string {
  if (typeof nodeOrFile === 'string') return `${nodeOrFile}:${line}`;
  return `${nodeOrFile.file}:${nodeOrFile.line}`;
}

function oneLine(code: string): string {
  const flat = code.replace(/\s+/g, ' ').trim();
  return flat.length > MAX_CODE ? `${flat.slice(0, MAX_CODE - 3)}...` : flat;
}

/**
 * What carries a value out of this node.
 *
 * Read off the source node rather than off the edge, because Joern's REACHING_DEF edges arrive with
 * an empty property - measured, not assumed. Losing the carrier would cost the one column a call
 * hierarchy cannot show.
 */
function carrierOf(node: NodeFact): string {
  if (node.member) return `field ${node.member}`;
  if (node.label === 'IDENTIFIER' || node.label === 'METHOD_PARAMETER_IN') {
    return oneLine(node.code).replace(/^.*[\s.]/, '');
  }
  if (node.label === 'RETURN') return 'return';
  if (node.label === 'CALL' && node.name && !node.name.startsWith('<operator>')) return `${node.name}()`;
  return oneLine(node.code);
}

export function buildModel(g: Graph): Model {
  const byId = new Map(g.nodes.map((x) => [x.id, x]));
  const methodByFullName = new Map(g.methods.map((m) => [m.fullName, m]));

  const stmts = new Map<string, Stmt>();
  const stmtOfNode = new Map<string, string>();
  for (const node of g.nodes) {
    if (node.line <= 0) continue;
    const key = stmtKey(node);
    stmtOfNode.set(node.id, key);
    const existing = stmts.get(key);
    const code = oneLine(node.code);
    if (!existing) stmts.set(key, { key, file: node.file, line: node.line, code, method: node.method });
    else if (code.length > existing.code.length) existing.code = code;
  }
  // A signature line otherwise shows one parameter's declaration text, whichever happened to be
  // longest. The signature itself is what a reader expects at the top of a tree.
  //
  // It is found through the parameters rather than through `method.line`, because Joern reports a
  // method as starting at its first annotation: `@POST` on line 15, `@Path` on 16, and the signature
  // a reader would point at on 17.
  for (const m of g.methods) {
    // Parameter 0 is `this`, which Joern places on the method's line, i.e. back at the annotation.
    const lines = m.params.filter((p) => p.index > 0).map((p) => p.line).filter((l) => l > 0);
    const stmt = stmts.get(stmtKey(m.file, lines.length ? Math.min(...lines) : m.line));
    if (stmt && stmt.method === m.fullName) {
      const named = m.params.filter((p) => p.index > 0).map((p) => p.name);
      stmt.code = oneLine(`${m.typeName}.${m.name}(${named.join(', ')})`);
    }
  }

  const edges: ValueEdge[] = [];
  const seen = new Set<string>();
  const add = (from: string, to: string, carrier: string, kind: ValueEdge['kind']) => {
    if (!from || !to || from === to || !byId.has(from) || !byId.has(to)) return;
    const id = `${kind} ${from} ${to}`;
    if (seen.has(id)) return;
    seen.add(id);
    edges.push({ from, to, carrier, kind });
  };

  // Who contains whom. Needed to tell def-use between statements from Joern's wiring inside one.
  const parentOf = new Map<string, string>();
  for (const node of g.nodes) for (const child of node.args ?? []) if (child) parentOf.set(child, node.id);

  // 1. Def-use. Two classes of REACHING_DEF edge are dropped, and both were measured rather than
  //    guessed:
  //
  //    - Edges out of the METHOD node. Joern hangs the first use of every parameter and local off
  //      the method itself; they say only "this method has a body".
  //    - Edges inside a single expression - child to its own container, container back to its child,
  //      or between two arguments of one call. Joern wires all of those, in both directions:
  //      `this.repo.save(loan)` yields `this.repo -> loan` *and* `loan -> this.repo`. Composition
  //      already says how a value moves inside an expression, and it says so in one direction, so
  //      keeping Joern's version buys nothing and costs a two-node cycle on almost every line. In
  //      the tiny fixture it produced eleven of them, one per assignment and one per call.
  for (const e of g.edges) {
    const from = byId.get(e.from);
    if (!from || from.label === 'METHOD') continue;
    if (e.kind === 'cdg') {
      add(e.from, e.to, `guard ${oneLine(from.code)}`, 'guard');
      continue;
    }
    const above = parentOf.get(e.from);
    const below = parentOf.get(e.to);
    if (above === e.to || below === e.from || (above !== undefined && above === below)) continue;
    add(e.from, e.to, carrierOf(from), 'local');
  }

  // Callback targets. A METHOD_REF's `methodFullName` drops the `(n)` arity suffix when it is a
  // reference like `repo::save`, so both sides are keyed without it.
  const unarity = (fullName: string) => fullName.replace(/\(\d+\)$/, '');
  const methodsByBase = new Map<string, MethodFact[]>();
  for (const m of g.methods) {
    const key = unarity(m.fullName);
    methodsByBase.set(key, [...(methodsByBase.get(key) ?? []), m]);
  }
  const targetsOf = (ref: NodeFact | undefined) =>
    ref?.label === 'METHOD_REF' && ref.methodFullName ? (methodsByBase.get(unarity(ref.methodFullName)) ?? []) : [];

  // Which callbacks were handed to a parameter of a corpus method, keyed by that parameter's type.
  const carried = new Map<string, Set<MethodFact>>();
  for (const node of g.nodes) {
    const callee = node.methodFullName ? methodByFullName.get(node.methodFullName) : undefined;
    if (!callee) continue;
    for (const p of callee.params) {
      if (p.type === 'ANY') continue;
      for (const target of targetsOf(byId.get(node.args?.[p.index] ?? ''))) {
        if (!carried.has(p.type)) carried.set(p.type, new Set());
        carried.get(p.type)!.add(target);
      }
    }
  }

  /** The callback's declared parameters take `from`, and its returns come back to `call`. */
  const invoke = (target: MethodFact, from: string[], call: string) => {
    for (const p of target.params) {
      if (p.index < 1) continue;
      for (const arg of from) add(arg, p.id, `callback arg ${p.index} ${p.name}`, 'arg');
    }
    for (const r of target.returns) add(r, call, `return of ${target.name}()`, 'ret');
  };

  // 2. Composition, and the call exception.
  for (const node of g.nodes) {
    const args = node.args ?? [];
    if (!args.length) continue;
    const callee = node.methodFullName ? methodByFullName.get(node.methodFullName) : undefined;

    // A callback handed to a call into a jar: the jar is what invokes it, with what it was given.
    if (!callee && !node.methodFullName?.startsWith('<operator>') && !node.name?.startsWith('<operator>')) {
      const refs = args.filter((a) => byId.get(a)?.label === 'METHOD_REF');
      const given = args.filter((a) => a && byId.has(a) && !refs.includes(a));
      for (const ref of refs) {
        for (const a of given) add(a, ref, 'callback arg', 'arg');
        for (const target of targetsOf(byId.get(ref))) invoke(target, given, node.id);
      }
    }

    if (callee) {
      for (const p of callee.params) {
        const arg = args[p.index];
        if (arg) add(arg, p.id, `arg ${p.index} ${p.name}`, 'arg');
      }
      for (const r of callee.returns) add(r, node.id, `return of ${callee.name}()`, 'ret');
      // A callee with no RETURN node has no body to walk: an interface method, an abstract one, a
      // Spring Data repository whose implementation is a runtime proxy. Resolving the name is not
      // the same as being able to see through it, so it is composed like any call into a jar. Without
      // this, `Loan loan = repo.findById(id)` swallows the request and nothing downstream of a
      // repository read is reachable at all - measured on the tiny fixture, where it removed every
      // pair but one.
      if (callee.returns.length) continue;
      // And an interface call with no body dispatches to whatever callbacks were seen for that type.
      for (const target of carried.get(callee.typeFullName) ?? []) invoke(target, args, node.id);
    }

    // An assignment runs the other way round: the right-hand side reaches the statement, and the
    // statement defines the left-hand side. Joern numbers an operator's operands from 1 - slot 0 is
    // the receiver, and an operator has none - so the target is `args[1]`, never `args[0]`. Reading
    // it as `args[0]` silently produces a graph with no assignments in it at all, and therefore no
    // field writes and no field edges: the exact hop the tool exists to show.
    if (node.name === '<operator>.assignment' && args.length >= 3) {
      for (const rhs of args.slice(2)) {
        const from = byId.get(rhs);
        if (from) add(rhs, node.id, carrierOf(from), 'local');
      }
      add(node.id, args[1]!, '=', 'local');
      continue;
    }

    // Everything else composes: each sub-expression flows into the expression holding it. For a call
    // into a jar that includes the receiver and every argument, because with no body to walk there is
    // no evidence the result is independent of them, and a false edge is cheaper than a missing one.
    for (const child of args) {
      const from = byId.get(child);
      if (from) add(child, node.id, carrierOf(from), 'local');
    }
  }

  // 3. Fields. Deliberately flow-insensitive: without this edge the tool misses the shape it exists
  //    to show, and with it the worst case is an edge a reader can see is wrong.
  const writes = new Map<string, Set<string>>();
  for (const node of g.nodes) {
    if (node.name !== '<operator>.assignment' || !node.args?.length) continue;
    const lhs = byId.get(node.args[1]!);
    if (!lhs?.member) continue;
    if (!writes.has(lhs.member)) writes.set(lhs.member, new Set());
    writes.get(lhs.member)!.add(lhs.id);
  }
  const written = new Set([...writes.values()].flatMap((s) => [...s]));
  for (const node of g.nodes) {
    if (!node.member || written.has(node.id)) continue;
    for (const writeAt of writes.get(node.member) ?? []) add(writeAt, node.id, `field ${node.member}`, 'field');
  }

  return {
    stmts,
    edges,
    byId,
    stmtOfNode,
    methodByFullName,
    typeByName: new Map(g.types.map((t) => [t.name, t])),
    graph: g,
  };
}
