import { test } from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import type { Graph, Model, NodeFact } from '../src/types.ts';
import { buildModel } from '../src/load.ts';
import { condense } from '../src/condense.ts';

/**
 * Contraction must neither invent nor lose flows. Merging occurrences of one variable is a deliberate
 * change, so the reference is the original graph with each kept node renamed to its condensed key:
 * for every pair of distinct kept keys, reachable in the condensed graph iff in that quotient.
 */
function check(m: Model) {
  const c = condense(m);
  const name = (id: string) => c.keyOf.get(id) ?? id;
  const quotient = new Map<string, Set<string>>();
  for (const e of m.edges) {
    const a = name(e.from);
    const b = name(e.to);
    if (a !== b) (quotient.get(a) ?? quotient.set(a, new Set()).get(a)!).add(b);
  }
  const condensed = new Map<string, Set<string>>();
  for (const e of c.edges) (condensed.get(e.from) ?? condensed.set(e.from, new Set()).get(e.from)!).add(e.to);
  const reach = (g: Map<string, Set<string>>, from: string) => {
    const seen = new Set<string>();
    const queue = [from];
    while (queue.length) for (const t of g.get(queue.shift()!) ?? []) if (!seen.has(t)) { seen.add(t); queue.push(t); }
    return seen;
  };
  const kept = new Set(c.keyOf.values());
  for (const k of kept) {
    const want = new Set([...reach(quotient, k)].filter((x) => kept.has(x) && x !== k));
    const got = new Set([...reach(condensed, k)].filter((x) => x !== k));
    assert.deepEqual([...got].sort(), [...want].sort(), `reachable from ${k}`);
  }
  for (const e of c.edges) assert.notEqual(e.from, e.to);
}

const node = (id: string, label: string, code: string, extra: Partial<NodeFact> = {}): NodeFact => ({
  id, label, code, file: 'A.java', line: 1, method: 'A.f', ...extra,
});

test('contraction through dropped nodes keeps flows and invents none', () => {
  const nodes = [
    node('lit', 'LITERAL', '1', { col: 1 }),
    node('x1', 'IDENTIFIER', 'x', { typeFullName: 'int', col: 2 }),
    node('asg', 'CALL', 'x = 1', { name: '<operator>.assignment' }),
    node('x2', 'IDENTIFIER', 'x', { typeFullName: 'int', col: 3 }),
    node('add', 'CALL', 'x + 2', { name: '<operator>.addition', col: 4 }),
    node('blk', 'BLOCK', ''),
    node('y', 'IDENTIFIER', 'y', { typeFullName: 'int', col: 5 }),
    node('other', 'LITERAL', '9', { col: 6 }),
  ];
  const edge = (from: string, to: string) => ({ from, to, carrier: '', kind: 'local' as const });
  const m = {
    byId: new Map(nodes.map((n) => [n.id, n])),
    methodByFullName: new Map(),
    edges: [edge('lit', 'asg'), edge('asg', 'x1'), edge('x2', 'add'), edge('add', 'blk'), edge('blk', 'y'), edge('other', 'blk')],
  } as unknown as Model;
  check(m);
  const c = condense(m);
  assert.deepEqual(c.edges.map((e) => `${e.from}>${e.to}`).sort(), [
    'A.f|BIN_OP|+|1:4>A.f|VARIABLE|y',
    'A.f|LITERAL|1|1:1>A.f|VARIABLE|x',
    'A.f|LITERAL|9|1:6>A.f|VARIABLE|y',
    'A.f|VARIABLE|x>A.f|BIN_OP|+|1:4',
  ]);
});

const resources = new URL('../../app/src/test/resources/', import.meta.url);
test('contraction property holds on every committed case dump', () => {
  let cases = 0;
  for (const name of readdirSync(resources)) {
    const file = new URL(`${name}/spike-graph.json`, resources);
    if (!existsSync(file)) continue;
    check(buildModel(JSON.parse(readFileSync(file, 'utf8')) as Graph));
    cases++;
  }
  assert.ok(cases >= 70);
});
