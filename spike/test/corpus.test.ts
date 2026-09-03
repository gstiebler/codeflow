import { test } from 'node:test';
import assert from 'node:assert/strict';
import { hand, KEY } from './corpus.ts';

const g = hand();

// A fixture that lies is worse than no fixture: every later suite would agree with it and none would
// notice. These three checks are the ones a hand-written graph actually gets wrong.
test('every edge endpoint is a declared node', () => {
  const ids = new Set(g.nodes.map((x) => x.id));
  const dangling = g.edges.filter((e) => !ids.has(e.from) || !ids.has(e.to));
  assert.deepEqual(dangling, []);
});

test('every node and parameter belongs to a declared method', () => {
  const methods = new Set(g.methods.map((m) => m.fullName));
  const orphans = g.nodes.filter((x) => !methods.has(x.method)).map((x) => `${x.id} in ${x.method}`);
  assert.deepEqual(orphans, []);
});

test('every call argument names a node that exists', () => {
  const ids = new Set(g.nodes.map((x) => x.id));
  const bad = g.nodes.flatMap((x) => (x.args ?? []).filter((a) => !ids.has(a)).map((a) => `${x.id} -> ${a}`));
  assert.deepEqual(bad, []);
});

test('the statement keys the tests name all exist in the fixture', () => {
  const keys = new Set(g.nodes.map((x) => `${x.file}:${x.line}`));
  const missing = Object.values(KEY).filter((k) => !keys.has(k));
  assert.deepEqual(missing, []);
});
