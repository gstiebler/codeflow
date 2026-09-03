import type { Boundary, Model, ValueEdge } from './types.ts';
import type { Condensation } from './scc.ts';

/**
 * The two questions the panel answers, and the third the diff is built on.
 *
 * `forward` is "who reads what this line wrote?". `backward` is "where did the values this line reads
 * come from?". Both stop at a boundary, because a boundary is the point past which the answer stops
 * being about this program. `allPairs` runs `forward` from every input and records where it landed:
 * that list, and not the graph, is what a PR diff compares.
 *
 * Breadth-first with a parent map, so the path reported is a shortest one. Shortest is a deliberate
 * choice over all-paths: a reader wants the reason this input reaches that output, and the count of
 * distinct routes between two boundaries is both unbounded and uninteresting. Whether *any* route
 * exists is the claim, and one witness proves it.
 *
 * A walk starts from a set of nodes, not one. A source line holds several graph nodes and an endpoint
 * holds one input per parameter, so "what does this reach?" is a question about all of them at once;
 * running one walk per node and unioning the answers would report the same output several times over.
 *
 * The walk records boundaries but does not stop at them. It is tempting to stop - the rendered tree
 * does - but a database read sits in the middle of most Fineract write paths, and a walk that halted
 * there would report that no request ever reaches a save. Stopping is a rendering decision; reaching
 * is a fact about the program.
 */

export type Step = { node: string; via: ValueEdge | undefined };
export type Hit = { boundary: Boundary; path: Step[] };
export type Pair = { source: Boundary; sink: Boundary; path: Step[] };

type Index = {
  /** Boundaries at a component, by direction. */
  inAt: Map<string, Boundary[]>;
  outAt: Map<string, Boundary[]>;
};

/** Group boundaries by the component their statement collapsed into. */
export function indexBoundaries(c: Condensation, bs: Boundary[]): Index {
  const inAt = new Map<string, Boundary[]>();
  const outAt = new Map<string, Boundary[]>();
  for (const b of bs) {
    const at = c.componentOf.get(b.node) ?? b.node;
    const bucket = b.dir === 'in' ? inAt : outAt;
    if (!bucket.has(at)) bucket.set(at, []);
    bucket.get(at)!.push(b);
  }
  return { inAt, outAt };
}

function walk(c: Condensation, starts: string[], direction: 'out' | 'in') {
  const parent = new Map<string, { from: string; via: ValueEdge }>();
  const seen = new Set(starts);
  const order: string[] = [...seen];
  const queue = [...seen];
  while (queue.length) {
    const node = queue.shift()!;
    for (const e of (direction === 'out' ? c.out : c.in).get(node) ?? []) {
      const nextNode = direction === 'out' ? e.to : e.from;
      if (seen.has(nextNode)) continue;
      seen.add(nextNode);
      parent.set(nextNode, { from: node, via: e });
      order.push(nextNode);
      queue.push(nextNode);
    }
  }
  return { parent, order };
}

/** Back up the parent map to whichever start this node was found from. */
function pathTo(parent: Map<string, { from: string; via: ValueEdge }>, end: string): Step[] {
  const steps: Step[] = [];
  let at = end;
  for (;;) {
    const step = parent.get(at);
    if (!step) break;
    steps.push({ node: at, via: step.via });
    at = step.from;
  }
  steps.push({ node: at, via: undefined });
  return steps.reverse();
}

/** Where a walk actually begins: the components the given nodes collapsed into. */
function startsOf(c: Condensation, node: string | string[]): string[] {
  const ids = typeof node === 'string' ? [node] : node;
  return [...new Set(ids.map((id) => c.componentOf.get(id) ?? id))];
}

/** Every output boundary the value at `node` can reach, with a witness path. */
export function forward(c: Condensation, index: Index, node: string | string[]): Hit[] {
  const { parent, order } = walk(c, startsOf(c, node), 'out');
  const hits: Hit[] = [];
  for (const at of order) {
    for (const boundary of index.outAt.get(at) ?? []) {
      hits.push({ boundary, path: pathTo(parent, at) });
    }
  }
  return hits;
}

/** Every input boundary that can reach `node`, with a witness path read start-to-finish. */
export function backward(c: Condensation, index: Index, node: string | string[]): Hit[] {
  const { parent, order } = walk(c, startsOf(c, node), 'in');
  const hits: Hit[] = [];
  for (const at of order) {
    for (const boundary of index.inAt.get(at) ?? []) {
      hits.push({ boundary, path: pathTo(parent, at).reverse() });
    }
  }
  return hits;
}

/**
 * Every input-to-output pair in the corpus.
 *
 * This is the expensive call - one breadth-first walk per input boundary - and it is the one a PR
 * diff needs, because the thing a reviewer wants to know is not what the graph looks like but which
 * of these pairs the change added, removed, or re-routed.
 */
export function allPairs(m: Model, c: Condensation, bs: Boundary[]): Pair[] {
  const index = indexBoundaries(c, bs);
  // One walk per input *label*, not per input node: an endpoint's parameters are separate nodes but
  // one input, and pairing them separately would report the same request-to-response flow twice.
  const byLabel = new Map<string, Boundary[]>();
  for (const b of bs) {
    if (b.dir !== 'in') continue;
    if (!byLabel.has(b.key)) byLabel.set(b.key, []);
    byLabel.get(b.key)!.push(b);
  }

  const pairs: Pair[] = [];
  for (const group of byLabel.values()) {
    for (const hit of forward(c, index, group.map((b) => b.node))) {
      pairs.push({ source: group[0]!, sink: hit.boundary, path: hit.path });
    }
  }
  void m;
  return pairs;
}
