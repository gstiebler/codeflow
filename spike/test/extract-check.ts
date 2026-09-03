import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
import type { Graph } from '../src/types.ts';
import { buildModel } from '../src/load.ts';
import { condense } from '../src/scc.ts';
import { boundaries } from '../src/boundaries.ts';
import { allPairs, forward, indexBoundaries } from '../src/slice.ts';
import { tree } from '../src/render.ts';

/**
 * Does Joern produce, from real Java, the shape `corpus.ts` claims by hand?
 *
 * Run by `extract.sh`, never by `node --test`: it needs a CPG, which takes minutes to build. Every
 * assertion here has a twin in the unit tests that runs against the hand-written fixture in
 * milliseconds. If one of these fails and its twin passes, the fixture is lying about Joern.
 */

const path = process.argv[2];
if (!path) throw new Error('usage: extract-check.ts <graph.json>');

const g: Graph = JSON.parse(readFileSync(path, 'utf8'));
console.log(`${g.types.length} types, ${g.methods.length} methods, ${g.nodes.length} nodes, ${g.edges.length} edges`);

const m = buildModel(g);
const c = condense(m);
const bs = boundaries(m);
const index = indexBoundaries(c, bs);

const labels = [...new Set(bs.map((b) => `${b.dir} ${b.kind} ${b.label}`))].sort();
console.log('\nboundaries:');
for (const label of labels) console.log(`  ${label}`);

const has = (want: string) => assert.ok(labels.includes(want), `missing boundary: ${want}\nfound:\n${labels.join('\n')}`);
has('in net POST /loans/{id}/charge');
has('out net POST /loans/{id}/charge returns');
has('in db LoanRepository.findById');
has('out db LoanRepository.save');
has('out throw throws NoSuchElementException');

// The field hop: `this.total = amount` writes and `BigDecimal t = this.total` reads, with no call
// between them. Joern's own dataflow finds nothing here, which is why load.ts adds the edge.
const field = m.edges.filter((e) => e.kind === 'field');
assert.ok(field.length > 0, 'no field edges: the write-then-read hop was lost');
console.log(`\n${field.length} field edges, e.g. ${field[0]!.carrier}`);

// The cycle in Svc.spin.
const cycles = [...c.members.values()].filter((group) => group.length > 1);
assert.ok(cycles.length > 0, 'Svc.spin should collapse into one component');
console.log(`${cycles.length} cycles, largest ${Math.max(...cycles.map((x) => x.length))} nodes`);

const entry = bs.filter((b) => b.label === 'POST /loans/{id}/charge').map((b) => b.node);
console.log('\nforward from the endpoint:');
console.log(tree(forward(c, index, entry), m, c));

const pairs = [...new Set(allPairs(m, c, bs).map((p) => `${p.source.label} -> ${p.sink.label}`))].sort();
console.log('\npairs:');
for (const p of pairs) console.log(`  ${p}`);
assert.ok(
  pairs.includes('POST /loans/{id}/charge -> POST /loans/{id}/charge returns'),
  'the request should reach the response',
);
assert.ok(pairs.includes('POST /loans/{id}/charge -> LoanRepository.save'), 'the request should reach the write');

console.log('\nOK');
