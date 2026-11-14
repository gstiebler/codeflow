import { test } from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, readdirSync, readFileSync, writeFileSync } from 'node:fs';
import type { Graph } from '../src/types.ts';
import { buildModel } from '../src/load.ts';
import { caseMermaid } from '../src/mermaid.ts';

/**
 * Golden file per case of the Kotlin engine (app/src/test/resources/<case>): spike-graph.json is
 * the Joern dump (refreshed by `extract-cases.sh`), spike-truth.md the Mermaid generated from it,
 * next to the Kotlin engine's own truth.md. spike-truth.md is written when missing or when
 * UPDATE_SNAPSHOTS=1, otherwise compared.
 */

const resources = new URL('../../app/src/test/resources/', import.meta.url);

for (const name of readdirSync(resources).sort()) {
  const dir = new URL(`${name}/`, resources);
  if (!existsSync(new URL('spike-graph.json', dir))) continue;
  test(`case ${name} matches spike-truth.md`, () => {
    const g: Graph = JSON.parse(readFileSync(new URL('spike-graph.json', dir), 'utf8'));
    const md = caseMermaid(name, buildModel(g));
    const truth = new URL('spike-truth.md', dir);
    if (!existsSync(truth) || process.env.UPDATE_SNAPSHOTS === '1') writeFileSync(truth, md);
    assert.equal(md, readFileSync(truth, 'utf8'), 'Re-run with UPDATE_SNAPSHOTS=1 to accept the new output.');
  });
}
