import { test } from 'node:test';
import assert from 'node:assert/strict';
import ELK from 'elkjs/lib/elk.bundled.js';
import { condensed, focusOf, focusPath } from '../../../main/resources/viewer/condensed.ts';
import { FOCUS_LANE, flatElkGraph, positions, routes } from '../../../main/resources/viewer/layout.ts';
import { diffViews, type Drawn } from '../../../main/resources/viewer/transition.ts';
import type { EdgeKind, Id, Payload, PayloadNode } from '../../../main/resources/viewer/types.ts';

const BOX = 'box';
const leaf = (id: string): PayloadNode => ({ id, label: id, type: 'VARIABLE', parent: BOX });
const edge = (source: string, target: string, kind: EdgeKind = 'FLOW') => ({ source, target, kind });

/** The sketch: `top` feeds `a` and `b`, both feed `bottom`; `bottom` then runs before `later` (ORDER). */
const DIAMOND: Payload = {
  nodes: [{ id: BOX, label: 'main', type: 'METHOD', method: 'p.App#main()' }, ...['top', 'a', 'b', 'bottom', 'later'].map(leaf)],
  edges: [edge('top', 'a'), edge('top', 'b'), edge('a', 'bottom'), edge('b', 'bottom'), edge('bottom', 'later', 'ORDER')],
};
const ALL = new Set(['top', 'a', 'b', 'bottom', 'later']);
const pathOf = (revealed: Set<Id>, target: Parameters<typeof focusOf>[2]) =>
  focusPath(DIAMOND, revealed, focusOf(DIAMOND, revealed, target)!);
const pairs = (edges: Map<Id, Set<Id>>) => [...edges].flatMap(([s, ts]) => [...ts].map((t) => `${s}->${t}`)).sort();

test('a click on a leaf puts what flows into it and what it flows into on the path, and not the other branch', () => {
  const a = pathOf(ALL, { node: 'a' });
  assert.deepEqual([...a.nodes].sort(), ['a', 'bottom', 'top']);
  assert.deepEqual(pairs(a.edges), ['a->bottom', 'top->a']);
  // The pair: the other branch is the other way round.
  assert.deepEqual([...pathOf(ALL, { node: 'b' }).nodes].sort(), ['b', 'bottom', 'top']);
});

test('a click on an edge: what flows into its source, the edge, what its target flows into', () => {
  const path = pathOf(ALL, { edge: ['a', 'bottom'] });
  assert.deepEqual([...path.nodes].sort(), ['a', 'bottom', 'top']);
  assert.deepEqual(pairs(path.edges), ['a->bottom', 'top->a']);
});

test('ORDER is not walked: it says which call ran first, not where a value goes', () => {
  assert.ok(!pathOf(ALL, { node: 'bottom' }).nodes.has('later'));
  // The pair: a FLOW edge in its place is walked.
  const flowing = { ...DIAMOND, edges: [...DIAMOND.edges.slice(0, 4), edge('bottom', 'later')] };
  assert.ok(focusPath(flowing, ALL, focusOf(flowing, ALL, { node: 'bottom' })!).nodes.has('later'));
});

test('a leaf still in its group puts the group on the path, and the walk goes through it', () => {
  const revealed = new Set(['a', 'b', 'bottom', 'later']);
  const path = pathOf(revealed, { node: 'bottom' });
  assert.deepEqual([...path.nodes].sort(), ['a', 'b', 'bottom', BOX].sort());
  // Every node on the path is drawn: the lane holds nothing the view does not.
  const drawn = new Set(condensed(DIAMOND, revealed).nodes.map((n) => n.id));
  for (const id of path.nodes) assert.ok(drawn.has(id), `${id} is on the path but not drawn`);
});

test('a click on a group focuses nothing: it stands for values that need not be related', () => {
  assert.equal(focusOf(DIAMOND, new Set(), { node: BOX }), null);
  assert.notEqual(focusOf(DIAMOND, ALL, { node: 'a' }), null);
});

test('the path is laid out in a lane, and a line inside it comes back in the root frame', async () => {
  const view = condensed(DIAMOND, ALL);
  const path = pathOf(ALL, { node: 'a' });
  const laid = await new ELK().layout(flatElkGraph(view, (n) => n.label, undefined, path.nodes));
  const at = new Map(positions(laid).map((p) => [p.id, p]));
  assert.equal(at.get('a')!.parent, FOCUS_LANE);
  assert.equal(at.get('b')!.parent, undefined);
  const lane = at.get(FOCUS_LANE)!;
  const route = routes(laid).get('top->a')!;
  const top = at.get('top')!;
  // The line leaves `top`'s bottom edge, on the page.
  assert.equal(route[0].y, lane.y + top.y + top.height);
  // The pair: no path, no lane.
  const plain = await new ELK().layout(flatElkGraph(view, (n) => n.label));
  assert.ok(!positions(plain).some((p) => p.id === FOCUS_LANE));
});

test('a kept node that changes box starts where it was on the page', () => {
  const prev: Drawn = new Map([['n', { x: 100, y: 50 }]]);
  const next: Drawn = new Map([[FOCUS_LANE, { x: 40, y: 20 }], ['n', { x: 5, y: 5, parent: FOCUS_LANE }]]);
  assert.deepEqual(diffViews(prev, next).start.get('n'), { x: 60, y: 30 });
});
