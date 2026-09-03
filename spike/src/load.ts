import type { Graph, Model, NodeFact, Stmt, ValueEdge } from './types.ts';

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
 * Four edge kinds, and four is the whole model:
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
  for (const m of g.methods) {
    const stmt = stmts.get(stmtKey(m.file, m.line));
    if (stmt && stmt.method === m.fullName) {
      stmt.code = oneLine(`${m.typeName}.${m.name}(${m.params.map((p) => p.name).join(', ')})`);
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

  // 1. Def-use. Joern hangs the first use of every parameter and local off the METHOD node itself;
  //    those edges say only "this method has a body" and are dropped.
  for (const e of g.edges) {
    const from = byId.get(e.from);
    if (!from || from.label === 'METHOD') continue;
    if (e.kind === 'cdg') add(e.from, e.to, `guard ${oneLine(from.code)}`, 'guard');
    else add(e.from, e.to, carrierOf(from), 'local');
  }

  // 2. Composition, and the call exception.
  for (const node of g.nodes) {
    const args = node.args ?? [];
    if (!args.length) continue;
    const callee = node.methodFullName ? methodByFullName.get(node.methodFullName) : undefined;

    if (callee) {
      for (const p of callee.params) {
        const arg = args[p.index];
        if (arg) add(arg, p.id, `arg ${p.index} ${p.name}`, 'arg');
      }
      for (const r of callee.returns) add(r, node.id, `return of ${callee.name}()`, 'ret');
      continue;
    }

    // An assignment runs the other way round: the right-hand side reaches the statement, and the
    // statement defines the left-hand side.
    if (node.name === '<operator>.assignment' && args.length >= 2) {
      for (const rhs of args.slice(1)) {
        const from = byId.get(rhs);
        if (from) add(rhs, node.id, carrierOf(from), 'local');
      }
      add(node.id, args[0]!, '=', 'local');
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
    const lhs = byId.get(node.args[0]!);
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
