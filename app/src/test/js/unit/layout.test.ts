import { test } from 'node:test';
import assert from 'node:assert/strict';
import { elkGraph, positions, TITLE_HEIGHT, type ElkNode } from '../../../main/resources/viewer/layout.ts';
import type { View } from '../../../main/resources/viewer/types.ts';

// main { main, x, f { f, a } }. `a` is off screen and so is the edge reaching it.
const view: View = {
  showing: new Set(['mR', 'x', 'fR']),
  stubs: new Set(['fR']),
  hidden: new Map(),
  nodes: [
    { id: 'm', type: 'METHOD', label: 'main', source: 'A.java:1', badge: 'main', visible: true },
    { id: 'mR', type: 'RETURN', label: 'main', source: 'A.java:1', parent: 'm', badge: 'main', visible: true },
    { id: 'x', type: 'VARIABLE', label: 'x', source: 'A.java:2', parent: 'm', badge: 'x', visible: true },
    { id: 'f', type: 'METHOD', label: 'f', source: 'A.java:8', parent: 'm', badge: 'f', visible: true },
    { id: 'fR', type: 'RETURN', label: 'f', source: 'A.java:8', parent: 'f', badge: 'f', visible: true },
    { id: 'a', type: 'VARIABLE', label: 'a', source: 'A.java:9', parent: 'f', badge: 'a', visible: false },
  ],
  edges: [
    { source: 'x', target: 'fR', kind: 'FLOW', visible: true },
    { source: 'x', target: 'a', kind: 'FLOW', visible: false },
  ],
};

const childIds = (node: ElkNode | undefined) => (node?.children ?? []).map((c) => c.id).sort();
const child = (node: ElkNode, id: string) => node.children!.find((c) => c.id === id)!;

// Containment is what a box means, and ELK is told it the same way React Flow is: a child sits
// inside its parent's `children`, and its coordinates come back relative to it.
test('the graph nests a box inside its parent', () => {
  const root = elkGraph(view);
  assert.deepEqual(childIds(root), ['m']);
  const main = child(root, 'm');
  assert.deepEqual(childIds(main), ['f', 'mR', 'x']);
  assert.deepEqual(childIds(child(main, 'f')), ['fR']);
});

// A hidden node laid out is a hole in the diagram: ELK reserves space for it, the boxes come out too
// big, and nothing on the page explains the gap.
test('nothing hidden reaches the layout', () => {
  const root = elkGraph(view);
  assert.equal(JSON.stringify(root).includes('"a"'), false);
  assert.equal(root.edges?.length, 1);
});

// Every edge at the root, whatever it connects. ELK requires an edge to sit in a graph enclosing
// both endpoints, and only the root always qualifies - the same rule GraphmlExporter follows.
test('edges are declared at the root', () => {
  const root = elkGraph(view);
  assert.deepEqual(root.edges, [{ id: 'x->fR', sources: ['x'], targets: ['fR'] }]);
  assert.equal(child(root, 'm').edges, undefined);
});

// A leaf needs a size before anything is rendered; a box must not have one, or ELK honours it
// instead of sizing the box to fit its children - which is how a box ends up smaller than what it
// contains.
test('a leaf is sized from its caption and a box is not sized at all', () => {
  const main = child(elkGraph(view), 'm');
  assert.ok(child(main, 'x').width! > 0);
  assert.ok(child(main, 'x').height! > 0);
  assert.equal(child(main, 'f').width, undefined);
  assert.equal(child(main, 'f').height, undefined);
});

// Longer captions get wider boxes. Without this every node is one width and a badge overflows it -
// and a badge is exactly the caption that grows, since it carries the hidden-neighbour counts.
test('a longer badge is wider', () => {
  const badged: View = { ...view, nodes: view.nodes.map((n) => (n.id === 'x' ? { ...n, badge: 'x ↑2 ↓3' } : n)) };
  const widthOf = (v: View) => child(child(elkGraph(v), 'm'), 'x').width!;
  assert.ok(widthOf(badged) > widthOf(view));
});

// INCLUDE_CHILDREN is the option that stops ELK laying each container out independently and letting
// the boxes overlap. It has to be on the root *and* on every box, since each one is a layout.
test('every box carries the hierarchical layout options', () => {
  const root = elkGraph(view);
  const main = child(root, 'm');
  for (const box of [root, main, child(main, 'f')]) {
    assert.equal(box.layoutOptions?.['elk.hierarchyHandling'], 'INCLUDE_CHILDREN');
  }
});

// A box means a method, so its name is the one label saying which method a value lives in. ELK's
// default padding is even on all four sides, which lays the first row of nodes straight over it.
test('a box reserves more room at the top than at the bottom, for its caption', () => {
  const padding = elkGraph(view).layoutOptions!['elk.padding'];
  assert.equal(padding, `[top=${TITLE_HEIGHT},left=12,bottom=12,right=12]`);
  assert.ok(TITLE_HEIGHT > 12, 'the caption needs more room than the other three sides');
});

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
