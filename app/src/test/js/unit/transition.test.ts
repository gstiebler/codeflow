import { test } from 'node:test';
import assert from 'node:assert/strict';
import { absolute, diffViews, easeOut, frame, type Drawn } from '../../../main/resources/viewer/transition.ts';

// main { x, y, f { r } } before; after a click on `x`: `y` gone, `z` new in main, `f` holding a new `s`,
// and a new box `g` with a new `t` inside it.
const prev: Drawn = new Map([
  ['main', { x: 4, y: 7 }],
  ['x', { x: 10, y: 30, parent: 'main' }],
  ['y', { x: 60, y: 30, parent: 'main' }],
  ['f', { x: 10, y: 80, parent: 'main' }],
  ['r', { x: 5, y: 20, parent: 'f' }],
]);
const next: Drawn = new Map([
  ['main', { x: 4, y: 7 }],
  ['x', { x: 40, y: 30, parent: 'main' }],
  ['z', { x: 90, y: 30, parent: 'main' }],
  ['f', { x: 10, y: 120, parent: 'main' }],
  ['r', { x: 5, y: 20, parent: 'f' }],
  ['s', { x: 50, y: 20, parent: 'f' }],
  ['g', { x: 200, y: 120, parent: 'main' }],
  ['t', { x: 5, y: 20, parent: 'g' }],
]);

test('diffViews sorts the nodes into kept, added and removed by id', () => {
  const { kept, added, removed } = diffViews(prev, next, 'x');
  assert.deepEqual(kept, ['main', 'x', 'f', 'r']);
  assert.deepEqual(added, ['z', 's', 'g', 't']);
  assert.deepEqual(removed, ['y']);
});

test('a kept node starts where it was', () => {
  const { start } = diffViews(prev, next, 'x');
  assert.deepEqual(start.get('x'), { x: 10, y: 30 });
  assert.deepEqual(start.get('f'), { x: 10, y: 80 });
});

// On the page, every added node starts at the clicked node's old place, (14, 37) - `main` is at (4, 7) so
// that this differs from `x`'s own (10, 30), which is the bug a relative origin would be. `z` is in the same
// box as `x`; `s` is inside `f`, which starts at (14, 87); `t` is inside `g`, which is itself new and
// so itself starts at (14, 37). Relative positions that put each of them there are the claim.
test('an added node grows out of the clicked node\'s old place, wherever its box is', () => {
  const { start } = diffViews(prev, next, 'x');
  const begun: Drawn = new Map([...next].map(([id, p]) => [id, { ...start.get(id)!, parent: p.parent }]));
  for (const id of ['z', 's', 'g', 't']) assert.deepEqual(absolute(begun, id), { x: 14, y: 37 }, id);
  assert.deepEqual(start.get('s'), { x: 0, y: -50 });
});

test('with nothing clicked, or the clicked node not drawn before, an added node starts at its own place', () => {
  for (const clicked of [undefined, 'nowhere']) {
    const { start } = diffViews(prev, next, clicked);
    assert.deepEqual(start.get('z'), { x: 90, y: 30 });
    assert.deepEqual(start.get('t'), { x: 5, y: 20 });
    assert.deepEqual(start.get('x'), { x: 10, y: 30 });
  }
});

test('a frame runs from the start to the new place, eased', () => {
  const diff = diffViews(prev, next, 'x');
  assert.deepEqual(frame(diff, next, 0).get('x'), { x: 10, y: 30, parent: 'main' });
  assert.deepEqual(frame(diff, next, 1).get('x'), { x: 40, y: 30, parent: 'main' });
  const half = frame(diff, next, 0.5).get('x')!;
  assert.equal(half.x, 10 + 30 * easeOut(0.5));
  assert.ok(half.x > 25, 'ease-out covers more than half the way in the first half');
  assert.deepEqual(frame(diff, next, 7).get('z'), { x: 90, y: 30, parent: 'main' });
});
