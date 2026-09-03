import type { Boundary, Model, ValueEdge } from './types.ts';
import type { Condensation } from './scc.ts';
import { nodeFingerprinter, setName } from './fingerprint.ts';

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
export type Pair = {
  source: Boundary;
  sink: Boundary;
  /** One shortest witness, to show. */
  path: Step[];
  /** A name for every statement any witness could cross. See `interiorOf`. */
  interior: string;
};

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
 * Every input-to-output pair in the corpus, and the code each one runs through.
 *
 * This is the expensive call - one breadth-first walk per input boundary - and it is the one a PR
 * diff needs, because the thing a reviewer wants to know is not what the graph looks like but which
 * of these pairs the change added, removed, or re-routed.
 *
 * The re-routed ones are why a pair carries an interior as well as a witness. A witness is one path
 * out of many, chosen by the walk; comparing two versions' witnesses compares two arbitrary choices,
 * and measured on Fineract d602928ae that is what most of the `PATH` verdicts turned out to be. The
 * interior is a property of the pair itself: every statement that lies on *some* route from this
 * input to this output. Two builds whose interiors agree run the same code between the same two
 * boundaries, whichever route a reader traces.
 *
 * Done in two passes so only one backward set is alive at a time. On the 254k-node corpus the
 * backward sets average thirteen thousand components each and there are fifteen hundred of them;
 * holding them all would cost more memory than the CPG.
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

  // Every statement a component stands for. A cycle collapses up to fifty of them into one node and
  // all fifty are interior, so the fold is over members, not over components.
  const fp = nodeFingerprinter(m);
  const inside = new Map<string, string[]>();
  for (const [id, members] of c.members) inside.set(id, [...new Set(members.map(fp))]);

  const found: { source: Boundary; sink: Boundary; path: Step[]; at: string; ahead: Set<string> }[] = [];
  for (const group of byLabel.values()) {
    const { parent, order } = walk(c, startsOf(c, group.map((b) => b.node)), 'out');
    const ahead = new Set(order);
    for (const at of order) {
      for (const sink of index.outAt.get(at) ?? []) {
        found.push({ source: group[0]!, sink, path: pathTo(parent, at), ahead, at });
      }
    }
  }

  const bySink = new Map<string, typeof found>();
  for (const f of found) {
    if (!bySink.has(f.at)) bySink.set(f.at, []);
    bySink.get(f.at)!.push(f);
  }

  const pairs: Pair[] = [];
  for (const [at, group] of bySink) {
    const behind = new Set(walk(c, [at], 'in').order);
    for (const f of group) {
      pairs.push({ source: f.source, sink: f.sink, path: f.path, interior: interiorOf(f.ahead, behind, inside) });
    }
  }
  return pairs;
}

/**
 * A name for the statements between a source and a sink.
 *
 * A component lies on some route from the source to the sink exactly when it is reachable forwards
 * from the one and backwards from the other, so the intersection of the two walks is the answer -
 * exact, and two walks rather than an enumeration of paths, of which there are usually infinitely
 * many.
 */
function interiorOf(ahead: Set<string>, behind: Set<string>, inside: Map<string, string[]>): string {
  const [small, large] = ahead.size <= behind.size ? [ahead, behind] : [behind, ahead];
  const crossed = new Set<string>();
  for (const component of small) {
    if (!large.has(component)) continue;
    for (const f of inside.get(component) ?? []) crossed.add(f);
  }
  return setName(crossed);
}
