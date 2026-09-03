import { test } from 'node:test';
import assert from 'node:assert/strict';
import { hand, KEY } from './corpus.ts';
import { buildModel } from '../src/load.ts';
import { condense } from '../src/scc.ts';
import { boundaries } from '../src/boundaries.ts';
import { allPairs, backward, forward, indexBoundaries } from '../src/slice.ts';

const m = buildModel(hand());
const c = condense(m);
const bs = boundaries(m);
const index = indexBoundaries(c, bs);

const names = (xs: { boundary: { label: string } }[]) => [...new Set(xs.map((x) => x.boundary.label))].sort();

/** Every graph node on a source line - what clicking that line in the panel would ask about. */
const nodesAt = (stmt: string) => [...m.stmtOfNode].filter(([, at]) => at === stmt).map(([id]) => id);

test('forward from the endpoint reaches the write and the response', () => {
  assert.deepEqual(names(forward(c, index, nodesAt(KEY.endpoint))), [
    'LoanRepository.save',
    'POST /loans/{id}/charge returns',
  ]);
});

test('the witness path crosses the service, not just the endpoint method', () => {
  const hit = forward(c, index, nodesAt(KEY.endpoint)).find((h) => h.boundary.label.endsWith('returns'))!;
  const files = [...new Set(hit.path.map((s) => (m.stmtOfNode.get(s.node) ?? '').split(':')[0]))];
  assert.deepEqual(files, ['app/api/Api.java', 'app/svc/Svc.java']);
});

test('the path names what carries the value at each step, including the field', () => {
  const hit = forward(c, index, nodesAt(KEY.endpoint)).find((h) => h.boundary.label.endsWith('returns'))!;
  assert.ok(hit.path.some((s) => s.via?.carrier === 'field Svc.total'), 'the field hop must be visible');
});

test('backward from the database write reaches the request it came from', () => {
  assert.deepEqual(names(backward(c, index, nodesAt(KEY.save))), ['LoanRepository.findById', 'POST /loans/{id}/charge']);
});

test('a backward path reads from the input towards the statement asked about', () => {
  const hit = backward(c, index, nodesAt(KEY.save)).find((h) => h.boundary.label === 'POST /loans/{id}/charge')!;
  assert.equal(m.stmtOfNode.get(hit.path[0]!.node), KEY.endpoint);
  assert.equal(m.stmtOfNode.get(hit.path[hit.path.length - 1]!.node), KEY.save);
});

// Not every input reaches every output. If it did, the diff below would be worthless: `loan` is
// handed to the service and used there, but nothing the service returns is derived from it.
test('an input that does not reach an output is not paired with it', () => {
  const pairs = allPairs(m, c, bs).map((p) => `${p.source.label} -> ${p.sink.label}`).sort();
  assert.deepEqual(pairs, [
    'LoanRepository.findById -> LoanRepository.save',
    'POST /loans/{id}/charge -> LoanRepository.save',
    'POST /loans/{id}/charge -> POST /loans/{id}/charge returns',
  ]);
});

test('an output nothing reaches is still a boundary, just an unpaired one', () => {
  assert.ok(bs.some((b) => b.label === 'throws NoSuchElementException'));
  assert.ok(!allPairs(m, c, bs).some((p) => p.sink.label === 'throws NoSuchElementException'));
});

test('a value the callee drops does not reach what the callee returns', () => {
  const pairs = allPairs(m, c, bs).map((p) => `${p.source.label} -> ${p.sink.label}`);
  assert.ok(!pairs.includes('LoanRepository.findById -> POST /loans/{id}/charge returns'));
});

test('a walk through a cycle terminates and reports the component once', () => {
  const hits = forward(c, index, [...c.members.values()].find((g) => g.length > 1)![0]!);
  assert.deepEqual(hits, []);
});
