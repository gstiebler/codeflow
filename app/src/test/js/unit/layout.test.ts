import { test } from 'node:test';
import assert from 'node:assert/strict';
import ELK from 'elkjs/lib/elk.bundled.js';
import { curvedPath, flatElkGraph, nodeWidth, positions, routes, type ElkNode } from '../../../main/resources/viewer/layout.ts';
import type { CondensedView } from '../../../main/resources/viewer/types.ts';

test('positions flattens the laid-out tree, keeping each parent', () => {
  const laid: ElkNode = {
    id: 'root',
    children: [{
      id: 'm', x: 0, y: 0, width: 200, height: 100,
      children: [{ id: 'x', x: 10, y: 20, width: 90, height: 30 }],
    }],
  };
  assert.deepEqual(positions(laid), [
    { id: 'm', x: 0, y: 0, width: 200, height: 100, parent: undefined },
    { id: 'x', x: 10, y: 20, width: 90, height: 30, parent: 'm' },
  ]);
});

// One level, every node sized from the caption it is drawn with, and an edge id per ordered pair.
test('the layout is flat, sized from the captions, one edge per ordered pair', () => {
  const root = flatElkGraph({
    nodes: [
      { id: 'b0', label: 'main', type: 'GROUP', members: 12 },
      { id: 'x', label: 'x', type: 'VARIABLE' },
    ],
    edges: [
      { source: 'x', target: 'b0', kinds: { FLOW: 2, ORDER: 1 }, count: 3 },
      { source: 'b0', target: 'x', kinds: { FLOW: 1 }, count: 1 },
    ],
    frames: [],
  }, (node) => (node.type === 'GROUP' ? `${node.label} (${node.members})` : node.label));
  assert.deepEqual(root.children!.map((c) => [c.id, c.width, c.children]), [
    ['b0', nodeWidth('main (12)'), undefined],
    ['x', nodeWidth('x'), undefined],
  ]);
  assert.ok(nodeWidth('main (12)') > nodeWidth('x'), 'a longer caption is wider');
  assert.deepEqual(root.edges!.map((e) => e.id), ['x->b0', 'b0->x']);
  assert.equal(root.layoutOptions!['elk.layered.spacing.nodeNodeBetweenLayers'], '70');
  assert.equal(root.layoutOptions!['elk.spacing.nodeNode'], '40');
  for (const value of Object.values(root.layoutOptions!)) assert.equal(typeof value, 'string');
  assert.equal(root.layoutOptions!['elk.hierarchyHandling'], undefined);
});

// Square is today's routing and the default; curved asks ELK for splines.
test('flatElkGraph routes square by default and curved on request', () => {
  const view = { nodes: [{ id: 'a', label: 'a', type: 'VARIABLE' as const }], edges: [], frames: [] };
  const routing = (root: ElkNode) => root.layoutOptions!['elk.edgeRouting'];
  assert.equal(routing(flatElkGraph(view, (n) => n.label)), 'ORTHOGONAL');
  assert.equal(routing(flatElkGraph(view, (n) => n.label, undefined, 'square')), 'ORTHOGONAL');
  assert.equal(routing(flatElkGraph(view, (n) => n.label, undefined, 'curved')), 'SPLINES');
});

// ELK's SPLINES section, recorded from elkjs 0.9.3 (a -> b, one layer apart): startPoint, then
// bendPoints and endPoint together are cubic Bezier control points in threes - 1 + 3k points in all.
const spline: ElkNode = {
  id: 'root',
  edges: [{
    id: 'a->b', sources: ['a'], targets: ['b'],
    sections: [{
      id: 'a->b_s0', startPoint: { x: 105.5, y: 38 }, endPoint: { x: 134, y: 108 },
      bendPoints: [{ x: 105.5, y: 38 }, { x: 131.15, y: 73 }],
    }],
  }],
};

test('curvedPath draws a recorded ELK spline section as a cubic Bezier', () => {
  assert.equal(curvedPath(routes(spline).get('a->b')!), 'M 105.5 38 C 105.5 38 131.15 73 134 108');
});

test('curvedPath chains one cubic per three points, and draws a route with no bends straight', () => {
  const p = (x: number, y: number) => ({ x, y });
  assert.equal(curvedPath([p(0, 0), p(1, 1), p(2, 2), p(3, 3), p(4, 4), p(5, 5), p(6, 6)]),
    'M 0 0 C 1 1 2 2 3 3 C 4 4 5 5 6 6');
  assert.equal(curvedPath([p(0, 0), p(10, 20)]), 'M 0 0 L 10 20');
});

test('curvedPath draws a route whose point count is not 1 + 3k as a curve through its points', () => {
  const p = (x: number, y: number) => ({ x, y });
  assert.equal(curvedPath([p(0, 0), p(12, 0), p(24, 0)]), 'M 0 0 C 2 0 8 0 12 0 C 16 0 22 0 24 0');
});

// A method's values are drawn in its frame: a compound child of the root, holding the nodes that name it.
// Edges stay declared at the root, which needs INCLUDE_CHILDREN - and nothing else asks for it.
test('flatElkGraph puts each frame at the root with its nodes inside, and asks for INCLUDE_CHILDREN only then', () => {
  const view: CondensedView = {
    nodes: [
      { id: 'a', label: 'a', type: 'VARIABLE', frame: 'frame:m' },
      { id: 'm', label: 'm', type: 'GROUP', members: 4, frame: 'frame:m' },
      { id: 'p', label: 'program', type: 'GROUP', members: 9 },
      { id: 'c', label: 'c', type: 'VARIABLE', frame: 'frame:k' },
    ],
    edges: [{ source: 'a', target: 'c', kinds: { FLOW: 1 }, count: 1 }, { source: 'p', target: 'a', kinds: { FLOW: 1 }, count: 1 }],
    frames: [{ id: 'frame:m', box: 'm', label: 'm' }, { id: 'frame:k', box: 'k', label: 'k', boundary: true }],
  };
  const root = flatElkGraph(view, (n) => n.label);
  assert.deepEqual(root.children!.map((c) => [c.id, (c.children ?? []).map((x) => x.id)]), [
    ['frame:m', ['a', 'm']], ['frame:k', ['c']], ['p', []],
  ]);
  assert.equal(root.layoutOptions!['elk.hierarchyHandling'], 'INCLUDE_CHILDREN');
  assert.match(root.children![0].layoutOptions!['elk.padding'], /top=28/);
  for (const value of Object.values(root.children![0].layoutOptions!)) assert.equal(typeof value, 'string');
  // Edges are declared at the root, once, across the frames.
  assert.deepEqual(root.edges!.map((e) => e.id), ['a->c', 'p->a']);
  assert.equal(root.children![0].edges, undefined);
  // The pair: no frame, no hierarchy option; and a frame is one level deep, so a frame holds no frame.
  assert.equal(flatElkGraph({ ...view, frames: [], nodes: [view.nodes[2]], edges: [] }, (n) => n.label).layoutOptions!['elk.hierarchyHandling'], undefined);
  for (const frame of root.children!.filter((c) => c.id.startsWith('frame:'))) {
    assert.ok(frame.children!.every((c) => c.children === undefined));
  }
});

// ELK answers an edge inside a frame in the frame's own frame of reference, with `container` naming it;
// routes() moves it back to the root's, for both line styles, so the line meets its two nodes where they are.
test('routes puts a line inside a frame, and one crossing its edge, in root coordinates, square and curved', async () => {
  const view: CondensedView = {
    nodes: [
      { id: 'a', label: 'a', type: 'VARIABLE', frame: 'frame:m' },
      { id: 'b', label: 'b', type: 'VARIABLE', frame: 'frame:m' },
      { id: 'c', label: 'c', type: 'VARIABLE' },
      { id: 'd', label: 'd', type: 'VARIABLE', frame: 'frame:k' },
    ],
    edges: [flow2('a', 'b'), flow2('b', 'c'), flow2('c', 'd'), flow2('a', 'd')],
    frames: [{ id: 'frame:m', box: 'm', label: 'm' }, { id: 'frame:k', box: 'k', label: 'k' }],
  };
  for (const style of ['square', 'curved'] as const) {
    const result = await new ELK().layout(flatElkGraph(view, (n) => n.label, undefined, style)) as ElkNode;
    const frames = new Map(result.children!.map((c) => [c.id, c]));
    assert.ok(frames.get('frame:m')!.x! > 0 || frames.get('frame:m')!.y! > 0 || frames.get('frame:k')!.y! > 0, 'a frame at the origin proves nothing');
    const page = new Map<string, { x: number; y: number; width: number; height: number }>();
    for (const c of result.children!) {
      page.set(c.id, { x: c.x!, y: c.y!, width: c.width!, height: c.height! });
      for (const k of c.children ?? []) page.set(k.id, { x: c.x! + k.x!, y: c.y! + k.y!, width: k.width!, height: k.height! });
    }
    const read = routes(result);
    assert.equal(read.size, 4, style);
    const inside = (p: { x: number; y: number }, b: { x: number; y: number; width: number; height: number }) =>
      p.x >= b.x - 1 && p.x <= b.x + b.width + 1 && p.y >= b.y - 1 && p.y <= b.y + b.height + 1;
    for (const e of view.edges) {
      const route = read.get(`${e.source}->${e.target}`)!;
      assert.ok(inside(route[0], page.get(e.source)!), `${style} ${e.source}->${e.target} starts off ${e.source}`);
      assert.ok(inside(route[route.length - 1], page.get(e.target)!), `${style} ${e.source}->${e.target} ends off ${e.target}`);
    }
  }
});

const flow2 = (source: string, target: string) => ({ source, target, kinds: { FLOW: 1 as const }, count: 1 });
