import { test } from 'node:test';
import assert from 'node:assert/strict';
import { flatElkGraph, nodeWidth, positions, type ElkNode } from '../../../main/resources/viewer/layout.ts';

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
      { id: 'program', label: 'program', type: 'GROUP', members: 12 },
      { id: 'x', label: 'x', type: 'VARIABLE' },
    ],
    edges: [
      { source: 'x', target: 'program', kinds: { FLOW: 2, ORDER: 1 }, count: 3 },
      { source: 'program', target: 'x', kinds: { FLOW: 1 }, count: 1 },
    ],
  }, (node) => (node.type === 'GROUP' ? `${node.label} (${node.members})` : node.label));
  assert.deepEqual(root.children!.map((c) => [c.id, c.width, c.children]), [
    ['program', nodeWidth('program (12)'), undefined],
    ['x', nodeWidth('x'), undefined],
  ]);
  assert.deepEqual(root.edges!.map((e) => e.id), ['x->program', 'program->x']);
  assert.equal(root.layoutOptions!['elk.layered.spacing.nodeNodeBetweenLayers'], '70');
  assert.equal(root.layoutOptions!['elk.spacing.nodeNode'], '40');
  for (const value of Object.values(root.layoutOptions!)) assert.equal(typeof value, 'string');
  assert.equal(root.layoutOptions!['elk.hierarchyHandling'], undefined);
});
