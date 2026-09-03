import { test } from 'node:test';
import assert from 'node:assert/strict';
import { hand } from './corpus.ts';
import { buildModel } from '../src/load.ts';
import { condense } from '../src/scc.ts';
import { boundaries } from '../src/boundaries.ts';
import { forward, indexBoundaries, type Hit } from '../src/slice.ts';
import { diffReport, tree } from '../src/render.ts';

const m = buildModel(hand());
const c = condense(m);
const bs = boundaries(m);
const index = indexBoundaries(c, bs);
const starts = [...m.stmtOfNode].filter(([, at]) => at === 'app/api/Api.java:20').map(([id]) => id);
const hits = forward(c, index, starts);

test('two flows that start the same way share one branch', () => {
  const out = tree(hits, m, c);
  const opens = out.split('\n').filter((l) => l.includes('Loan loan = repo.findById(id)'));
  assert.equal(opens.length, 1, out);
  // Both outputs are still reported, just under the branch they share.
  assert.ok(out.includes('LoanRepository.save'), out);
  assert.ok(out.includes('POST /loans/{id}/charge returns'), out);
});

test('a row says where the value came from and what carried it', () => {
  const out = tree(hits, m, c);
  assert.ok(/app\/svc\/Svc\.java:12 .*\[via field Svc\.total\]/.test(out), out);
});

test('an output is marked as a boundary, and the tree stops there', () => {
  const out = tree(hits, m, c).split('\n');
  const save = out.findIndex((l) => l.includes('LoanRepository.save'));
  assert.ok(out[save]!.includes('db:out'), out.join('\n'));
  // The next row is a sibling or an ancestor's sibling, never a child: the branch ended here.
  const indent = (l: string) => l.length - l.trimStart().length;
  assert.ok(indent(out[save + 1]!) <= indent(out[save]!), out.slice(save, save + 2).join('\n'));
});

test('a cycle is one row naming how many statements it holds', () => {
  const cycle = [...c.members.entries()].find(([, group]) => group.length > 1)!;
  const save = bs.find((b) => b.label === 'LoanRepository.save')!;
  const through: Hit[] = [
    { boundary: save, path: [{ node: starts[0]!, via: undefined }, { node: cycle[0], via: c.edges[0] }, { node: save.node, via: c.edges[0] }] },
  ];
  const out = tree(through, m, c);
  assert.ok(out.includes(`cycle (${cycle[1].length} stmts)`), out);
  assert.equal(out.split('\n').filter((l) => l.includes('cycle (')).length, 1, out);
});

test('a diff report leads with what changed and counts what did not', () => {
  const out = diffReport([
    { status: 'SAME', source: 'a', sink: 'b' },
    { status: 'PATH', source: 'c', sink: 'd' },
    { status: 'NEW', source: 'POST /loans', sink: 'throws NoSuchElementException in pick' },
  ]);
  assert.match(out.split('\n')[0]!, /^NEW\s+POST \/loans -> throws NoSuchElementException in pick$/);
  assert.match(out, /3 pairs: 1 new, 0 gone, 1 same endpoints by different code, 1 unchanged/);
});

// A PATH is counted, never listed: it is one witness per pair, and the measurement in render.ts says
// the witness moves for reasons that have nothing to do with the commit.
test('a report does not list the rows whose endpoints are unchanged', () => {
  const out = diffReport([{ status: 'PATH', source: 'c', sink: 'd' }]);
  assert.ok(!out.includes('c -> d'), out);
});
