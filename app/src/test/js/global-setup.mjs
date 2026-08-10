import { execSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

/**
 * Builds a real page from a real fixture before the browser tests run.
 *
 * Going through `gradlew run` rather than the installed CLI means the JDK is Gradle's problem, not
 * ours - the `run` task pins its launcher to the same version the test task uses, while the installed
 * script takes whatever `java` is on PATH and dies on an older one.
 *
 * The fixture path is absolute because the `run` task's working directory is `app/`, not the repo
 * root, so a repo-relative path silently resolves to `app/app/...` and Files.walk throws.
 */
/**
 * `member` earns its place beside `funcCall`: `app.func1()` passes no argument and returns nothing,
 * so not one edge crosses from `main` into `func1`, and the callee is reachable only through call
 * structure. It is the fixture where following dataflow finds nothing at all.
 */
const FIXTURES = ['funcCall', 'member'];

/**
 * Both pages, from one payload.
 *
 * The suite asks each fixture's questions of both renderers, so both have to exist. `--html` is the
 * React Flow page and `--html-cytoscape` the older one; the two are built from the same sources in
 * the same run, so a disagreement between them is a fact about model.ts rather than about which
 * build produced which file.
 */
const PAGES = [{ flag: '--html', suffix: '' }, { flag: '--html-cytoscape', suffix: '-cytoscape' }];

export default function globalSetup() {
  mkdirSync('build/viewer-test', { recursive: true });
  for (const name of FIXTURES) {
    for (const { flag, suffix } of PAGES) {
      const html = execSync(
        `./gradlew -q run --args="${resolve(`app/src/test/resources/${name}`)} ${flag}"`,
        // stderr is ignored, not inherited: codeflow logs at debug level there and it buries the test
        // report. A build failure still surfaces, as execSync throws on a non-zero exit.
        { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024, stdio: ['ignore', 'pipe', 'ignore'] },
      );
      writeFileSync(`build/viewer-test/${name}${suffix}.html`, html);
    }
  }
}
