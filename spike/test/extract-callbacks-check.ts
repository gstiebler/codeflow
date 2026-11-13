import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
import type { Graph } from '../src/types.ts';
import { buildModel } from '../src/load.ts';
import { condense } from '../src/scc.ts';
import { boundaries } from '../src/boundaries.ts';
import { allPairs, forward, indexBoundaries } from '../src/slice.ts';
import { tree } from '../src/render.ts';

/**
 * Which callback flows does the dataflow follow today? Run by `extract-callbacks.sh`, never by
 * `node --test`. Only the control is asserted; the other scenarios are printed, because the point is
 * a baseline that a later improvement moves from GAP to FOUND.
 */

const path = process.argv[2];
if (!path) throw new Error('usage: extract-callbacks-check.ts <graph.json>');

const g: Graph = JSON.parse(readFileSync(path, 'utf8'));
const m = buildModel(g);
const c = condense(m);
const bs = boundaries(m);
const index = indexBoundaries(c, bs);

const labels = [...new Set(bs.map((b) => `${b.dir} ${b.kind} ${b.label}`))].sort();
console.log('boundaries:');
for (const label of labels) console.log(`  ${label}`);

const scenarios: [string, string][] = [
  ['a captured variable', 'captured'],
  ['b lambda parameter', 'lambda-param'],
  ['c lambda return', 'lambda-return'],
  ['d method reference', 'method-ref'],
  ['e own callback interface', 'own-callback'],
  ['f control (direct)', 'direct'],
];
const save = 'ItemRepository.save';
for (const [, name] of scenarios) assert.ok(labels.includes(`in net POST /cb/${name}`), `missing endpoint boundary: ${name}`);
assert.ok(labels.includes(`out db ${save}`), `missing boundary: ${save}`);

const pairs = new Set(allPairs(m, c, bs).map((p) => `${p.source.label} -> ${p.sink.label}`));
const found = (name: string) => pairs.has(`POST /cb/${name} -> ${save}`);

console.log('\nscenario                  | pair found?');
console.log('--------------------------|------------');
for (const [title, name] of scenarios) console.log(`${title.padEnd(25)} | ${found(name) ? 'FOUND' : 'GAP'}`);

for (const [title, name] of scenarios) {
  if (found(name)) continue;
  const entry = bs.filter((b) => b.dir === 'in' && b.label === `POST /cb/${name}`).map((b) => b.node);
  console.log(`\nGAP ${title}: forward walk from POST /cb/${name} (last nodes reached are where the chain breaks):`);
  console.log(tree(forward(c, index, entry), m, c) || '  (no boundary reached)');
  // forward() only reports boundaries, so also list every node the walk reached and mark the last
  // ones (no onward edge): that is where the value stops.
  const seen = new Set(entry.map((id) => c.componentOf.get(id) ?? id));
  const queue = [...seen];
  while (queue.length) {
    for (const e of c.out.get(queue.shift()!) ?? []) {
      if (seen.has(e.to)) continue;
      seen.add(e.to);
      queue.push(e.to);
    }
  }
  for (const id of seen) {
    const n = m.byId.get(c.members.get(id)?.[0] ?? id);
    const last = (c.out.get(id) ?? []).length === 0 ? ' <- last' : '';
    console.log(`  reached ${n?.label} \`${n?.code}\` (${n?.file}:${n?.line})${last}`);
  }
}

assert.ok(found('direct'), 'control: direct repo.save(param) must be found');
console.log('\nOK');
