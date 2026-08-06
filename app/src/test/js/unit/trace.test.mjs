import { test } from 'node:test';
import assert from 'node:assert/strict';
import { traceFrom } from '../../../main/resources/viewer/viewer.mjs';

const ids = (set) => [...set].sort();

test('reaches both backwards and forwards from the clicked node', () => {
  const edges = [{ source: 'a', target: 'b' }, { source: 'b', target: 'c' }];
  assert.deepEqual(ids(traceFrom(edges, 'b')), ['a', 'b', 'c']);
});

test('leaves an unrelated component alone', () => {
  const edges = [{ source: 'a', target: 'b' }, { source: 'c', target: 'd' }];
  assert.deepEqual(ids(traceFrom(edges, 'a')), ['a', 'b']);
});

test('includes a node with no edges at all', () => {
  assert.deepEqual(ids(traceFrom([], 'lonely')), ['lonely']);
});

// A for-loop's counter flows into itself through the loop body, so the graph really does contain
// cycles. A naive walk follows one forever and the page hangs with no error to explain it.
test('terminates on a cycle', () => {
  const edges = [{ source: 'a', target: 'b' }, { source: 'b', target: 'a' }];
  assert.deepEqual(ids(traceFrom(edges, 'a')), ['a', 'b']);
});

// Two calls to one method are two subgraphs that share no nodes. Tracing from inside one must not
// light up the other, which is the whole reason nodes are per-occurrence.
test('does not cross into a sibling call with the same shape', () => {
  const edges = [
    { source: 'arg1', target: 'param1' }, { source: 'param1', target: 'ret1' },
    { source: 'arg2', target: 'param2' }, { source: 'param2', target: 'ret2' },
  ];
  assert.deepEqual(ids(traceFrom(edges, 'param1')), ['arg1', 'param1', 'ret1']);
});
