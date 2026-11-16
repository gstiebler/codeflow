import { test } from 'node:test';
import assert from 'node:assert/strict';
import ELK from 'elkjs/lib/elk.bundled.js';
import { flatElkGraph, positions, stabilise, type ElkNode, type Point } from '../../../main/resources/viewer/layout.ts';
import type { CondensedNode, CondensedView } from '../../../main/resources/viewer/types.ts';

const leaf = (id: string): CondensedNode => ({ id, label: id, type: 'VARIABLE' });
const flow = (source: string, target: string) => ({ source, target, kinds: { FLOW: 1 }, count: 1 });
const caption = (node: CondensedNode) => node.label;

const lay = async (problem: ElkNode) =>
  new Map(positions(await new ELK().layout(problem)).map((p) => [p.id, { x: p.x, y: p.y }]));

/** Pairs of nodes drawn both times whose left/right or above/below order flipped. */
function inversions(before: Map<string, Point>, after: Map<string, Point>): string[] {
  const kept = [...before.keys()].filter((id) => after.has(id));
  const flipped: string[] = [];
  for (const [i, a] of kept.entries()) {
    for (const b of kept.slice(i + 1)) {
      const [p, q, r, s] = [before.get(a)!, before.get(b)!, after.get(a)!, after.get(b)!];
      if ((p.x - q.x) * (r.x - s.x) < 0 || (p.y - q.y) * (r.y - s.y) < 0) flipped.push(`${a}/${b}`);
    }
  }
  return flipped;
}

// `a` feeds `b` and `c`; a click puts `d` under `b`. ELK from scratch swaps `b` and `c` to make room -
// the jump that loses the reader. Seeded with the drawing before, every old pair keeps its order.
const before: CondensedView = { nodes: [leaf('a'), leaf('b'), leaf('c')], edges: [flow('a', 'b'), flow('a', 'c')] };
const after: CondensedView = { nodes: [...before.nodes, leaf('d')], edges: [...before.edges, flow('b', 'd')] };

test('a one-neighbour reveal keeps every old node in its order', async () => {
  const drawn = await lay(flatElkGraph(before, caption));
  const fresh = await lay(flatElkGraph(after, caption));
  assert.notDeepEqual(inversions(drawn, fresh), [], 'ELK from scratch keeps the order here, so this payload proves nothing');
  const seeded = await lay(flatElkGraph(after, caption, drawn));
  assert.deepEqual(inversions(drawn, seeded), []);
  assert.ok(seeded.has('d'));
});

// What the seeding writes, so a box or a node that loses it is caught without running ELK: each old
// node at its old place, a new one half a pixel past its first old neighbour, the options on every
// graph and as strings, and the input untouched.
test('stabilise seats old nodes where they were and new ones beside a neighbour', () => {
  const previous = new Map([['a', { x: 10, y: 0 }], ['b', { x: 0, y: 66 }]]);
  const root: ElkNode = {
    id: 'root', layoutOptions: { algorithm: 'layered' },
    children: [
      { id: 'a', width: 20, height: 26 },
      { id: 'box', layoutOptions: { algorithm: 'layered' }, children: [{ id: 'inner', width: 20, height: 26 }] },
      { id: 'b', width: 20, height: 26 },
      { id: 'c', width: 20, height: 26 },
      { id: 'loose', width: 20, height: 26 },
    ],
    edges: [{ id: 'b->c', sources: ['b'], targets: ['c'] }, { id: 'a->inner', sources: ['a'], targets: ['inner'] }],
  };
  const copy = structuredClone(root);
  const seeded = stabilise(root, previous);
  const at = (id: string) => {
    const find = (n: ElkNode): ElkNode | undefined => (n.id === id ? n : n.children?.map(find).find(Boolean));
    const node = find(seeded)!;
    return [node.x, node.y, node.layoutOptions?.['elk.position']];
  };
  assert.deepEqual(at('a'), [10, 0, '(10,0)']);
  assert.deepEqual(at('c'), [0.5, 66.5, '(0.5,66.5)']);
  // `inner`'s neighbour is outside its box, where a position means nothing; `loose` has none at all.
  assert.deepEqual(at('inner'), [undefined, undefined, undefined]);
  assert.deepEqual(at('loose'), [undefined, undefined, undefined]);
  for (const graph of [seeded, seeded.children![1]]) {
    assert.equal(graph.layoutOptions!['elk.layered.layering.strategy'], 'INTERACTIVE');
    assert.equal(graph.layoutOptions!['elk.layered.crossingMinimization.semiInteractive'], 'true');
    for (const value of Object.values(graph.layoutOptions!)) assert.equal(typeof value, 'string');
  }
  assert.deepEqual(root, copy);
});
