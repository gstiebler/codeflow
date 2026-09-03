import type { Model, ValueEdge } from './types.ts';

/**
 * Cycles in the value graph, collapsed to one node apiece.
 *
 * A fixed-point calculation - Fineract's EMI solver is the motivating one - is a genuine cycle in the
 * dataflow: the EMI feeds the interest and the interest feeds the EMI. Walked naively, a tree through
 * it never terminates. Collapsed, it becomes a single row a reader can expand, and it says something
 * true and useful in one line: *a change to any statement in here potentially changes all of them.*
 * That is why the collapse belongs at query time and not in the extractor - the cycle is a fact about
 * the graph, but the decision to fold it is a fact about what a reader can hold in their head.
 *
 * Tarjan, written with an explicit stack. Fineract's call graph nests deep enough that a recursive
 * one overflows, and this is not the place to find that out.
 *
 * One subtlety, found the hard way. Return edges are excluded from cycle detection, though not from
 * slicing. At this grain a call statement is a single node that both sends arguments and receives the
 * result, so `t = svc.apply(x)` closes a loop through every callee: argument in, return back, done -
 * and without this exclusion the first fixture produced a five-statement "cycle" spanning two files
 * that is not a cycle in the program at all. It is the realizable-paths problem that context-sensitive
 * slicers solve with call strings, and dropping the return edge is the cheap version: real recursion
 * still shows up, because that closes through argument edges, and the artefact does not. The cost is
 * that the condensation is no longer a DAG, so every walk over it needs a visited set anyway.
 */

export type Condensation = {
  /** Node id to component id. A node in no cycle maps to itself. */
  componentOf: Map<string, string>;
  /** Component id to its member nodes, in the order Tarjan found them. */
  members: Map<string, string[]>;
  /** The edges between components, with self-loops removed. */
  edges: ValueEdge[];
  out: Map<string, ValueEdge[]>;
  in: Map<string, ValueEdge[]>;
};

export function condense(m: Model): Condensation {
  const succ = new Map<string, string[]>();
  for (const e of m.edges) {
    if (e.kind === 'ret') continue;
    if (!succ.has(e.from)) succ.set(e.from, []);
    succ.get(e.from)!.push(e.to);
  }

  const index = new Map<string, number>();
  const low = new Map<string, number>();
  const onStack = new Set<string>();
  const stack: string[] = [];
  const componentOf = new Map<string, string>();
  const members = new Map<string, string[]>();
  let next = 0;

  for (const root of m.byId.keys()) {
    if (index.has(root)) continue;
    // Each frame is a node and how far through its successors we have walked.
    const work: { node: string; at: number }[] = [{ node: root, at: 0 }];
    index.set(root, next);
    low.set(root, next);
    next += 1;
    stack.push(root);
    onStack.add(root);

    while (work.length) {
      const frame = work[work.length - 1]!;
      const kids = succ.get(frame.node) ?? [];
      if (frame.at < kids.length) {
        const kid = kids[frame.at]!;
        frame.at += 1;
        if (!index.has(kid)) {
          index.set(kid, next);
          low.set(kid, next);
          next += 1;
          stack.push(kid);
          onStack.add(kid);
          work.push({ node: kid, at: 0 });
        } else if (onStack.has(kid)) {
          low.set(frame.node, Math.min(low.get(frame.node)!, index.get(kid)!));
        }
        continue;
      }
      work.pop();
      const parent = work[work.length - 1];
      if (parent) low.set(parent.node, Math.min(low.get(parent.node)!, low.get(frame.node)!));
      if (low.get(frame.node) === index.get(frame.node)) {
        const group: string[] = [];
        for (;;) {
          const popped = stack.pop()!;
          onStack.delete(popped);
          group.push(popped);
          if (popped === frame.node) break;
        }
        // The component is named after its lowest-numbered member, so the name is stable across runs
        // and reads as a real statement rather than a counter.
        const id = group.slice().sort()[0]!;
        members.set(id, group);
        for (const g of group) componentOf.set(g, id);
      }
    }
  }

  const at = (k: string) => componentOf.get(k) ?? k;
  const edges: ValueEdge[] = [];
  const seen = new Set<string>();
  for (const e of m.edges) {
    const from = at(e.from);
    const to = at(e.to);
    if (from === to) continue;
    const id = `${from} ${to} ${e.kind} ${e.carrier}`;
    if (seen.has(id)) continue;
    seen.add(id);
    edges.push({ ...e, from, to });
  }

  const out = new Map<string, ValueEdge[]>();
  const inn = new Map<string, ValueEdge[]>();
  for (const e of edges) {
    if (!out.has(e.from)) out.set(e.from, []);
    out.get(e.from)!.push(e);
    if (!inn.has(e.to)) inn.set(e.to, []);
    inn.get(e.to)!.push(e);
  }

  return { componentOf, members, edges, out, in: inn };
}

/** How many statements a component holds. One means it is not a cycle. */
export function size(c: Condensation, id: string): number {
  return c.members.get(id)?.length ?? 1;
}
