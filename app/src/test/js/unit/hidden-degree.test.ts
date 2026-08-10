import { test } from 'node:test';
import assert from 'node:assert/strict';
import { hiddenDegree, badgeLabel } from '../../../main/resources/viewer/model.ts';
import type { Id, Link } from '../../../main/resources/viewer/types.ts';

const degree = (edges: Link[], revealed: Id[]) => hiddenDegree(edges, new Set(revealed));

// `a -> b -> c`, so `b` has one edge in each direction and each end can be hidden independently.
const chain = [
  { source: 'a', target: 'b' },
  { source: 'b', target: 'c' },
];

// The whole point of the annotation: an edge whose other end is on screen is not missing, so
// nothing about it should be said. A node in the middle of a fully revealed chain is annotated
// exactly as a genuine source or sink is - with nothing.
test('an edge to a revealed node counts nothing', () => {
  assert.deepEqual([...degree(chain, ['a', 'b', 'c']).keys()], []);
});

test('a hidden source counts towards the target as incoming', () => {
  assert.deepEqual(degree(chain, ['b', 'c']).get('b'), { in: 1, out: 0 });
});

test('a hidden target counts towards the source as outgoing', () => {
  assert.deepEqual(degree(chain, ['a', 'b']).get('b'), { in: 0, out: 1 });
});

// Direction is half the meaning, so the two sides must never be summed into one number.
test('a node hidden on both sides counts each direction separately', () => {
  assert.deepEqual(degree(chain, ['b']).get('b'), { in: 1, out: 1 });
});

// An edge between two nodes that are both off screen is not something the reader is missing at any
// visible node - it belongs to neither, and counting it at both would annotate nodes nobody sees.
test('an edge with both ends hidden contributes to neither', () => {
  assert.deepEqual([...degree(chain, ['a']).keys()], ['a']);
});

test('several hidden neighbours in one direction accumulate', () => {
  const fanIn = [
    { source: 'x', target: 'sum' },
    { source: 'y', target: 'sum' },
    { source: 'z', target: 'sum' },
  ];
  assert.deepEqual(degree(fanIn, ['sum', 'y']).get('sum'), { in: 2, out: 0 });
});

// A node with nothing missing has no entry at all, which is what lets badgeLabel take `undefined`
// for the ordinary case rather than every caller constructing a pair of zeroes.
test('a fully surrounded node has no entry', () => {
  assert.equal(degree(chain, ['a', 'b', 'c']).has('b'), false);
});

test('a node with nothing hidden renders as its bare name', () => {
  assert.equal(badgeLabel('total', undefined), 'total');
  assert.equal(badgeLabel('total', { in: 0, out: 0 }), 'total');
});

test('both counts render with the name between them', () => {
  assert.equal(badgeLabel('total', { in: 2, out: 3 }), 'total ↑2 ↓3');
});

// A zero is not information, and `amount ↑0 ↓3` reads as though something were being denied.
test('a direction with nothing hidden is left out', () => {
  assert.equal(badgeLabel('amount', { in: 0, out: 3 }), 'amount ↓3');
  assert.equal(badgeLabel('amount', { in: 2, out: 0 }), 'amount ↑2');
});
