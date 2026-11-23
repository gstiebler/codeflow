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
 * `boundaryRoots` is drawn as its golden is, with its roots and `--boundary`: it is the page where a
 * boundary call's `call` is bold and the order token has its own fill.
 *
 * `boundary` is drawn as AppTest draws it: three boundary methods, five call sites, so the condensed
 * view opens on seven nodes - a box per call site (the entry method's and five calls), and the entry
 * point's event.
 */
const FIXTURES = {
  funcCall: '',
  boundaryRoots: '--from App#first --from App#second --boundary Store',
  boundary: '--from App#entrypoint --boundary Boundary',
  boundaryBranch: '--from App#entrypoint --boundary Port',
};

/**
 * `bigBoundary`: twenty methods of five steps, each calling `Port` twice per step - 200 boundary call
 * sites in a payload of a couple of MB. Written here rather than kept in `src/test/resources`, where
 * every directory is also a golden test. It exists for one reason: the Boundaries panel recomputes
 * every row on every render, and on a payload this size that costs more than React Flow's own
 * measurement of a node, which is the race `measured.spec.ts` is about. A small fixture draws too
 * fast to lose it.
 */
function bigBoundary() {
  const dir = resolve('build/viewer-test/src/bigBoundary');
  mkdirSync(dir, { recursive: true });
  const methods = Array.from({ length: 20 }, (_, m) => [
    `  static int m${m}(int a, int b) {`,
    '    int x = a;',
    ...Array.from({ length: 5 }, (_, s) => [
      `    x = x + b * ${s + 1};`,
      `    if (x > ${s}) { Port.send(x); } else { x = x - a; }`,
      '    Port.flush(x);',
    ]).flat(),
    '    return x;',
    '  }',
  ].join('\n'));
  const calls = methods.map((_, m) => `    m${m}(a, b);`).join('\n');
  writeFileSync(`${dir}/App.java`,
    `package bigBoundary;\npublic class App {\n  static void entrypoint(int a, int b) {\n${calls}\n  }\n${methods.join('\n')}\n}\n`);
  writeFileSync(`${dir}/Port.java`, 'package bigBoundary;\npublic class Port {\n  static void send(int x) {}\n  static void flush(int x) {}\n}\n');
  return dir;
}

function highLevelSample() {
  const dir = resolve('build/viewer-test/src/highLevel');
  mkdirSync(dir, { recursive: true });
  writeFileSync(`${dir}/Loan.java`, [
    'package highLevel;',
    '@Entity',
    'public class Loan { public int balance; }',
  ].join('\n'));
  writeFileSync(`${dir}/LoanRepository.java`, [
    'package highLevel;',
    '@Repository',
    'public interface LoanRepository { void save(Loan loan); }',
  ].join('\n'));
  writeFileSync(`${dir}/Logger.java`, [
    'package highLevel;',
    'public class Logger { public void info(String msg) {} }',
  ].join('\n'));
  writeFileSync(`${dir}/LoanService.java`, [
    'package highLevel;',
    'public interface LoanService { int apply(Loan loan, int amount); }',
  ].join('\n'));
  writeFileSync(`${dir}/LoanServiceImpl.java`, [
    'package highLevel;',
    '@Service',
    'public class LoanServiceImpl implements LoanService {',
    '  private LoanRepository repo;',
    '  @Override',
    '  public int apply(Loan loan, int amount) {',
    '    int next = loan.balance - amount;',
    '    loan.balance = next;',
    '    repo.save(loan);',
    '    return loan.balance;',
    '  }',
    '}',
  ].join('\n'));
  writeFileSync(`${dir}/LoanController.java`, [
    'package highLevel;',
    '@RestController',
    '@RequestMapping("/loans")',
    'public class LoanController {',
    '  private LoanService service;',
    '  private Logger log;',
    '  @PostMapping("/repay")',
    '  public int repay(Loan loan, int amount) {',
    '    log.info("repaying");',
    '    if (amount <= 0) {',
    '      throw new IllegalArgumentException("bad");',
    '    }',
    '    return service.apply(loan, amount);',
    '  }',
    '}',
  ].join('\n'));
  return dir;
}

export default function globalSetup() {
  mkdirSync('build/viewer-test', { recursive: true });
  const big = bigBoundary();
  const html = execSync(
    `./gradlew -q run --args="${big} --html --from App#entrypoint --boundary Port"`,
    { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024, stdio: ['ignore', 'pipe', 'ignore'] },
  );
  writeFileSync('build/viewer-test/bigBoundary.html', html);
  const hl = highLevelSample();
  const hlHtml = execSync(
    `./gradlew -q run --args="${hl} --html --assume-single-impl --auto-boundaries --discover-roots --level architecture"`,
    { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024, stdio: ['ignore', 'pipe', 'ignore'] },
  );
  writeFileSync('build/viewer-test/highLevel.html', hlHtml);
  for (const [name, flags] of Object.entries(FIXTURES)) {
    const html = execSync(
      `./gradlew -q run --args="${resolve(`app/src/test/resources/${name}`)} --html ${flags}"`,
      // stderr is ignored, not inherited: codeflow logs at debug level there and it buries the test
      // report. A build failure still surfaces, as execSync throws on a non-zero exit.
      { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024, stdio: ['ignore', 'pipe', 'ignore'] },
    );
    writeFileSync(`build/viewer-test/${name}.html`, html);
  }
}
