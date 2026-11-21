import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  BOUNDARY_OUTLINE, EDGE_MEANINGS, NODE_MEANINGS, PALETTE, legend,
} from '../../../main/resources/viewer/theme.ts';
import type { EdgeKind, NodeType, Payload } from '../../../main/resources/viewer/types.ts';

const LEAVES: NodeType[] = [
  'LITERAL', 'VARIABLE', 'OBJ_VARIABLE', 'BIN_OP', 'FUNC_PARAM', 'RETURN', 'EXTERNAL', 'UNMODELLED',
  'BOUNDARIES', 'EVENT',
];
const KINDS: EdgeKind[] = ['FLOW', 'TRUE', 'FALSE', 'CONDITION', 'ORDER', 'SHARED', 'EVENT'];

// A colour with no meaning is the guess the legend is there to stop.
test('every leaf type and every edge kind has a meaning', () => {
  for (const type of LEAVES) assert.ok(NODE_MEANINGS[type], `no meaning for ${type}`);
  for (const kind of KINDS) assert.ok(EDGE_MEANINGS[kind], `no meaning for ${kind}`);
});

const payload = (types: NodeType[], kinds: EdgeKind[]): Payload => ({
  nodes: [{ id: 'm', label: 'main', type: 'METHOD' },
    ...types.map((type, i) => ({ id: `n${i}`, label: `n${i}`, type, parent: 'm' }))],
  edges: kinds.map((kind) => ({ source: 'n0', target: 'n1', kind })),
});

test('the legend lists only the leaf types and edge kinds the payload has', () => {
  const rows = legend(payload(['RETURN', 'LITERAL', 'LITERAL'], ['FLOW']));
  assert.deepEqual(rows.leaves.map((r) => r.name), ['LITERAL', 'RETURN']);
  assert.deepEqual(rows.lines.map((r) => r.name), ['FLOW']);
  assert.equal(rows.leaves.find((r) => r.name === 'RETURN')!.fill, PALETTE.RETURN);
  // The pair: a payload with an ORDER edge lists it, dashed, in the colour it is painted in.
  const order = legend(payload(['RETURN', 'LITERAL'], ['FLOW', 'ORDER'])).lines.find((r) => r.name === 'ORDER');
  assert.deepEqual(order && { colour: order.colour, dashed: order.dashed }, { colour: '#7b1fa2', dashed: true });
});

test('the legend lists the boundary group outline and the path lane', () => {
  const rows = legend(payload(['LITERAL', 'RETURN'], ['FLOW'])).boxes;
  assert.ok(rows.some((r) => r.outline === BOUNDARY_OUTLINE), 'no boundary outline');
  assert.ok(rows.some((r) => r.name === 'path'), 'no path lane');
});

test('the legend names the plain method box next to the boundary one', () => {
  const rows = legend(payload(['LITERAL', 'RETURN'], ['FLOW'])).boxes;
  assert.equal(rows.find((r) => r.name === 'method')?.meaning, "a method call's body, folded; click to open");
});

test('the legend lists the program box first', () => {
  const rows = legend(payload(['LITERAL', 'RETURN'], ['FLOW'])).boxes;
  assert.equal(rows[0].name, 'program');
  assert.equal(rows[0].meaning, 'everything not yet in a method box; click to open');
});
