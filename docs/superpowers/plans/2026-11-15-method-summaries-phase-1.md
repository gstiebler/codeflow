# Method Summaries (Phase 1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Analyse every source method once into a summary of which inputs reach which outputs.
Compose summaries at call sites, solve recursion by fixpoint, and emit the result as `--summaries`
JSON. All of this happens alongside the existing per-call-site builder, without changing any of its
output.

**Architecture:**
- A second interpreter, `ir/Summarizer.kt`, walks the same `MethodBody.instructions` the builder
  consumes, over an abstract heap of interface paths and allocation sites (`ir/Summary.kt`).
- Dispatch is shared with the builder through `ir/Dispatch.kt`, so the two can't drift.
- `SummaryParityTest` holds the two interpreters to the same answers on every fixture, and lists
  every place they are allowed to differ.

**Tech Stack:** Kotlin, javac Tree API (JDK 25), Gradle, kotlin.test / JUnit 5, Gson (already used
by `JsonExporter`; check `app/build.gradle.kts` and use whatever JSON writer `JsonExporter` uses).

**Spec:** `docs/superpowers/specs/2026-11-15-method-summaries-design.md`. Read it before every
task. Also read `CLAUDE.md` and `docs/decisions/joins.md` ("Fields join too").

## Global Constraints

- JDK 25: `JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem ./gradlew test`.
- **No existing golden, test or output may change.** `--json`, `--html`, `--graphml` and Mermaid
  stay byte-identical. The full suite (192 Gradle tests plus `npm test`, 63 tests) stays green.
- stdout is the document; counters go to stderr. `Args` refuses unknown flags.
- Ask javac, never match strings: field and method keys come from `Element`s.
- No silently missing flow. Anything a summary can't follow becomes a gap with a kind, a
  `file:line:col` and a detail.
- Gap kinds, exactly: `dispatch`, `fixpoint`, `depth`, `unmodelled`, `body`.
- Atom spellings, exactly:
  - inputs: `P<i>`, `this.<qfield>[.<qfield>]`, `P<i>.<qfield>…`, `static.<qfield>`,
    `ext:<label>@<file:line:col>`
  - outputs: `RET`, the same field spellings, `ext-arg:<label>@<file:line:col>`
  - `<qfield>` is the declaring class's qualified name, a `.`, then the field name, e.g.
    `pkg.Loan.rate`.
- Field path depth cap: 2.
- Output format header: `{"format": "codeflow-summaries", "version": 1, ...}`. Methods are sorted
  by `method` and pairs are sorted.
- Commit in the worktree after each task. Message ends with
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **A field written in one arm of an `if` and read below it.** The summary must have both arms'
   flows, as the builder now does. The spike lost this; test it in Task 2.
2. **A recursive method whose result depends on a parameter only through recursion**, e.g.
   `int f(int n, int k){ return n == 0 ? k : f(n-1, k); }`. The summary must have `P1 → RET` and
   must terminate. Task 4.
3. **A virtual call on a parameter where the sources hold two overrides.** The output must carry a
   `dispatch` gap at the call's position, not a static guess. Task 3.
4. **An empty corpus slice** (`--from` naming a method with no body, such as an abstract or
   interface method). It must be listed with no flows and no crash. Task 5.
5. **The same corpus run twice.** The `--summaries` output must be byte-identical: no
   identity-hash order anywhere (this bit `MemPos` before). Task 5.

---

### Task 1: Share the dispatch rule

**Files:**
- Create: `app/src/main/kotlin/codeflow/ir/Dispatch.kt`
- Modify: `app/src/main/kotlin/codeflow/ir/IrGraphBuilder.kt` (`Frame.resolve` and `isVirtual`, at
  about lines 795–825)
- Test: the existing suite. This is a pure move.

**Interfaces:**
- Produces:
  - `object Dispatch`
  - `fun isVirtual(receiver: Receiver, declared: ExecutableElement): Boolean`
  - `fun resolve(globalCtx: GlobalContext, insn: Call, types: List<Element>): Method?`. `types` is
    the distinct constructed types of the receiver's objects. Empty means static resolution.
    Several implementations means `null`.
  - `fun overriddenInSources(globalCtx: GlobalContext, declared: ExecutableElement): Boolean`: true
    when some source method other than `declared` overrides it. Write it with
    `Symbols.overrides`, the way the spike's `Summarizer.overriders` does (branch `spike-summaries`,
    `app/src/main/kotlin/codeflow/ir/Summaries.kt`). Memoise it per `declared` in a map owned by
    the caller, or recompute it; it is not on the builder's path.

- [ ] **Step 1:** Move `isVirtual` and the body of `resolve` into `Dispatch`, keeping their KDoc.
  `Frame.resolve` becomes `Dispatch.resolve(globalCtx, insn, objects.mapNotNull { it.type }.distinct())`.
- [ ] **Step 2:** Run `./gradlew test --rerun-tasks`. Expected: all pass, and `git status` shows no
  change under `app/src/test/resources`.
- [ ] **Step 3:** Commit: "Dispatch rule in one place, for the builder and the summarizer".

### Task 2: Summary model and the intra-method interpreter

**Files:**
- Create: `app/src/main/kotlin/codeflow/ir/Summary.kt` (model plus spelling)
- Create: `app/src/main/kotlin/codeflow/ir/Summarizer.kt`
- Test: `app/src/test/kotlin/codeflow/ir/SummarizerTest.kt`

**Interfaces:**
- Produces, in `Summary.kt`:
  - `sealed interface Root`, with `This`, `ParamRoot(i: Int)` and `StaticRoot(cls: Element)`.
  - `sealed interface Obj`, with `PathObj(root: Root, fields: List<Element>)` and
    `AllocObj(site: List<String>, type: Element?)`.
    - `site` is the chain of `insn.source` positions, outermost call site first.
    - `equals`/`hashCode` are by value, never by identity.
  - `sealed interface Atom`, with `ParamAtom(i)`, `FieldAtom(obj: PathObj, field: Element)` and
    `ExtResult(label: String, site: String)`.
  - `data class AbsVal(val flows: Set<Atom>, val objs: Set<Obj>)` with `plus`, and `val EMPTY`.
  - `data class Gap(val kind: String, val at: String, val detail: String)`
  - `data class Summary(val ret: AbsVal, val effects: Map<Pair<Obj, Element>, AbsVal>, val sinks: Map<String, AbsVal>, val gaps: Set<Gap>)`
    - `sinks` is keyed by `"ext-arg:<label>@<site>"`.
  - `fun Summary.pairs(): List<Pair<String, String>>`: sorted, spelled exactly as in Global
    Constraints.
- Produces, in `Summarizer.kt`:
  - `class Summarizer(globalCtx: GlobalContext, bodyOf: (Method) -> MethodBody, initializersOf: (Element?) -> List<Insn>)`
  - `fun summary(method: Method): Summary`
  - `fun summariseAll(): Map<Method, Summary>`, in `globalCtx.sourceMethods()` order.
- In this task, a `Call` / `New` / `Delegate` is always opaque: `ExtResult` plus the argument and
  receiver flows, and a sink. Task 3 replaces that.
- Test helper in `SummarizerTest`:
  - `private fun summarise(source: String, method: String): Summary`
  - It writes `source` to `A.java` in a JUnit `@TempDir`, calls `AstReader(dir).analyse(listOf(file))`,
    finds the method by `MethodSpec.matches(it, method)`, and builds a `Summarizer` from an
    `IrGraphBuilder(analysis.globalCtx)`'s `bodyOf` / `initializersOf`.
  - `private fun pairs(...) = summarise(...).pairs().map { "${it.first}>${it.second}" }`.

- [ ] **Step 1: Write the failing tests** (class `A` in the default package, so `<qfield>` is `A.f`):

```kotlin
@Test fun aParameterReachesTheReturnThroughArithmetic() =
    assertEquals(listOf("P0>RET"), pairs("class A { int f(int a, int b) { int c = a * 2; return c; } }", "A#f"))
@Test fun aConstantReachesNothing() =
    assertEquals(emptyList(), pairs("class A { int f(int a) { return 3; } }", "A#f"))
@Test fun aFieldWriteIsAnEffectAndAFieldReadIsAnInput() {
    assertEquals(listOf("P0>this.A.x"), pairs("class A { int x; void f(int a) { this.x = a; } }", "A#f"))
    assertEquals(listOf("this.A.x>RET"), pairs("class A { int x; int f() { return x; } }", "A#f"))
}
@Test fun bothArmsOfAFieldWriteReachAReadBelowTheIf() =
    assertEquals(listOf("P0>RET", "P0>this.A.x", "P1>RET", "P1>this.A.x", "P2>RET", "P2>this.A.x"),
        pairs("class A { int x; int f(int a, int b, boolean c) { if (c) this.x = a; else this.x = b; return this.x; } }", "A#f"))
@Test fun aWriteThroughAParameterIsAnEffectOnThatParameter() =
    assertEquals(listOf("P1>P0.B.v"), pairs("class B { int v; } class A { void f(B b, int a) { b.v = a; } }", "A#f"))
@Test fun aStaticFieldIsKeyedByItsClass() =
    assertEquals(listOf("P0>static.A.s"), pairs("class A { static int s; void f(int a) { s = a; } }", "A#f"))
@Test fun anOpaqueCallIsAnExtResultAndASink() {
    val p = pairs("class A { int f(String a) { return a.length(); } }", "A#f")
    assertTrue(p.any { it.startsWith("P0>ext-arg:length@") }, "$p")
    assertTrue(p.any { it.startsWith("ext:length@") && it.endsWith(">RET") }, "$p")
    assertTrue("P0>RET" in p, "$p")
}
@Test fun anUnmodelledConstructIsAGap() =
    assertEquals(setOf("unmodelled"), summarise("class A { int f(double d) { return (int) d; } }", "A#f").gaps.map { it.kind }.toSet())
@Test fun aFieldPathDeeperThanTheCapIsAGap() =
    assertTrue(summarise("class C { int z; } class B { C c; } class D { B b; } class A { int f(D d) { return d.b.c.z; } }", "A#f").gaps.any { it.kind == "depth" })
```

  Before using the `(int) d` test, check that a cast is still unmodelled:
  `grep -n TYPE_CAST app/src/main/kotlin/codeflow/ir/Lowering.kt`. If casts are modelled now, use
  any construct that `MODELLED_EXPRESSIONS` doesn't list.

- [ ] **Step 2:** Run `./gradlew test --tests '*SummarizerTest*'`. Expected: FAIL to compile, since
  there is no `Summarizer` yet.
- [ ] **Step 3: Implement.**
  - Write the `eval` cases per the spec, section "The interpreter". The spike's `Summaries.kt` is a
    starting point, but it has the last-write-wins field bug and string field keys: fix both.
  - For `FieldFork` / `FieldArm` / `FieldJoin`, copy the builder's semantics from `IrGraphBuilder`
    (`openFork`, `endArm`, `mergeFields`, `closeFork`, `journalWrite`, `restore`) over the abstract
    heap `HashMap<Pair<Obj, Element>, AbsVal>`. A join merges every arm, including arms that end in
    `return` or `throw`.
  - An `ENUM_CONSTANT` read is `EMPTY`, as in the builder.
  - Use `LinkedHashMap` / `LinkedHashSet` throughout, so iteration order is insertion order.
- [ ] **Step 4:** Run the same command. Expected: PASS. Then make each test fail against a wrong
  implementation:
  - Turn `FieldJoin` into a no-op and confirm the `if` test fails.
  - Drop gate flows and confirm a test with a gated phi fails. If none does, add `int f(int a, boolean c) { int r = 0; if (c) r = 1; return r; }` → `P1>RET`.
- [ ] **Step 5:** Run `./gradlew test --rerun-tasks`. Expected: all pass. Commit: "Summaries: the
  model and the intra-method interpreter".

### Task 3: Calls, constructors and dispatch

**Files:**
- Modify: `app/src/main/kotlin/codeflow/ir/Summarizer.kt`
- Test: `app/src/test/kotlin/codeflow/ir/SummarizerTest.kt`

**Interfaces:**
- Consumes: `Dispatch.isVirtual`, `Dispatch.resolve` and `Dispatch.overriddenInSources` from
  Task 1, and the Task 2 model.
- Produces: `fun summary(method: Method, receiverType: Element? = null): Summary`, memoised by
  `(method.element, receiverType)`. A `this`-receiver call uses the caller's own `receiverType`. A
  call on a value receiver with exactly one constructed type passes that type.

- [ ] **Step 1: Write the failing tests:**

```kotlin
@Test fun aCalleeSummaryIsInstantiatedPerCallSite() =
    assertEquals(listOf("P0>RET"), pairs("class A { int id(int v) { return v; } int f(int a, int b) { int x = id(a); int y = id(b); return x; } }", "A#f"))
@Test fun anEffectThroughAParameterIsSeenByTheCaller() =
    assertEquals(listOf("P0>RET"), pairs("class B { int v; } class A { void set(B b, int a) { b.v = a; } int f(int a) { B b = new B(); set(b, a); return b.v; } }", "A#f"))
@Test fun aFactoryObjectCarriesItsFieldsBack() =
    assertEquals(listOf("P0>RET"), pairs("class B { int v; B(int v) { this.v = v; } } class A { B make(int a) { return new B(a); } int f(int a) { return make(a).v; } }", "A#f"))
@Test fun initializersRunBeforeTheConstructorBody() =
    assertEquals(listOf("P0>RET"), pairs("class B { int v = 1; int w; B(int a) { w = a + v; } } class A { int f(int a) { return new B(a).w; } }", "A#f"))
@Test fun superConstructorWritesAreSeenFromTheSubclass() =
    assertEquals(listOf("P0>RET"), pairs("class Base { int v; Base(int v) { this.v = v; } } class Sub extends Base { Sub(int a) { super(a); } } class A { int f(int a) { return new Sub(a).v; } }", "A#f"))
@Test fun dispatchOnThisIsSpecialisedByTheReceiverClass() {
    val src = "abstract class T { int run(int a) { return step(a); } abstract int step(int a); } " +
        "class K extends T { int step(int a) { return 0; } } class I extends T { int step(int a) { return a; } } " +
        "class A { int f(int a) { return new I().run(a); } int g(int a) { return new K().run(a); } }"
    assertEquals(listOf("P0>RET"), pairs(src, "A#f"))
    assertEquals(emptyList(), pairs(src, "A#g"))
}
@Test fun dispatchOnAParameterWithSeveralOverridesIsAGap() {
    val s = summarise("class S { int area(int a) { return a; } } class Q extends S { int area(int a) { return 0; } } class A { int f(S s, int a) { return s.area(a); } }", "A#f")
    assertEquals(listOf("dispatch"), s.gaps.map { it.kind })
    assertTrue("P1>RET" in s.pairs().map { "${it.first}>${it.second}" })
}
@Test fun varargsCollapseLikeTheBuilder() =
    assertEquals(listOf("P0>RET", "P1>RET"), pairs("class A { int sum(int... v) { return v[0]; } int f(int a, int b) { return sum(a, b); } }", "A#f"))
@Test fun aCalleeGapIsInheritedByTheCaller() =
    assertTrue(summarise("class A { int g(double d) { return (int) d; } int f(double d) { return g(d); } }", "A#f").gaps.any { it.kind == "unmodelled" })
```

  The varargs expectation must match what the builder draws. If `arrayAccess` is opaque so `v[0]`
  takes all of `v`, then both params reach. Check against `IrGraphBuilder` before finalising, and
  if the builder differs, assert the builder's answer.
- [ ] **Step 2:** Run `./gradlew test --tests '*SummarizerTest*'`. Expected: the new tests FAIL.
- [ ] **Step 3: Implement instantiation** per the spec, section "Calls".
  - It maps `PathObj` objects through the caller's heap, renames `AllocObj` by prefixing the call
    site's `insn.source`, and applies effects: strong for one callee, weak for several.
  - Copy the varargs collapse from the builder's `invoke`, not from the spike.
  - `New`, `Delegate` and initializers follow the builder's `construct` / `delegate` / `enter`.
  - A method whose lowering throws `GraphException` gets a `Summary` with a single `body` gap and no
    flows. Its callers treat it as opaque and inherit the gap.
  - Recursion in this task: if a summary is requested while it is in progress, use `EMPTY` for now.
    Task 4 makes that a fixpoint.
- [ ] **Step 4:** Run the tests. Expected: PASS. Mutation check: make dispatch always static and
  confirm `dispatchOnThisIsSpecialisedByTheReceiverClass` fails.
- [ ] **Step 5:** Run `./gradlew test --rerun-tasks`, all passing, then commit: "Summaries: compose
  at call sites, dispatch on the receiver class".

### Task 4: Fixpoint for recursion and loops

**Files:**
- Modify: `app/src/main/kotlin/codeflow/ir/Summarizer.kt`
- Test: `app/src/test/kotlin/codeflow/ir/SummarizerTest.kt`

**Interfaces:**
- Produces:
  - `Summarizer(..., budget: Int = 20)`: the maximum number of re-evaluations per summary key.
  - Allocation chains are k-limited to 2 sites: drop the oldest site.
  - The worklist re-queues the dependents of a summary whose value changed.
  - A key that exceeds its budget keeps its last value and gets a `fixpoint` gap at the method's
    position.
  - A loop body with back-edge phis re-runs until its values are stable, at most `budget` passes,
    and then gets a `fixpoint` gap.

- [ ] **Step 1: Write the failing tests:**

```kotlin
@Test fun recursionReachesAFixpoint() =
    assertEquals(listOf("P0>RET", "P1>RET"), pairs("class A { int f(int n, int k) { return n == 0 ? k : f(n - 1, k); } }", "A#f"))
@Test fun mutualRecursionReachesAFixpoint() =
    assertEquals(listOf("P1>RET"), pairs("class A { int e(int n, int k) { if (n == 0) return k; return o(n - 1, k); } int o(int n, int k) { return e(n - 1, k); } }", "A#o").filter { it == "P1>RET" })
@Test fun anAccumulatorLoopIsAFixpoint() =
    assertEquals(listOf("P0>RET", "P1>RET"), pairs("class A { int f(int n, int a) { int t = 0; for (int i = 0; i < n; i++) t = t + a; return t; } }", "A#f"))
@Test fun aRecursiveFactoryTerminates() {
    val s = summarise("class N { N next; } class A { N f(int n) { N x = new N(); if (n > 0) x.next = f(n - 1); return x; } }", "A#f")
    assertTrue(s.gaps.none { it.kind == "fixpoint" }, "${s.gaps}")
}
```

  Check the `n == 0 ? k :` part: whether the ternary condition flows into the value depends on how
  the builder draws `Select`. Assert whatever the builder's inputs say. `P0>RET` holds if `n` is an
  input of the `?:`, and `Select` carries all its inputs.
- [ ] **Step 2:** Run the tests. Expected: `recursionReachesAFixpoint` FAILS (it lacks `P1>RET`).
- [ ] **Step 3: Implement the worklist and loop iteration** per the interfaces above.
- [ ] **Step 4:** Run the tests. Expected: PASS. Mutation check: set the budget to 1 and confirm
  `recursionReachesAFixpoint` fails.
- [ ] **Step 5:** Run `./gradlew test --rerun-tasks`, all passing, then commit: "Summaries: recursion
  and loops solved by fixpoint".

### Task 5: `--summaries`

**Files:**
- Create: `app/src/main/kotlin/codeflow/SummariesExporter.kt`
- Modify: `app/src/main/kotlin/codeflow/App.kt` (`Args.FORMATS`, the format dispatch in `main`)
- Modify: `app/src/main/kotlin/codeflow/java/AstReader.kt`
  - Add `fun summarise(fileNames: List<Path>, entryPoints: List<String>): List<Pair<Method, Summary>>`.
  - It runs `analyse`, then `Summarizer.summariseAll()`, filtered by `MethodSpec.matches` when
    `entryPoints` is non-empty, and fails as `selectEntry` does for a spec matching nothing.
  - Then it runs `reportUnmodelled()` and closes the analysis.
- Test: `app/src/test/kotlin/codeflow/AppTest.kt`

**Interfaces:**
- Consumes: `Summary.pairs()` and `Summarizer.summariseAll()`.
- Produces:
  - `class SummariesExporter { fun export(entries: List<Pair<Method, Summary>>): String }`. The
    output is the document in the spec, section "Output".
    - `method` is `<qualified class>#<name>(<param types, erased, comma-separated>)`.
    - `at` is `method.ctx.location(method.name)`.
    - `flows` is a list of `[in, out]` pairs.
    - `gaps` is a list of `{kind, at, detail}`, sorted by `(at, kind, detail)`.
    - `counts` holds `methods`, `pairs` and a `gaps` map by kind.
  - The stderr line is `codeflow: summarised <n> methods, <p> pairs, <g> gaps`.

- [ ] **Step 1: Write the failing tests** in `AppTest`, using a `buildSummaries(testDir, files, from)`
  helper that calls `AstReader(...).summarise` and parses the result with the JSON parser the
  existing `buildJson` helper uses:

```kotlin
@Test fun summariesListEveryMethodWithItsFlows() {
    val doc = buildSummaries("funcCall", listOf(/* the fixture's files */))
    assertEquals("codeflow-summaries", doc["format"].asString)
    // every method the fixture declares is present, sorted; one known pair from the fixture is present
}
@Test fun summariesAreByteStableAcrossRuns() =
    assertEquals(rawSummaries("objectArgument"), rawSummaries("objectArgument"))
@Test fun summariesOfAnAbstractMethodAreEmptyNotACrash()   // fixture abstractMethod, --from naming the abstract one
@Test fun summariesRefuseAFromThatMatchesNothing()          // assertFailsWith<GraphException>
@Test fun summariesIsAKnownFlag()                           // Args(arrayOf("dir", "--summaries")).flags contains it
```

  Fill in the fixture-specific pair for the first test after reading `funcCall`'s sources. Run
  `rawSummaries` twice inside one JVM, and also once via `./gradlew run` in Step 4.
- [ ] **Step 2:** Run `./gradlew test --tests '*ummar*'`. Expected: FAIL.
- [ ] **Step 3: Implement the exporter, the `AstReader.summarise` path and the flag.** `--summaries`
  must not build any graph.
- [ ] **Step 4:** Run the tests, expecting PASS. Then run
  `./gradlew run --args="app/src/test/resources/objectArgument --summaries" > /tmp/a.json` twice and
  `cmp` the two files. Expected: identical.
- [ ] **Step 5:** Run `./gradlew test --rerun-tasks`, all passing, then commit: "--summaries: every
  method's summary as one JSON document".

### Task 6: Parity with the builder

**Files:**
- Create: `app/src/test/kotlin/codeflow/ir/SummaryParityTest.kt`
- Modify, only if needed: `IrGraphBuilder.kt`. A public `buildIsolated(root: Method): GraphBuilderBlock`
  that clears statics the way `build(roots)` does between roots: `forgetStatics()`,
  `unwrittenStaticReads.clear()`, `build(root, 0)`.

**Interfaces:**
- Consumes: `Summarizer`, `IrGraphBuilder`, `GraphBuilderBlock.parameterNodes` / `returnNode`, and
  `edgesIterator()`.

- [ ] **Step 1: Write the test.**
  - For every fixture directory that `InvariantsTest.fixtures()` uses (reuse its filter), analyse
    once.
  - For each source method with a non-void return that isn't a constructor:
    - **Oracle:** take `P<i>` → `RET` when a walk over node ids from `block.parameterNodes[i]`,
      following every edge kind through nested blocks, reaches `block.returnNode`. Use the walk
      `reaches()` uses in `AppTest`; lift it to a shared test helper if it is private.
    - **Summary:** the `P<i>>RET` pairs from `summary(method)`.
  - Compare the two as sets per `Class#method`.
  - Compare against `private val knownDifferences: Map<String, String>`, keyed
    `"<fixture>/<Class#method>/<pair>/<missing|extra>"`, where each value is a one-line reason.
  - The test fails listing every difference not in the map, and every map entry that no longer
    occurs, so the list stays honest in both directions.
  - If a lowering or build throws for a method, skip it only if the summary has a `body` gap, and
    fail otherwise.
- [ ] **Step 2:** Run `./gradlew test --tests '*SummaryParityTest*'` and read every difference.
  - A difference that is a summarizer bug gets fixed in `Summarizer.kt`, with a `SummarizerTest`
    that pins it.
  - A difference that is by design goes into `knownDifferences` with its reason. Expected reasons:
    - `recursion`: the builder's `EXTERNAL` cut passes every argument to the result, and the
      summary is more precise.
    - `dispatch gap`.
  - Nothing else goes in without a sentence saying why the builder's answer is wrong or
    unknowable.
- [ ] **Step 3:** Run it again. Expected: PASS. Mutation check: drop the field-join merge in the
  summarizer and confirm the parity test fails on `fieldIf`.
- [ ] **Step 4:** Run `./gradlew test --rerun-tasks`, all passing, then commit: "Summaries held to
  the builder's answers on every fixture".

### Task 7: Smoke, measurements and docs

**Files:**
- Modify: `scripts/smoke.sh`
  - After the graph run, run `"$bin" "$src" --summaries` once, timing it.
  - Report its bytes and seconds, plus the `codeflow: summarised` line.
  - Fail on a stack trace or on a document that `python3 -c 'import json,sys; json.load(sys.stdin)'`
    rejects.
- Create: `docs/decisions/summaries.md`. It covers:
  - what a summary is
  - the atoms
  - the gap kinds and why each is a gap and not a guess
  - the parity test and its known-differences list
  - the Fineract numbers
- Modify: `CLAUDE.md`
  - Add a pipeline sentence: `ir.Summarizer`, once per method, behind `--summaries`.
  - Add the `--summaries` command line.
  - Add a decision-records row for `summaries.md`.
- Modify: `docs/if-written-again.md` §2. Add a "**Status:**" paragraph saying Phase 1 has landed,
  with its numbers, and that bodies drawn once (Phase 2) is next.

- [ ] **Step 1:** Edit `smoke.sh`.
  - Fetch the corpus as `.github/workflows/smoke.yml` does, into the scratchpad, or reuse an
    existing checkout if one is at
    `/private/tmp/claude-501/-Users-guistiebler--claude-spares-spare-ryRf4v/c1fd7b7f-24c2-4462-82bb-1552872273df/scratchpad/fin`.
  - Run `./gradlew installDist && scripts/smoke.sh app/build/install/app/bin/app <fin>/fineract-progressive-loan/src/main/java`.
  - Expected: 658/658 roots graphed as before, the summaries document is valid JSON, and the
    report prints its time, size, pairs and gaps.
- [ ] **Step 2:** Run the parity check on Fineract once, by hand.
  - Write a small throwaway main under `app/src/test/kotlin`, not committed, or a test gated on an
    env var `CODEFLOW_PARITY_SRC`. Commit only the env-gated test.
  - It applies Task 6's comparison to the 658 roots and prints recall, precision and the
    differences by reason.
- [ ] **Step 3:** Write the docs with those numbers.
- [ ] **Step 4:** Run `./gradlew test --rerun-tasks && npm test`. Expected: all pass, and no file
  under `app/src/test/resources` changed except new files.
- [ ] **Step 5:** Commit: "Summaries: smoke on Fineract, decision record, status".
