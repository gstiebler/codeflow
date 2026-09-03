import { test } from 'node:test';
import assert from 'node:assert/strict';
import type { Graph, Model } from '../src/types.ts';
import { hand } from './corpus.ts';
import { buildModel } from '../src/load.ts';
import { condense } from '../src/scc.ts';
import { boundaries } from '../src/boundaries.ts';
import { allPairs, type Pair } from '../src/slice.ts';
import { nodeFingerprinter } from '../src/fingerprint.ts';
import { diffPairs, pathPrint } from '../src/diff.ts';

function analyse(g: Graph): { m: Model; pairs: Pair[] } {
  const m = buildModel(g);
  const c = condense(m);
  return { m, pairs: allPairs(m, c, boundaries(m)) };
}

const base = analyse(hand());
const fp = nodeFingerprinter(base.m);
const rows = (after: { m: Model; pairs: Pair[] }) =>
  diffPairs(base.pairs, after.pairs, fp, nodeFingerprinter(after.m)).map((r) => `${r.status} ${r.source} -> ${r.sink}`);

test('an identical build is all SAME', () => {
  assert.ok(rows(analyse(hand())).every((r) => r.startsWith('SAME')), rows(analyse(hand())).join('\n'));
});

test('a flow the change removed is one GONE and nothing else moves', () => {
  const after = analyse(hand());
  after.pairs = after.pairs.filter((p) => p.source.label !== 'LoanRepository.findById');
  const out = rows(after);
  assert.deepEqual(out.filter((r) => !r.startsWith('SAME')), ['GONE LoanRepository.findById -> LoanRepository.save']);
});

test('a flow the change added is one NEW', () => {
  const after = analyse(hand());
  const borrowed = base.pairs[0]!;
  after.pairs = [...after.pairs, { ...borrowed, sink: { ...borrowed.sink, label: 'throws NoSuchElementException' } }];
  const out = rows(after);
  assert.deepEqual(out.filter((r) => !r.startsWith('SAME')), [`NEW ${borrowed.source.label} -> throws NoSuchElementException`]);
});

// The case a plain "which flows exist" diff cannot see: the same request still reaches the same
// response, but it now goes through different code. That is a review-worthy change, not a SAME.
test('the same pair reached by different code is PATH, not SAME', () => {
  const g = hand();
  for (const node of g.nodes) if (node.id === 's11-assign') node.code = 'this.total = amount.negate()';
  const out = rows(analyse(g));
  assert.deepEqual(out.filter((r) => !r.startsWith('SAME')), ['PATH POST /loans/{id}/charge -> POST /loans/{id}/charge returns']);
});

/**
 * The same fixture with a second, longer route from `amount` to what `apply` returns.
 *
 * A witness comparison cannot see a change on this route. The walk reports the short way round, the
 * short way is untouched, and the two versions print the same path - which is what the test asserts
 * before it asserts the verdict. What makes the change real is that a value from this input can run
 * through the detour on its way to this output, and the interior is over every such statement.
 */
function detour(first: string): Graph {
  const g = hand();
  const method = g.nodes.find((n) => n.id === 's11-amt')!.method;
  const hops = [first, 'BigDecimal v = u', 'BigDecimal w = v', 'BigDecimal x = w', 'BigDecimal y = x', 'BigDecimal z = y'];
  hops.forEach((code, i) => {
    g.nodes.push({ id: `d${i}`, label: 'IDENTIFIER', code, file: 'app/svc/Svc.java', line: 15 + i, method });
    g.edges.push({ kind: 'rd', from: i ? `d${i - 1}` : 's-amt', to: `d${i}` });
  });
  g.edges.push({ kind: 'rd', from: `d${hops.length - 1}`, to: 's14-t' });
  return g;
}

test('a change off the shortest route is still PATH', () => {
  const before = analyse(detour('BigDecimal u = amount'));
  const after = analyse(detour('BigDecimal u = amount.negate()'));
  const out = diffPairs(before.pairs, after.pairs, nodeFingerprinter(before.m), nodeFingerprinter(after.m));
  const changed = out.filter((r) => r.status !== 'SAME');
  assert.deepEqual(changed.map((r) => `${r.status} ${r.source} -> ${r.sink}`), [
    'PATH POST /loans/{id}/charge -> POST /loans/{id}/charge returns',
  ]);
  const row = changed[0]!;
  assert.deepEqual(
    pathPrint(row.before!, nodeFingerprinter(before.m)),
    pathPrint(row.after!, nodeFingerprinter(after.m)),
    'the witness should be identical: this is the case only the interior can see',
  );
});

test('renumbering every line changes nothing', () => {
  const g = hand();
  for (const node of g.nodes) node.line += 7;
  for (const method of g.methods) {
    method.line += 7;
    for (const p of method.params) p.line += 7;
  }
  assert.ok(rows(analyse(g)).every((r) => r.startsWith('SAME')), rows(analyse(g)).join('\n'));
});
