import { test } from 'node:test';
import assert from 'node:assert/strict';
import { curvedPath, flatElkGraph, nodeWidth, positions, routes, type ElkNode } from '../../../main/resources/viewer/layout.ts';

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
  const view = { nodes: [{ id: 'a', label: 'a', type: 'VARIABLE' as const }], edges: [] };
  const routing = (root: ElkNode) => root.layoutOptions!['elk.edgeRouting'];
  assert.equal(routing(flatElkGraph(view, (n) => n.label)), 'ORTHOGONAL');
  assert.equal(routing(flatElkGraph(view, (n) => n.label, undefined, undefined, 'square')), 'ORTHOGONAL');
  assert.equal(routing(flatElkGraph(view, (n) => n.label, undefined, undefined, 'curved')), 'SPLINES');
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
