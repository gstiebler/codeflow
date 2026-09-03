import { test } from 'node:test';
import assert from 'node:assert/strict';
import { hand, KEY } from './corpus.ts';
import { buildModel } from '../src/load.ts';

const m = buildModel(hand());

const edge = (from: string, to: string) => m.edges.filter((e) => e.from === from && e.to === to);

test('a statement is a source line, and carries the widest code Joern attributed to it', () => {
  assert.equal(m.stmts.get(KEY.fieldWrite)?.code, 'this.total = amount');
  assert.equal(m.stmts.get(KEY.apply)?.code, 'BigDecimal t = svc.apply(loan, amount)');
});

// The rate-factor bug in one assertion: writer and reader are joined by a field, with no call
// between them, so a call hierarchy shows nothing and this edge is the whole point of the tool.
test('a value crossing a field produces an edge naming the field', () => {
  const e = edge(KEY.fieldWrite, KEY.fieldRead);
  assert.equal(e.length, 1);
  assert.equal(e[0]!.kind, 'field');
  assert.equal(e[0]!.carrier, 'field Svc.total');
});

test('a resolved call links its arguments to the callee parameters and the return back', () => {
  const into = edge(KEY.apply, KEY.applyDecl);
  assert.deepEqual(into.map((e) => e.carrier).sort(), ['arg 1 loan', 'arg 2 amount']);
  assert.ok(into.every((e) => e.kind === 'arg'));

  const back = edge(KEY.applyReturn, KEY.apply);
  assert.equal(back.length, 1);
  assert.equal(back[0]!.kind, 'ret');
});

test('an unresolved call gets no argument edges, because there is no callee to reach', () => {
  assert.deepEqual(edge(KEY.setAmount, KEY.optionalGet), []);
  assert.ok(!m.edges.some((e) => e.kind === 'arg' && e.from === KEY.setAmount));
});

test('local def-use edges carry the variable name and skip Joern noise', () => {
  const e = edge(KEY.endpoint, KEY.findById);
  assert.equal(e.length, 1);
  assert.equal(e[0]!.kind, 'local');
  assert.equal(e[0]!.carrier, 'id');
});

test('no edge is a self-loop and no edge starts at a method declaration node', () => {
  assert.deepEqual(m.edges.filter((e) => e.from === e.to), []);
  const methodNodes = new Set(hand().nodes.filter((x) => x.label === 'METHOD').map((x) => x.id));
  assert.equal(methodNodes.size, 0, 'the fixture has none, so this guards the real extractor');
});

test('the model indexes methods and types by the names the rules look them up with', () => {
  assert.ok(m.typeByName.get('Svc'));
  assert.equal(m.typeByName.get('Svc')?.members[0]?.name, 'total');
  assert.equal(m.methodByFullName.size, 4);
});
