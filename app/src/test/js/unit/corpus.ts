import { readdirSync, readFileSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';
import type { Payload } from '../../../main/resources/viewer/types.ts';

const RESOURCES = fileURLToPath(new URL('../../resources', import.meta.url));

/** How many fixtures the golden suite writes. Below this, something did not run - see [loadCorpus]. */
export const EXPECTED_AT_LEAST = 60;

/**
 * Every fixture's payload, read from the graph.json the Kotlin suite writes.
 *
 * These are gitignored and rewritten on every `./gradlew test`, so a checkout that has not run one
 * has none - and a sweep over zero fixtures passes every property it is given while proving nothing.
 * That is the same trap as a negative browser assertion, so finding too few is a failure with an
 * instruction, not an empty loop.
 */
export function loadCorpus() {
  // Annotated rather than inferred: `JSON.parse` returns `any`, so without this every sweep over the
  // corpus typechecks against nothing at all - the checks would compile and mean no more than the
  // untyped ones did.
  const corpus: { name: string; payload: Payload }[] = [];
  for (const name of readdirSync(RESOURCES, { withFileTypes: true })) {
    if (!name.isDirectory()) continue;
    const path = join(RESOURCES, name.name, 'graph.json');
    if (existsSync(path)) corpus.push({ name: name.name, payload: JSON.parse(readFileSync(path, 'utf8')) });
  }
  if (corpus.length < EXPECTED_AT_LEAST) {
    throw new Error(
      `found ${corpus.length} fixture payloads, expected at least ${EXPECTED_AT_LEAST}. ` +
      'Run `./gradlew test` first - graph.json is gitignored and written by the golden suite.',
    );
  }
  return corpus;
}
