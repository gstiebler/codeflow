import { test } from 'node:test';
import assert from 'node:assert/strict';
import { hand } from './corpus.ts';
import { buildModel } from '../src/load.ts';
import { condense } from '../src/scc.ts';
import { boundaries } from '../src/boundaries.ts';
import { forward, indexBoundaries } from '../src/slice.ts';
import { dot } from '../src/dot.ts';

const m = buildModel(hand());
const c = condense(m);
const bs = boundaries(m);
const index = indexBoundaries(c, bs);
const starts = bs.filter((b) => b.label === 'POST /loans/{id}/charge').map((b) => b.node);
const out = dot(forward(c, index, starts), m, c, 'forward from the endpoint', starts);

const boxes = out.split('\n').filter((l) => /^ {2}"[^"]+" \[/.test(l));
const edges = out.split('\n').filter((l) => l.includes('->'));

test('the graph is one box per source line, not one per graph node', () => {
  // `BigDecimal t = svc.apply(loan, amount)` is four nodes and must be one box, or the picture is a
  // column of identical rectangles - the thing render.ts folds away for exactly the same reason.
  const apply = boxes.filter((l) => l.includes('svc.apply(loan, amount)'));
  assert.equal(apply.length, 1, boxes.join('\n'));
});

test('a boundary box is filled and says which boundary it is', () => {
  const save = boxes.find((l) => l.includes('LoanRepository.save'));
  assert.ok(save?.includes('fillcolor='), boxes.join('\n'));
});

test('the line asked about is outlined', () => {
  assert.equal(boxes.filter((l) => l.includes('penwidth=2.4')).length, 1, boxes.join('\n'));
});

test('a cycle is one box saying how many statements it holds', () => {
  const cycle = boxes.filter((l) => l.includes('⟳ cycle'));
  assert.ok(cycle.every((l) => l.includes('peripheries=2')), cycle.join('\n'));
});

test('edges carry the carrier, and the field hop is drawn differently', () => {
  assert.ok(edges.some((l) => l.includes('field Svc.total') && l.includes('style=dashed')), edges.join('\n'));
});

test('no edge points at a box that is not in the graph', () => {
  const ids = new Set([...out.matchAll(/^ {2}("[^"]+") \[/gm)].map((x) => x[1]));
  for (const line of edges) {
    const [, from, to] = /^ {2}("[^"]+") -> ("[^"]+")/.exec(line)!;
    assert.ok(ids.has(from!) && ids.has(to!), line);
  }
});

test('quotes in source do not break the file', () => {
  const g = hand();
  for (const node of g.nodes) if (node.id === 's11-assign') node.code = 'this.total = new BigDecimal("1")';
  const m2 = buildModel(g);
  const c2 = condense(m2);
  const bs2 = boundaries(m2);
  const out2 = dot(forward(c2, indexBoundaries(c2, bs2), starts), m2, c2, 'q');
  assert.ok(out2.includes('\\"1\\"'), out2);
});
