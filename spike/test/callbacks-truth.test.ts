import { test } from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import type { Graph } from '../src/types.ts';
import { buildModel } from '../src/load.ts';
import { boundaries } from '../src/boundaries.ts';
import { scenarioMermaid } from '../src/mermaid.ts';

/**
 * Golden file for the callbacks fixture, like app/src/test/resources/memberReference: graph.json is
 * the Joern dump (refreshed by `extract-callbacks.sh`), truth.md the Mermaid generated from it.
 * truth.md is written when missing or when UPDATE_SNAPSHOTS=1, otherwise compared.
 */

const dir = new URL('../fixtures/callbacks/', import.meta.url);
const truth = new URL('truth.md', dir);

const scenarios = [
  { key: 'a', path: 'captured', title: 'lambda captures param' },
  { key: 'b', path: 'lambda-param', title: 'value into lambda parameter' },
  { key: 'c', path: 'lambda-return', title: 'value out of lambda return' },
  { key: 'd', path: 'method-ref', title: 'method reference repo::save' },
  { key: 'e', path: 'own-callback', title: 'own Callback interface' },
  { key: 'f', path: 'direct', title: 'direct repo.save (control)' },
];

test('callbacks fixture graph matches truth.md', () => {
  const g: Graph = JSON.parse(readFileSync(new URL('graph.json', dir), 'utf8'));
  const m = buildModel(g);
  const md = scenarioMermaid(g, m, boundaries(m), scenarios);
  if (!existsSync(truth) || process.env.UPDATE_SNAPSHOTS === '1') writeFileSync(truth, md);
  assert.equal(md, readFileSync(truth, 'utf8'), 'Re-run with UPDATE_SNAPSHOTS=1 to accept the new output.');
});
