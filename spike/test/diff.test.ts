import { test } from 'node:test';
import assert from 'node:assert/strict';
import type { Graph, Model } from '../src/types.ts';
import { hand } from './corpus.ts';
import { buildModel } from '../src/load.ts';
import { condense } from '../src/scc.ts';
import { boundaries } from '../src/boundaries.ts';
import { allPairs, type Pair } from '../src/slice.ts';
import { nodeFingerprinter } from '../src/fingerprint.ts';
import { diffPairs } from '../src/diff.ts';

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

test('renumbering every line changes nothing', () => {
  const g = hand();
  for (const node of g.nodes) node.line += 7;
  for (const method of g.methods) {
    method.line += 7;
    for (const p of method.params) p.line += 7;
  }
  assert.ok(rows(analyse(g)).every((r) => r.startsWith('SAME')), rows(analyse(g)).join('\n'));
});
