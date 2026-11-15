/**
 * How far a click moves what was already on screen, swept over every fixture - the measurement
 * behind `stabilise` in layout.ts. Not a test: it prints numbers, and docs/decisions/viewer.md
 * records them. Run after `./gradlew test`, which writes the graph.json files it reads:
 *
 *   node app/src/test/js/stability.ts
 *
 * Each fixture is walked as the reader would: in Boxes, every leaf on screen clicked until nothing
 * new appears; in Condensed, every display node until nothing is grouped. Each click that changes
 * what is drawn is laid out once per candidate, each seeded from its own previous drawing, and scored
 * on the nodes drawn both before and after it:
 *
 * - displacement: how far each moved, net of the clicked node's own move, since the page keeps the
 *   clicked node where it was on screen (raw, when the clicked node is gone);
 * - inversions: pairs whose left/right or above/below order flipped.
 */
import ELK from 'elkjs/lib/elk.bundled.js';
import { loadCorpus } from './unit/corpus.ts';
import { opening, screen, tap } from '../../main/resources/viewer/model.ts';
import { condensed, condensedCaption, condensedOpening, tapCondensed } from '../../main/resources/viewer/condensed.ts';
import {
  elkGraph, flatElkGraph, positions, stabilise, type ElkNode, type Point,
} from '../../main/resources/viewer/layout.ts';
import type { Id, Payload } from '../../main/resources/viewer/types.ts';

/** One click: the ELK problem for the view after it, and the node clicked. */
type Step = { problem: ElkNode; clicked: Id };
type Walk = { first: ElkNode; steps: Step[] };

function boxesWalk(payload: Payload): Walk {
  let revealed = opening(payload);
  const steps: Step[] = [];
  const first = elkGraph(screen(payload, revealed));
  for (;;) {
    const before = screen(payload, revealed).showing;
    for (const id of before) {
      const was = screen(payload, revealed).showing.size;
      revealed = tap(payload, revealed, id);
      const view = screen(payload, revealed);
      if (view.showing.size !== was) steps.push({ problem: elkGraph(view), clicked: id });
    }
    if (screen(payload, revealed).showing.size === before.size) return { first, steps };
  }
}

function condensedWalk(payload: Payload): Walk {
  let revealed = condensedOpening();
  const steps: Step[] = [];
  const first = flatElkGraph(condensed(payload, revealed), condensedCaption);
  const leaves = payload.nodes.filter((n) => n.type !== 'METHOD').length;
  for (let round = 0; round <= leaves; round += 1) {
    const before = condensed(payload, revealed);
    if (!before.nodes.some((n) => n.type === 'GROUP')) break;
    for (const node of before.nodes) {
      const was = revealed.size;
      revealed = tapCondensed(payload, revealed, { node: node.id });
      if (revealed.size !== was) steps.push({ problem: flatElkGraph(condensed(payload, revealed), condensedCaption), clicked: node.id });
    }
  }
  return { first, steps };
}

/**
 * The candidate that lost: the spec's model order. Every children list sorted into the old drawing's
 * left-to-right, then top-to-bottom order, a new node just after its first old sibling neighbour,
 * edges after their endpoints, and ELK told to respect that order. Sorting top-to-bottom first was
 * also tried and is worse on both counts.
 */
function byModelOrder(root: ElkNode, previous: Map<Id, Point>): ElkNode {
  const neighbours = new Map<Id, Id[]>();
  for (const e of root.edges ?? []) {
    for (const [a, b] of [[e.sources[0], e.targets[0]], [e.targets[0], e.sources[0]]]) {
      neighbours.set(a, [...(neighbours.get(a) ?? []), b]);
    }
  }
  const rank = new Map<Id, number>();
  const rebuild = (graph: ElkNode): ElkNode => {
    const siblings = new Set((graph.children ?? []).map((c) => c.id));
    const seat = (id: Id) => {
      const old = previous.get(id) ? id : (neighbours.get(id) ?? []).find((n) => siblings.has(n) && previous.has(n));
      return old === undefined ? undefined : { x: previous.get(old)!.x + (old === id ? 0 : 0.5), y: previous.get(old)!.y + (old === id ? 0 : 0.5) };
    };
    const seated = (graph.children ?? []).map((child, index) => ({ child, index, at: seat(child.id) }));
    seated.sort((a, b) => (a.at && b.at ? a.at.x - b.at.x || a.at.y - b.at.y : (a.at ? 0 : 1) - (b.at ? 0 : 1)) || a.index - b.index);
    const children = seated.map(({ child }) => {
      rank.set(child.id, rank.size);
      return child.children ? rebuild(child) : child;
    });
    const layoutOptions = graph.layoutOptions && { ...graph.layoutOptions, 'elk.layered.considerModelOrder.strategy': 'NODES_AND_EDGES' };
    return { ...graph, ...(layoutOptions ? { layoutOptions } : {}), children };
  };
  const rebuilt = rebuild(root);
  const at = (ids: Id[]) => rank.get(ids[0])!;
  rebuilt.edges = [...(root.edges ?? [])].sort((a, b) => at(a.sources) - at(b.sources) || at(a.targets) - at(b.targets));
  return rebuilt;
}

const CANDIDATES: Record<string, (root: ElkNode, previous: Map<Id, Point>) => ElkNode> = {
  'none (before)': (root) => root,
  'model order': byModelOrder,
  'interactive (chosen)': stabilise,
};

const elk = new ELK();

/** Relative positions, as the page keeps them, and absolute ones, for scoring. */
async function lay(problem: ElkNode) {
  const laid = positions(await elk.layout(problem));
  const relative = new Map(laid.map((p) => [p.id, { x: p.x, y: p.y }]));
  const absolute = new Map<Id, Point>();
  for (const p of laid) {
    const base = p.parent ? absolute.get(p.parent)! : { x: 0, y: 0 };
    absolute.set(p.id, { x: base.x + p.x, y: base.y + p.y });
  }
  return { relative, absolute };
}

function score(before: Map<Id, Point>, after: Map<Id, Point>, clicked?: Id) {
  const kept = [...before.keys()].filter((id) => after.has(id));
  const anchor = clicked !== undefined && before.has(clicked) && after.has(clicked)
    ? { x: after.get(clicked)!.x - before.get(clicked)!.x, y: after.get(clicked)!.y - before.get(clicked)!.y }
    : { x: 0, y: 0 };
  let moved = 0;
  for (const id of kept) {
    const a = before.get(id)!;
    const b = after.get(id)!;
    moved += Math.hypot(b.x - a.x - anchor.x, b.y - a.y - anchor.y);
  }
  let inversions = 0;
  for (let i = 0; i < kept.length; i += 1) {
    for (let j = i + 1; j < kept.length; j += 1) {
      const [p, q] = [before.get(kept[i])!, before.get(kept[j])!];
      const [r, s] = [after.get(kept[i])!, after.get(kept[j])!];
      if ((p.x - q.x) * (r.x - s.x) < 0 || (p.y - q.y) * (r.y - s.y) < 0) inversions += 1;
    }
  }
  return { displacement: kept.length ? moved / kept.length : 0, inversions };
}

async function measure(walk: (p: Payload) => Walk, names: string[]) {
  const walks = loadCorpus().map(({ payload }) => walk(payload));
  for (const name of names) {
    let clicks = 0;
    let displacement = 0;
    let inversions = 0;
    for (const { first, steps } of walks) {
      let drawn = await lay(first);
      for (const step of steps) {
        const next = await lay(CANDIDATES[name](step.problem, drawn.relative));
        const scored = score(drawn.absolute, next.absolute, step.clicked);
        clicks += 1;
        displacement += scored.displacement;
        inversions += scored.inversions;
        drawn = next;
      }
    }
    console.log(`  ${name.padEnd(22)} ${clicks} clicks  mean displacement ${(displacement / clicks).toFixed(1)} px  inversions ${(inversions / clicks).toFixed(2)}/click`);
  }
}

console.log('Boxes');
await measure(boxesWalk, ['none (before)', 'interactive (chosen)']);
// Not run: ELK 0.9.3 throws inside a hierarchical layout given any considerModelOrder strategy, from
// a timer, where no await can catch it - so trying it here ends the process.
console.log('  model order            ELK 0.9.3 throws (considerModelOrder with INCLUDE_CHILDREN)');
console.log('Condensed');
await measure(condensedWalk, ['none (before)', 'model order', 'interactive (chosen)']);
