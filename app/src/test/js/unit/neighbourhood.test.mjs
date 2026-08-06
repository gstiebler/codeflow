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

// Reaching `d` by the short edge must not be defeated by also reaching it the long way round.
// A LIFO walk can record the long distance first and prune from there.
test('uses the shortest path to a node reachable two ways', () => {
  const diamond = [
    { source: 'a', target: 'b' },
    { source: 'b', target: 'c' },
    { source: 'c', target: 'd' },
    { source: 'a', target: 'x' },
    { source: 'x', target: 'd' },
  ];
  // d is 2 hops via x, 3 via b/c. At depth 2 it must be in.
  assert.ok(neighbourhood(diamond, 'a', 2).has('d'));
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
