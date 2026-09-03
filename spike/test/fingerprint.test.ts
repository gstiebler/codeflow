import { test } from 'node:test';
import assert from 'node:assert/strict';
import type { Graph, NodeFact } from '../src/types.ts';
import { hand, KEY } from './corpus.ts';
import { buildModel, stmtKey } from '../src/load.ts';
import { fingerprint, normalise } from '../src/fingerprint.ts';

const m = buildModel(hand());

/** The same program with one method's body moved, renamed class and all - Fineract d602928ae. */
function moved(): Graph {
  const g = hand();
  const from = 'app.svc.Svc.apply:java.math.BigDecimal(app.domain.Loan,java.math.BigDecimal)';
  const to = 'app.svc.Rewritten.apply:java.math.BigDecimal(app.domain.Loan,java.math.BigDecimal)';
  for (const x of g.nodes) {
    if (x.method !== from) continue;
    x.method = to;
    x.file = 'app/svc/Rewritten.java';
    x.line += 130;
  }
  for (const x of g.methods) {
    if (x.fullName !== from) continue;
    x.fullName = to;
    x.typeName = 'Rewritten';
    x.file = 'app/svc/Rewritten.java';
    x.line += 130;
    for (const p of x.params) p.line += 130;
  }
  return g;
}

test('a statement that moved file, class and line still fingerprints the same', () => {
  const after = buildModel(moved());
  assert.equal(
    fingerprint(m, KEY.fieldWrite),
    fingerprint(after, stmtKey('app/svc/Rewritten.java', 141)),
  );
});

// Not just the body: load.ts synthesizes the signature row as `Svc.apply(loan, amount)` so the tree
// reads like a call hierarchy, and that text carries the class the move is changing.
test('every statement of a moved method fingerprints the same, signature row included', () => {
  const after = buildModel(moved());
  const of = (model: ReturnType<typeof buildModel>, method: RegExp) =>
    [...model.stmts.values()].filter((s) => method.test(s.method)).map((s) => fingerprint(model, s.key)).sort();
  assert.deepEqual(of(after, /Rewritten\.apply/), of(m, /Svc\.apply/));
});

test('two identical statements in one method are told apart by their order', () => {
  const g = hand();
  const method = 'app.svc.Svc.spin:void()';
  const twice: NodeFact[] = [
    { id: 'r1', label: 'IDENTIFIER', code: 'total = total.add(x)', file: 'app/svc/Svc.java', line: 60, method },
    { id: 'r2', label: 'IDENTIFIER', code: 'total = total.add(x)', file: 'app/svc/Svc.java', line: 61, method },
  ];
  g.nodes.push(...twice);
  const withRepeats = buildModel(g);
  const one = fingerprint(withRepeats, 'app/svc/Svc.java:60');
  const two = fingerprint(withRepeats, 'app/svc/Svc.java:61');
  assert.notEqual(one, two);
  assert.ok(one.endsWith('#0') && two.endsWith('#1'), `${one} / ${two}`);
});

test('a fingerprint never contains a line number', () => {
  for (const key of m.stmts.keys()) assert.ok(!/:\d+/.test(fingerprint(m, key)), key);
});

test('normalising ignores spacing and a trailing semicolon, not the code', () => {
  assert.equal(normalise('  this.total   =  amount ;'), normalise('this.total = amount'));
  assert.notEqual(normalise('this.total = amount'), normalise('this.total = other'));
});
