import { test } from 'node:test';
import assert from 'node:assert/strict';
import { neighbourhood } from '../../../main/resources/viewer/viewer.mjs';

const ids = (set) => [...set].sort();
const chain = [
  { source: 'a', target: 'b' },
  { source: 'b', target: 'c' },
  { source: 'c', target: 'd' },
];

test('depth 0 returns only the start node', () => {
  assert.deepEqual(ids(neighbourhood(chain, 'b', 0)), ['b']);
});

// Undirected on purpose: `c = a + b` clicked at `a` has to show `b`, or the operator is
// drawn with one operand missing.
test('reaches neighbours in both directions', () => {
  assert.deepEqual(ids(neighbourhood(chain, 'b', 1)), ['a', 'b', 'c']);
});

// The assertion that fails if the bound is off by one. `d` is exactly one hop too far.
test('stops exactly at the depth bound', () => {
  assert.deepEqual(ids(neighbourhood(chain, 'a', 2)), ['a', 'b', 'c']);
});

// This is the test that fails on a LIFO walk, and it takes both halves to do it. The edge order
// puts the long branch on top of a stack, so a stack reaches `d` at 3 rather than 2; and `z` sits
// one hop beyond `d`, so that wrong distance prunes a node that belongs in the ball.
test('records the shortest distance, so nodes beyond the meeting point stay in range', () => {
  const diamond = [
    { source: 'a', target: 'x' }, { source: 'x', target: 'd' },   // short: d at 2
    { source: 'a', target: 'b' }, { source: 'b', target: 'c' },   // long:  d at 3
    { source: 'c', target: 'd' }, { source: 'd', target: 'z' },
  ];
  // z is 3 hops out via x. Record d at 3 and the walk stops there, losing z.
  assert.ok(neighbourhood(diamond, 'a', 3).has('z'));
});

// A for-loop's counter flows into itself, so the graph really does contain cycles. A naive walk
// follows one forever and the page hangs with nothing on screen to explain why.
test('terminates on a cycle', () => {
  const edges = [{ source: 'a', target: 'b' }, { source: 'b', target: 'a' }];
  assert.deepEqual(ids(neighbourhood(edges, 'a', 10)), ['a', 'b']);
});

test('returns a lone node with no edges at all', () => {
  assert.deepEqual(ids(neighbourhood([], 'lonely', 3)), ['lonely']);
});

// Two calls to one method are two subgraphs sharing no nodes. Revealing from inside one must not
// pull in the other, which is the whole reason nodes are per-occurrence.
test('does not cross into a sibling call with the same shape', () => {
  const edges = [
    { source: 'arg1', target: 'param1' }, { source: 'param1', target: 'ret1' },
    { source: 'arg2', target: 'param2' }, { source: 'param2', target: 'ret2' },
  ];
  assert.deepEqual(ids(neighbourhood(edges, 'param1', 5)), ['arg1', 'param1', 'ret1']);
});
