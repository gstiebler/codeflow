import { test } from 'node:test';
import assert from 'node:assert/strict';
import { screen } from '../../../main/resources/viewer/model.ts';
import type { Id, Payload, View } from '../../../main/resources/viewer/types.ts';

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
} satisfies Payload;

// `!` rather than a guard: an id these tests do not have is a broken test, and the property read
// that follows says so as clearly as anything written here would.
const nodeNamed = (view: View, id: Id) => view.nodes.find((n) => n.id === id)!;

// A box is on screen because something inside it is - transitively, since a box holds boxes, and
// the case that has always been the trap is a box whose only visible node is a grandchild. This and
// the grandchild test below are now the only statement of that rule: the page it used to be checked
// against end to end derived it for itself, and that page is gone.
test('a box is visible exactly when some descendant leaf is showing', () => {
  const view = screen(payload, new Set(['mR', 'x', 'y']));
  assert.equal(nodeNamed(view, 'm').visible, true);
  // f is visible too - closed, but its caller is open, so its stub is offered inside it.
  assert.equal(nodeNamed(view, 'f').visible, true);

  const empty = screen(payload, new Set([]));
  assert.equal(nodeNamed(empty, 'f').visible, false);
});

test('a revealed leaf is visible and an unrevealed one is not', () => {
  const view = screen(payload, new Set(['mR', 'x', 'y']));
  assert.equal(nodeNamed(view, 'x').visible, true);
  assert.equal(nodeNamed(view, 'a').visible, false);
});

// main { main, x, f { g { g, b } } }: f holds no leaf of its own, so its visibility can only come
// from b, two levels down. Deriving it from own children instead - the obvious wrong
// implementation - hides f and leaves b nowhere to live.
const nested = {
  nodes: [
    { id: 'm', type: 'METHOD', label: 'main' },
    { id: 'mR', type: 'RETURN', label: 'main', parent: 'm' },
    { id: 'f', type: 'METHOD', label: 'f', parent: 'm' },
    { id: 'g', type: 'METHOD', label: 'g', parent: 'f' },
    { id: 'gR', type: 'RETURN', label: 'g', parent: 'g' },
    { id: 'b', type: 'VARIABLE', label: 'b', parent: 'g' },
  ],
  edges: [],
} satisfies Payload;

test('a box whose only showing node is a grandchild is visible', () => {
  const view = screen(nested, new Set(['b']));
  assert.equal(nodeNamed(view, 'f').visible, true);
  assert.equal(nodeNamed(view, 'g').visible, true);
});

// The view is the whole payload with a verdict on each node, not the subset that survived. A
// filtered list would make "gone from the view" and "off screen" the same thing, and a renderer that
// keeps its own graph between frames could not tell a node that just left from one it never had.
test('every payload node is described, visible or not', () => {
  const view = screen(payload, new Set(['mR']));
  assert.equal(view.nodes.length, payload.nodes.length);
  assert.equal(view.edges.length, payload.edges.length);
});

test('an edge is visible only when both of its endpoints are showing', () => {
  const view = screen(payload, new Set(['mR', 'x', 'y']));
  assert.equal(view.edges.find((e) => e.source === 'x')!.visible, true);
  assert.equal(view.edges.find((e) => e.source === 'y')!.visible, false);
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
