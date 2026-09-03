import type { Graph, Model, NodeFact, Stmt, ValueEdge } from './types.ts';

/**
 * Joern's AST, collapsed into the graph the spec's tree actually walks.
 *
 * Two decisions carry the file. The first is the grain: **a statement is a source line**. Joern hands
 * back a node per sub-expression, which is the right shape for a query engine and the wrong shape for
 * a panel someone reads - `getEmi().minus(getCalculatedDueInterest())` is one thing a reader thinks
 * about, not five. Fineract is formatted one statement per line, so the line is a free and accurate
 * statement boundary; a codebase that chained differently would need the enclosing-expression walk
 * instead, and that is a known limit of the spike, not an oversight.
 *
 * The second is that four of the five edge kinds are built here rather than read off the CPG. Joern's
 * REACHING_DEF edges are intraprocedural only, and its interprocedural dataflow lives in the query
 * engine, not in the graph. Measured against a Fineract module, Joern's own reachability returns zero
 * flows for a setter-then-getter pair - the exact shape the rate-factor bug had. So the argument,
 * return and field edges are ours.
 */

const MAX_CODE = 160;

/** `file:line`. The id every later stage passes around; never a node id. */
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

/** Which statements write which member, found by looking at the left of every assignment. */
function writtenMembers(g: Graph, byId: Map<string, NodeFact>): Map<string, Set<string>> {
  const writes = new Map<string, Set<string>>();
  for (const node of g.nodes) {
    if (node.name !== '<operator>.assignment' || !node.args?.length) continue;
    const lhs = byId.get(node.args[0]!);
    if (!lhs?.member) continue;
    if (!writes.has(lhs.member)) writes.set(lhs.member, new Set());
    writes.get(lhs.member)!.add(stmtKey(node));
  }
  return writes;
}

export function buildModel(g: Graph): Model {
  const byId = new Map(g.nodes.map((x) => [x.id, x]));
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
  // A method declaration line often holds nothing but its parameters, whose code is one parameter's
  // declaration text. Prefer the signature there, so the endpoint row of a tree reads as a signature.
  for (const m of g.methods) {
    const key = stmtKey(m.file, m.line);
    const stmt = stmts.get(key);
    if (stmt && stmt.method === m.fullName) {
      stmt.code = oneLine(`${m.typeName}.${m.name}(${m.params.map((p) => p.name).join(', ')})`);
    }
  }

  const edges: ValueEdge[] = [];
  const seen = new Set<string>();
  const add = (from: string, to: string, carrier: string, kind: ValueEdge['kind']) => {
    if (!from || !to || from === to) return;
    const id = `${kind} ${from} ${to} ${carrier}`;
    if (seen.has(id)) return;
    seen.add(id);
    edges.push({ from, to, carrier, kind });
  };

  // 1. Local def-use, straight from the CPG. Joern hangs the first use of every parameter and local
  //    off the METHOD node itself; those edges say only "this method has a body" and are dropped.
  for (const e of g.edges) {
    const from = byId.get(e.from);
    const to = byId.get(e.to);
    if (!from || !to || from.label === 'METHOD') continue;
    const a = stmtOfNode.get(e.from);
    const b = stmtOfNode.get(e.to);
    if (!a || !b) continue;
    if (e.kind === 'cdg') add(a, b, `guard ${oneLine(from.code)}`, 'guard');
    else add(a, b, carrierOf(from), 'local');
  }

  // 2. Arguments into parameters, and returns back to the call site. Only for calls resolved to a
  //    method that is in the corpus - a call into a jar has no body here to walk into.
  const methodByFullName = new Map(g.methods.map((m) => [m.fullName, m]));
  for (const node of g.nodes) {
    if (node.label !== 'CALL' || !node.methodFullName) continue;
    const callee = methodByFullName.get(node.methodFullName);
    if (!callee) continue;
    const at = stmtOfNode.get(node.id);
    if (!at) continue;
    const args = node.args ?? [];
    for (const p of callee.params) {
      const arg = byId.get(args[p.index] ?? '');
      const argAt = arg && stmtOfNode.get(arg.id);
      if (argAt) add(argAt, stmtKey(callee.file, callee.line), `arg ${p.index} ${p.name}`, 'arg');
    }
    for (const r of callee.returns) {
      const retAt = stmtOfNode.get(r);
      if (retAt) add(retAt, at, `return of ${callee.name}()`, 'ret');
    }
  }

  // 3. Fields. Every statement writing a member reaches every statement reading it. Deliberately
  //    flow-insensitive: without this edge the tool misses the shape it exists to show, and with it
  //    the worst case is an edge a reader can see is wrong. A read on the writing statement itself is
  //    skipped by the self-edge guard, so `total = total.add(x)` does not link to itself.
  const writes = writtenMembers(g, byId);
  for (const node of g.nodes) {
    if (!node.member) continue;
    const readAt = stmtOfNode.get(node.id);
    if (!readAt) continue;
    for (const writeAt of writes.get(node.member) ?? []) add(writeAt, readAt, `field ${node.member}`, 'field');
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
