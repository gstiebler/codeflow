import { test } from 'node:test';
import assert from 'node:assert/strict';
import { screen } from '../../../main/resources/viewer/model.mjs';

// main { main, x, y, f { f, a } }, x -> y inside main and y -> a crossing into f.
const payload = {
  nodes: [
    { id: 'm', type: 'METHOD', label: 'main' },
    { id: 'mR', type: 'RETURN', label: 'main', parent: 'm' },
    { id: 'x', type: 'VARIABLE', label: 'x', parent: 'm' },
    { id: 'y', type: 'VARIABLE', label: 'y', parent: 'm' },
    { id: 'f', type: 'METHOD', label: 'f', parent: 'm' },
    { id: 'fR', type: 'RETURN', label: 'f', parent: 'f' },
    { id: 'a', type: 'VARIABLE', label: 'a', parent: 'f' },
  ],
  edges: [
    { source: 'x', target: 'y', kind: 'FLOW' },
    { source: 'y', target: 'a', kind: 'FLOW' },
  ],
};

const nodeNamed = (view, id) => view.nodes.find((n) => n.id === id);

// The rule Cytoscape derives a box's visibility from its descendants, transitively. A display of
// our own on a box hides one whose only visible node is a grandchild, and that grandchild then has
// nowhere to live. As data it is one assertion instead of an `if` in the middle of a loop.
test('no METHOD node carries a display', () => {
  const view = screen(payload, new Set(['mR', 'x', 'y']));
  const boxes = view.nodes.filter((n) => n.type === 'METHOD');
  assert.equal(boxes.length, 2);
  assert.deepEqual(boxes.map((n) => n.display), [null, null]);
});

test('a revealed leaf is element and an unrevealed one is none', () => {
  const view = screen(payload, new Set(['mR', 'x', 'y']));
  assert.equal(nodeNamed(view, 'x').display, 'element');
  assert.equal(nodeNamed(view, 'a').display, 'none');
});

// Nothing is ever removed from the graph, so a node that just left the screen still needs 'none'
// written onto it. A filtered list would leave it lit from the pass before.
test('every payload node is described, visible or not', () => {
  const view = screen(payload, new Set(['mR']));
  assert.equal(view.nodes.length, payload.nodes.length);
  assert.equal(view.edges.length, payload.edges.length);
});

test('an edge is visible only when both of its endpoints are showing', () => {
  const view = screen(payload, new Set(['mR', 'x', 'y']));
  assert.equal(view.edges.find((e) => e.source === 'x').visible, true);
  assert.equal(view.edges.find((e) => e.source === 'y').visible, false);
});

test('the badge carries the hidden counts and the label stays the plain name', () => {
  const view = screen(payload, new Set(['mR', 'x', 'y']));
  assert.equal(nodeNamed(view, 'y').badge, 'y ↓1');
  assert.equal(nodeNamed(view, 'y').label, 'y');
  assert.equal(nodeNamed(view, 'x').badge, 'x');
});

// A closed box whose caller is open is offered as its RETURN node, and `stubs` is what distinguishes
// "f is on screen because you opened it" from "f is on screen as its own name".
test('an offered callee is showing and is marked a stub', () => {
  const view = screen(payload, new Set(['mR', 'x', 'y']));
  assert.equal(view.showing.has('fR'), true);
  assert.equal(view.stubs.has('fR'), true);
});

test('an opened box own RETURN is showing and is not a stub', () => {
  const view = screen(payload, new Set(['mR', 'x', 'y', 'a', 'fR']));
  assert.equal(view.showing.has('fR'), true);
  assert.equal(view.stubs.has('fR'), false);
});
