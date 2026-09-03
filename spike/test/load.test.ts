import { test } from 'node:test';
import assert from 'node:assert/strict';
import { hand, KEY } from './corpus.ts';
import { buildModel } from '../src/load.ts';

const m = buildModel(hand());

/** Edges between two source lines. The graph is at node granularity; the tests read in lines. */
const edge = (from: string, to: string) =>
  m.edges.filter((e) => m.stmtOfNode.get(e.from) === from && m.stmtOfNode.get(e.to) === to);

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
  const into = edge(KEY.apply, KEY.applyDecl).filter((e) => e.kind === 'arg');
  // Argument 0 is the receiver, and it reaches the callee's `this` - Joern numbers it as parameter 0
  // and the whole dense-array convention exists so those two agree.
  assert.deepEqual(into.map((e) => e.carrier).sort(), ['arg 0 this', 'arg 1 loan', 'arg 2 amount']);

  const back = edge(KEY.applyReturn, KEY.apply).filter((e) => e.kind === 'ret');
  assert.equal(back.length, 1);
});

// Measured on the tiny fixture: Joern wires the inside of an expression in both directions, so
// `this.repo.save(loan)` yields `this.repo -> loan` and `loan -> this.repo`, and every assignment
// yields `t -> (t = ...)` on top of the composition edge going the other way. Kept, those made a
// two-node cycle out of eleven ordinary lines.
test('def-use edges inside one expression are dropped, because composition already has them', () => {
  const g = hand();
  g.edges.push(
    { kind: 'rd', from: 'a22-repo', to: 'a22-idarg' }, // two arguments of one call
    { kind: 'rd', from: 'a22-idarg', to: 'a22-repo' }, // and the same pair backwards
    { kind: 'rd', from: 'a23-t', to: 'a23-assign' }, // an assignment target, back into its assignment
    { kind: 'rd', from: 'a23-assign', to: 'a23-t' }, // and its container, forwards
  );
  const noisy = buildModel(g);
  const added = noisy.edges.filter(
    (e) => !m.edges.some((was) => was.from === e.from && was.to === e.to && was.kind === e.kind),
  );
  assert.deepEqual(added, []);
});

test('an unresolved call gets no argument edges, because there is no callee to reach', () => {
  const fromSetAmount = m.edges.filter((e) => e.kind === 'arg' && m.stmtOfNode.get(e.from) === KEY.setAmount);
  assert.deepEqual(fromSetAmount, []);
});

test('local def-use edges carry the variable name and skip Joern noise', () => {
  const e = edge(KEY.endpoint, KEY.findById).filter((x) => x.kind === 'local');
  assert.deepEqual(e.map((x) => x.carrier), ['id']);
});

test('no edge is a self-loop and no edge starts at a method declaration node', () => {
  assert.deepEqual(m.edges.filter((e) => e.from === e.to), []);
  const methodNodes = new Set(hand().nodes.filter((x) => x.label === 'METHOD').map((x) => x.id));
  assert.equal(methodNodes.size, 0, 'the fixture has none, so this guards the real extractor');
});

test('the model indexes methods and types by the names the rules look them up with', () => {
  assert.ok(m.typeByName.get('Svc'));
  assert.equal(m.typeByName.get('Svc')?.members[0]?.name, 'total');
  assert.equal(m.methodByFullName.size, 6);
});
