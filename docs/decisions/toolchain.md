# Toolchain: JDKs, Lombok, the bundle

Moved verbatim from CLAUDE.md, which now keeps only the rules. This is the reasoning behind them.

# Commands

```shell
./gradlew build                      # compile + test
./gradlew test --rerun-tasks         # tests, ignoring up-to-date checks
./gradlew test --tests '*ternary*'   # one test
UPDATE_SNAPSHOTS=1 ./gradlew test    # rewrite the golden files (see Testing)
./gradlew run --args="path/to/java/dir"
./gradlew run --args="path/to/java/dir --html"   # interactive page, to stdout
./gradlew run --args="path/to/java/dir --from Report#total"   # root it somewhere other than main

npm test                             # the viewer's pure functions, under node --test
npm run test:browser                 # the exported page, in a real browser
```

The JS suites need `npm install` once. `npm test` globs the files itself
(`app/src/test/js/unit/*.test.mjs`) because `node --test <dir>` tries to import the directory and
dies before running anything.

`scripts/delombok.sh <src> [out]` is the one preprocessing step, for a Lombok-annotated corpus.
codeflow parses with `-proc:none` and no classpath, so a `@Getter` accessor, a
`@RequiredArgsConstructor` constructor and `@Slf4j`'s `log` field do not exist in the attributed tree
and every use of one is drawn opaque; `delombok` writes them out as ordinary source, which is
codeflow's input contract with nothing added. Running Lombok *inside* codeflow instead would need a
jar on the processor path, `--add-opens` on the analysing JVM, and would let a processor found in the
analysed corpus execute during what the reader thinks is a read. The trade is that every `file:line`
codeflow prints afterwards names the delomboked copy. `docs/externals.md` measures both halves.

**Two JDK numbers, and they are not interchangeable.** codeflow parses with the *running* JDK's
javac (`ToolProvider.getSystemJavaCompiler()`), so the JDK is an input to the output and not just to
the build. `app/build.gradle` therefore compiles to bytecode **21** — `jvmToolchain(21)`, because
Kotlin 1.9 cannot target 25 and fails with "Inconsistent JVM-target compatibility" — and runs
`test` and `run` on **25** through a `javaLauncher` override (`def runtimeJdk = 25`). Bytecode 21
runs on 25 unchanged, so the two are free to differ.

Runtime 25 is not a preference. On javac 21 the whole test suite passes and real input does not:
attributing a `yield` whose type javac never worked out trips an internal assertion in `Attr`
(`Attr$1.visitYield`), and every root in Fineract's largest module produced zero bytes. javac 25
does not have that bug, and every golden is byte-identical across the two — which is what says
this is a fix and not a rewrite. What the tests assert has to be what a reader gets, so the suite
runs on the javac the tool is pointed at.

The installed CLI (`./gradlew installDist`, then `./app/build/install/app/bin/app <dir>`) cannot be
pinned from the build — its start script takes whatever `JAVA_HOME` names — so set a 25 yourself:
`JAVA_HOME=$HOME/.sdkman/candidates/java/25.0.4-tem` (or `$(/usr/libexec/java_home -v 25)`). Older
than 21 fails outright with `UnsupportedClassVersionError`; 21 through 24 run but carry the javac
bug above, which is the worse failure because it is silent about being one.

