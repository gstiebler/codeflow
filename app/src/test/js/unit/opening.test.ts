import { test } from 'node:test';
import assert from 'node:assert/strict';
import { opening } from '../../../main/resources/viewer/model.ts';
import type { Payload } from '../../../main/resources/viewer/types.ts';

// main { main, x, f { f, a } } - the entry method's own values, and nothing from what it calls.
const payload = {
  nodes: [
    { id: 'm', type: 'METHOD', label: 'main' },
    { id: 'mR', type: 'RETURN', label: 'main', parent: 'm' },
    { id: 'x', type: 'VARIABLE', label: 'x', parent: 'm' },
    { id: 'f', type: 'METHOD', label: 'f', parent: 'm' },
    { id: 'fR', type: 'RETURN', label: 'f', parent: 'f' },
    { id: 'a', type: 'VARIABLE', label: 'a', parent: 'f' },
  ],
  edges: [],
} satisfies Payload;

test('the opening view is the entry method own leaves', () => {
  assert.deepEqual([...opening(payload)].sort(), ['mR', 'x']);
});

// The callee's body is inlined at every call site, so opening everything is the wall the viewer
// exists to avoid. `a` being absent is the assertion.
test('the opening view holds nothing from a called method', () => {
  assert.equal(opening(payload).has('a'), false);
});

// The root is the box with no parent. Picking the first METHOD in the list instead would open a
// callee's body whenever the exporter happened to emit one first.
test('the entry method is the box with no parent, not the first box listed', () => {
  const reordered = { nodes: [payload.nodes[3], ...payload.nodes.filter((n) => n.id !== 'f')], edges: [] };
  assert.deepEqual([...opening(reordered)].sort(), ['mR', 'x']);
});
