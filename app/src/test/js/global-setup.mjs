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
export default function globalSetup() {
  const fixture = resolve('app/src/test/resources/funcCall');
  mkdirSync('build/viewer-test', { recursive: true });
  const html = execSync(
    `./gradlew -q run --args="${fixture} --html"`,
    // stderr is ignored, not inherited: codeflow logs at debug level there and it buries the test
    // report. A build failure still surfaces, as execSync throws on a non-zero exit.
    { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024, stdio: ['ignore', 'pipe', 'ignore'] },
  );
  writeFileSync('build/viewer-test/funcCall.html', html);
}
