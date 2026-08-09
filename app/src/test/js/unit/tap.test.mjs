import { test } from 'node:test';
import assert from 'node:assert/strict';
import { tap } from '../../../main/resources/viewer/model.mjs';

// main { main, x, y, f { f, a, g { g, b } } }, with x -> y so one leaf click has somewhere to go.
const payload = {
  nodes: [
    { id: 'm', type: 'METHOD', label: 'main' },
    { id: 'mR', type: 'RETURN', label: 'main', parent: 'm' },
    { id: 'x', type: 'VARIABLE', label: 'x', parent: 'm' },
    { id: 'y', type: 'VARIABLE', label: 'y', parent: 'm' },
    { id: 'f', type: 'METHOD', label: 'f', parent: 'm' },
    { id: 'fR', type: 'RETURN', label: 'f', parent: 'f' },
    { id: 'a', type: 'VARIABLE', label: 'a', parent: 'f' },
    { id: 'g', type: 'METHOD', label: 'g', parent: 'f' },
    { id: 'gR', type: 'RETURN', label: 'g', parent: 'g' },
    { id: 'b', type: 'VARIABLE', label: 'b', parent: 'g' },
  ],
  edges: [{ source: 'x', target: 'y', kind: 'FLOW' }],
};

const after = (revealed, id) => [...tap(payload, new Set(revealed), id)].sort();

test('clicking a closed box reveals its own leaves and not a nested box leaves', () => {
  assert.deepEqual(after(['mR', 'x'], 'f'), ['a', 'fR', 'mR', 'x']);
});

// descendants, not children: folding a box that left a nested method's nodes on screen would draw
// a callee floating with no caller around it.
test('clicking an open box folds every leaf under it, however deep', () => {
  assert.deepEqual(after(['mR', 'x', 'a', 'fR', 'b', 'gR'], 'f'), ['mR', 'x']);
});

test('a box open only through a grandchild still folds', () => {
  assert.deepEqual(after(['mR', 'x', 'b'], 'f'), ['mR', 'x']);
});

test('clicking a leaf unions its neighbourhood', () => {
  assert.deepEqual(after(['mR', 'x'], 'x'), ['mR', 'x', 'y']);
});

// Clicks union and never subtract - only a box click or R takes anything away.
test('clicking a leaf never removes anything', () => {
  assert.deepEqual(after(['mR', 'x', 'y', 'a', 'fR'], 'y'), ['a', 'fR', 'mR', 'x', 'y']);
});

test('tap does not mutate the set it was given', () => {
  const revealed = new Set(['mR', 'x']);
  tap(payload, revealed, 'f');
  assert.deepEqual([...revealed].sort(), ['mR', 'x']);
});

test('tapping an id that is not in the payload changes nothing', () => {
  assert.deepEqual(after(['mR', 'x'], 'nope'), ['mR', 'x']);
});

// The bug this whole change exists to fix. `fR` is on screen because f is closed and its caller is
// open, it carries f's name, it is the obvious thing to press - and a RETURN node that no value
// flows through has no neighbours, so the walk returned it to itself and nothing happened.
test('clicking a method name opens that method', () => {
  assert.deepEqual(after(['mR', 'x'], 'fR'), ['a', 'fR', 'gR', 'mR', 'x']);
});

// One level per click. `gR` is g's name and nothing of g's body comes with it: if a click opened the
// boxes below it too, one press would unfold the call tree - the wall the viewer exists to avoid.
test('opening a method offers the names of what it calls and nothing inside them', () => {
  assert.equal(after(['mR', 'x'], 'fR').includes('b'), false);
});

// The same gesture means the same thing whether the box is shut or already open, which is what
// makes every leaf reachable: a RETURN nothing flows through is otherwise unreachable once its box
// has been opened by following dataflow instead.
test('clicking a method name again is harmless', () => {
  assert.deepEqual(after(['a', 'fR', 'gR', 'mR', 'x'], 'fR'), ['a', 'fR', 'gR', 'mR', 'x']);
});

// A leaf that is not a method's name is untouched by any of this and still walks its edges.
test('clicking an ordinary leaf still walks its neighbourhood', () => {
  assert.deepEqual(after(['mR', 'x', 'a', 'fR'], 'x'), ['a', 'fR', 'mR', 'x', 'y']);
});
