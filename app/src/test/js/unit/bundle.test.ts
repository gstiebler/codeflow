import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { buildViewer, BUNDLES } from '../../../../../scripts/build-viewer.mjs';

const VIEWER = 'app/src/main/resources/viewer';

/**
 * The one way this design can be silently wrong.
 *
 * The bundles are generated and committed, so the page can render yesterday's model while every
 * other test passes on today's source - green, and drawing something nobody asked for. Rebuilding
 * into a temp directory and comparing bytes is the only check that reads what the page will actually
 * run.
 */
test('the committed bundles are what the sources build', async () => {
  const out = mkdtempSync(join(tmpdir(), 'codeflow-bundle-'));
  await buildViewer(out);
  for (const { out: name } of BUNDLES) {
    const fresh = readFileSync(join(out, name), 'utf8');
    const committed = readFileSync(join(VIEWER, name), 'utf8');
    // `ok` and not `equal`: a bundle is two megabytes, and a failing `equal` prints both of them.
    assert.ok(fresh === committed, `${name} is stale - run \`npm run build:viewer\``);
  }
});
