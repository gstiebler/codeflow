# Boundary-Diff Dataflow Spike Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Build a throwaway `spike/` tree that turns two Fineract commits into a boundary-pair diff - the input-to-output value paths a change added, removed, or re-routed, printed as an expandable text tree - and decide from three real commits whether the view in the spec is worth building for real.

**Architecture:** Joern does the parsing. `spike/build-cpg.sh` builds one CPG per commit from a detached git worktree. One Scala script, `spike/joern/export.sc`, dumps that CPG to a flat `graph.json` and contains no analysis whatsoever. Every decision - collapsing AST nodes into statements, typing boundaries, collapsing cycles, slicing, matching statements across versions, diffing, rendering - is TypeScript under `spike/src/`, unit-tested with `node --test` against a hand-written in-code fixture. Nothing under `app/` changes; the spike does not import from the Kotlin engine and the Kotlin engine does not learn about the spike.

**Tech Stack:** Joern 4.0.570 (`javasrc2cpg` and `joern --script`, already installed under `/opt/homebrew/Cellar/joern/4.0.570/libexec`), a Scala 3 dumper script, TypeScript 7 run directly by Node 26 (`node --test spike/test/*.test.ts`, the same setup `app/src/test/js/unit` already uses), bash.

**Spec:** `docs/superpowers/specs/2026-09-01-boundary-diff-view-design.md`

---

## Global Constraints

- **Nothing under `app/` changes.** The spike is additive: a new `spike/` tree, two lines in `package.json`, one line in `tsconfig.json`, four lines in `.gitignore`. If a task seems to need an `app/` edit, it is the wrong task.
- **No new npm dependencies.** `node:test`, `node:assert/strict`, `node:fs`, `node:child_process` only.
- **Joern lives outside the repo.** Refer to it as `$JOERN_HOME`, defaulting to `/opt/homebrew/Cellar/joern/4.0.570/libexec`. Never vendor it, never add it to the Gradle build.
- **CPGs must be built with `--delombok-mode no-delombok`.** Joern's default and `types-only` delombok modes *deadlock* on Fineract modules: Joern shells out to `lombok.launch.Main delombok`, that child writes its files and then neither process ever returns (a full stderr pipe from the cross-module "cannot find symbol" errors). A manual `java -jar lombok.jar delombok` on the same tree finishes in 8 seconds, so the hang is Joern's subprocess handling, not Lombok. Do not spend spike time fixing it.
- **Consequence of no-delombok, which the design must absorb:** Lombok-generated getters and setters do not exist in the CPG. They appear as calls with `methodFullName` starting `<unresolvedNamespace>`. Task 4's accessor rule is the bridge, and it must key off the receiver's *simple* type name, because the namespace is exactly what is missing.
- **Never use the default delombok mode even if the deadlock is fixed.** Its line numbers come from the delomboked copy, not the original source: in a measured comparison, source line 85 reported as 71, 427 as 354, 252 as 205. Every line number the spike prints would be wrong.
- **`node --test` unit tests never invoke Joern.** They run against `spike/test/corpus.ts`, a hand-written `Graph` value. Joern is exercised by one shell smoke script that a human runs.
- **TypeScript is `strict`.** Imports carry the `.ts` extension (`allowImportingTsExtensions` is already on). Do not add `"type": "module"` to `package.json` - the existing comment in that file explains at length why it is deliberately absent.
- **Build outputs are gitignored:** `spike/out/`, `spike/worktrees/`, `*.cpg`, `spike/workspace/`. Committed fixtures are only the small ones under `spike/fixtures/`.
- **Two-week time box.** Task 10 writes the go/no-go. A negative answer is a successful spike.

---

## What Joern actually gives us

Measured on 2026-09-02 against `fineract-working-capital-loan/src/main/java` at Fineract commit `542da12e6`, CPG built no-delombok (16 s wall, 1.3 MB). Every number below was printed by a probe script; none is an estimate. Build these into the code, do not re-derive them.

| Question | Answer |
|---|---|
| JAX-RS resource classes | 15, via `cpg.typeDecl.where(_.annotation.name("Path"))`; path string via `annotation.name("Path").parameterAssign.value.code` |
| HTTP endpoint methods | 87, via `cpg.method.where(_.annotation.name("GET\|POST\|PUT\|DELETE"))` |
| JPA entities | 24, via `annotation.name("Entity")`; table name from the `Table` annotation; column members via `member.astChildren.isAnnotation` |
| Repository calls | 301, via `cpg.call.where(_.receiver.typ.fullName(".*Repository"))` |
| `JsonCommand` parameter reads | 89, via `cpg.call.name(".*ValueOfParameterNamed.*")` |
| Call resolution rate | 9032 non-operator calls, 6794 resolved (75%), single-module CPG |
| Control dependence | 18234 CDG edges, reachable per node via `outE("CDG")` |

Four findings that change the design and are easy to get wrong:

1. **`throw` is not a control structure.** `cpg.controlStructure.controlStructureTypeExact("THROW")` returns **zero**; the distinct control-structure types in the whole module are IF, TRY, CATCH, ELSE, WHILE, FOR, CONTINUE, BREAK, MATCH, SWITCH. Throws are calls: `cpg.call.nameExact("<operator>.throw")` returns **128**. The thrown type is *not* in the arguments - `throw new LockedReadException(loanId, e)` reports its single argument as `BLOCK:<empty>`, because `new` is lowered to a block. Get the type from the node's `code` with `/throw\s+new\s+([\w.]+)/`, and fall back to `rethrow` for `throw re;`.
2. **REACHING_DEF edges carry no variable name.** `edge.property` is empty on every one of them. The carrier must be read off the *source node*: its `code` for an IDENTIFIER or METHOD_PARAMETER_IN, its member name for a FIELD_IDENTIFIER, its callee name for a CALL.
3. **REACHING_DEF edges out of the METHOD node are noise.** Joern hangs the first use of every parameter and local off the method node itself. Drop every edge whose source label is `METHOD`.
4. **The most interesting new exception in the acceptance set has no `throw` in the source.** Fineract commit `542da12e6` ends `getDefaultAllocationRule` with `...findFirst().get()` - the new failure is `Optional.get` throwing `NoSuchElementException`. A rule that only looks at `<operator>.throw` scores zero on the headline acceptance case. Task 4 therefore needs the implicit-throw table.

Two facts about running Joern that will otherwise cost the executor an hour each:

- **A Joern script run costs about 2.5 minutes on this CPG**, nearly all of it applying the default overlays on import, and the Scala compiles first, so a typo costs a full run. Compile-check by keeping the script small and running it against the tiny fixture CPG first.
- **Scala API traps hit during the probes:** flow and AST elements are `AstNode`, so use `.lineNumber` and `.code`, never `.method`; `flatgraph.Edge` exposes `.src`, `.dst`, `.property`, not `outNode`/`inNode`/`propertyOption`; `Member` has no `.fullName` and no `.annotation` (use `.name`, `.typeDecl.name`, `.astChildren.isAnnotation`); a traversal is a single-use iterator, so materialise with `.l` before asking twice - calling `.size` on a source set silently empties it.

---

## File Structure

```
spike/
  README.md              how to build a CPG, dump it, and run the three verbs
  build-cpg.sh           (commit, dirs...) -> a .cpg, via a detached git worktree
  dump.sh                a .cpg -> graph.json, wrapping `joern --script`
  joern/export.sc        the dumper. No analysis. ~120 lines of Scala
  fixtures/tiny/         a self-contained Java tree, ~6 files, no external jars
  src/types.ts           the graph.json contract, and the statement model
  src/load.ts            graph.json -> statements + value edges
  src/boundaries.ts      the seven boundary rules
  src/scc.ts             Tarjan, iterative
  src/slice.ts           forward, backward, and all input-to-output pairs
  src/fingerprint.ts     statement identity that survives a move
  src/diff.ts            boundary-pair diff: NEW, GONE, PATH, SAME
  src/render.ts          the text tree and the diff report
  src/cli.ts             forward | backward | boundary-diff
  test/corpus.ts         the hand-written Graph every unit test runs against
  test/*.test.ts         one per src module
  test/extract.sh        the only thing that runs Joern; a human runs it
  FINDINGS.md            written by Task 10: the go/no-go
```

Each `src` module is a pure function of its input and imports only from `src`. `cli.ts` is the only file that touches the filesystem or `process.argv`.

---
## Tasks

Each task ends with a commit and an independently runnable test. Tasks 2 through 9 are pure
TypeScript and never touch Joern; they run in seconds. Task 1 and Task 10 are the two ends that
touch real Fineract.

### Task 1: Scaffold

**Files:** create `spike/README.md`; modify `package.json` (add `"test:spike": "node --test spike/test/*.test.ts"`), `tsconfig.json` (add `"spike/**/*.ts"` to `include`), `.gitignore` (add `spike/out`, `spike/worktrees`, `spike/workspace`, `*.cpg`).

**Interfaces:** produces the `npm run test:spike` entry point every later task uses.

- [x] Add the four gitignore lines, the npm script, and the tsconfig include.
- [x] Write `spike/README.md`: what the spike is for, the three commands, and the time box.
- [x] Run `npx tsc` and confirm it still passes with nothing new to compile.
- [x] Commit.

### Task 2: The graph contract and the hand fixture

**Files:** create `spike/src/types.ts`, `spike/test/corpus.ts`, `spike/test/corpus.test.ts`.

**Interfaces:** produces `Graph`, `TypeFact`, `MethodFact`, `NodeFact`, `EdgeFact`, `Ann` in `types.ts`, and `hand(): Graph` in `corpus.ts`. Every later task consumes these.

`hand()` is a twelve-statement Java program expressed directly as a `Graph`: a `@POST` endpoint that
reads a repository, calls a service, saves, and returns; a service that writes a field and reads it
back; a two-statement cycle; and an `Optional.get`. It is the contract `export.sc` must satisfy, and
it is deliberately hand-written so no unit test depends on Joern's behaviour.

- [x] Write `types.ts`.
- [x] Write `corpus.ts` building that graph.
- [x] Write `corpus.test.ts` asserting the fixture's shape (node count, that every edge endpoint exists, that every node's method is a declared method). Run it - it must fail before the fixture exists.
- [x] Run `npm run test:spike`. Commit.

### Task 3: Statements and value edges

**Files:** create `spike/src/load.ts`, `spike/test/load.test.ts`.

**Interfaces:** produces `buildModel(g: Graph): Model` where `Model = { stmts, edges, byId, graph }`,
`Stmt = { key, file, line, code, method }`, `ValueEdge = { from, to, carrier, kind }` with
`kind: 'local' | 'field' | 'arg' | 'ret' | 'guard'`.

A statement is `file:line`. Five edge kinds are built: `local` from REACHING_DEF (dropping METHOD
sources and self-edges, carrier read off the source node); `arg` from each resolved call's argument
*i* to the callee's parameter *i*; `ret` from each callee return to the call site; `field` from every
statement writing a member to every statement reading it; `guard` from CDG.

- [x] Test: the fixture's field write at `Svc.java:11` reaches the read at `Svc.java:12` by a `field` edge carrying `Svc.total`. Fails first.
- [x] Test: the call at `Api.java:23` produces `arg` edges into `Svc.apply`'s parameters and a `ret` edge back.
- [x] Test: no edge has a METHOD-labelled source and no edge is a self-loop.
- [x] Implement, run, commit.

### Task 4: Boundary rules

**Files:** create `spike/src/boundaries.ts`, `spike/test/boundaries.test.ts`.

**Interfaces:** produces `boundaries(m: Model): Boundary[]`,
`Boundary = { key, stmt, dir: 'in'|'out', kind: 'net'|'db'|'throw'|'json', label }`.

Seven rules, one test each: HTTP endpoint in (verb plus class path plus method path); HTTP endpoint
out (its returns); repository read; repository write; JDBC; explicit throw (`<operator>.throw`, type
by regex on the code); implicit throw (a table keyed on `methodFullName` - `Optional.get`,
`Optional.orElseThrow`, `Iterator.next`, `List.get` - plus an unresolved fallback keyed on the
receiver's simple type or the receiving call's name, which is what catches `findFirst().get()`);
and `JsonCommand` parameter reads.

- [x] One failing test per rule, then the implementation, then the run. Commit.

### Task 5: Cycles

**Files:** create `spike/src/scc.ts`, `spike/test/scc.test.ts`.

**Interfaces:** produces `condense(m: Model): Condensation` with `componentOf: Map<string,string>`,
`members: Map<string,string[]>`, and the collapsed edge list.

Tarjan, written iteratively - Fineract methods nest deep enough to blow a recursive one.

- [x] Test: the fixture's two-statement cycle becomes one component with two members; every other statement is its own singleton. Fails first. Implement, run, commit.

### Task 6: Slicing

**Files:** create `spike/src/slice.ts`, `spike/test/slice.test.ts`.

**Interfaces:** produces `forward(m, c, stmtKey): Hit[]`, `backward(m, c, boundaryKey): Hit[]`,
`allPairs(m, c): Pair[]`, with `Hit = { boundary, path: string[] }` and
`Pair = { source, sink, path }`. Breadth-first over the condensation, one shortest path per
boundary reached, expansion stopping at an out-boundary.

- [x] Test: forward from the endpoint reaches the `db:out` save and the `net:out` return, and the path passes through the service.
- [x] Test: backward from the `db:out` reaches the endpoint's `net:in`.
- [x] Test: `allPairs` on the fixture returns exactly the expected source-sink pairs.
- [x] Implement, run, commit.

### Task 7: Statement identity across versions

**Files:** create `spike/src/fingerprint.ts`, `spike/test/fingerprint.test.ts`.

**Interfaces:** produces `normalise(code: string): string` and `fingerprint(m, stmtKey): string`,
the latter being the enclosing method's *simple* name, the normalised code, and an ordinal among
identical statements in that method. Line numbers and enclosing class are deliberately absent: that
is what makes a fingerprint survive the 130-line method move in Fineract `d602928ae`.

- [x] Test: the same statement at a different line in a different class, same method name, fingerprints identically. Fails first.
- [x] Test: two textually identical statements in one method get different fingerprints.
- [x] Implement, run, commit.

### Task 8: The diff

**Files:** create `spike/src/diff.ts`, `spike/test/diff.test.ts`.

**Interfaces:** produces `diffPairs(before: Pair[], after: Pair[], fpBefore, fpAfter): DiffRow[]`
with `status: 'NEW' | 'GONE' | 'PATH' | 'SAME'`. A pair is keyed by its source and sink labels; a
pair present on both sides whose fingerprint sequence differs is `PATH`.

- [x] Test: dropping a sink from the after set yields exactly one `GONE`.
- [x] Test: a pair whose middle statement changed yields `PATH`, not `SAME`.
- [x] Test: renumbering every line without changing code yields all `SAME`.
- [x] Implement, run, commit.

### Task 9: Rendering and the CLI

**Files:** create `spike/src/render.ts`, `spike/src/cli.ts`, `spike/test/render.test.ts`.

**Interfaces:** produces `tree(hits, m): string` (merged common prefixes, box-drawing characters,
the `[via <carrier>]` suffix, the boundary and cycle decorations from the spec) and
`diffReport(rows): string`. `cli.ts` takes `forward <graph.json> <File.java:123>`,
`backward <graph.json> <label>`, `boundary-diff <before.json> <after.json>`.

- [x] Test: two hits sharing a prefix render as one shared branch, not two.
- [x] Test: a cycle component renders as a single line naming its size.
- [x] Implement, run, commit.

### Task 10: Extraction, and the three-commit verdict

**Files:** create `spike/joern/export.sc`, `spike/build-cpg.sh`, `spike/dump.sh`,
`spike/fixtures/tiny/**`, `spike/test/extract.sh`, `spike/FINDINGS.md`.

`export.sc` walks methods, type declarations, AST nodes with line numbers, and REACHING_DEF and CDG
edges, and prints the JSON the fixture describes. It contains no filtering and no analysis; if a
rule feels tempting to add here, it belongs in `boundaries.ts`.

- [x] Write the tiny Java fixture, with its own annotation declarations so it needs no external jars.
- [x] Write `build-cpg.sh` and `dump.sh` and run them on the tiny fixture. Assert the JSON parses as a `Graph` and that the shapes match `corpus.ts`.
- [x] Build before-and-after CPGs for Fineract `542da12e6`, corpus `fineract-working-capital-loan/src/main/java`. Run `boundary-diff`. Expected: the `DUE_PRINCIPAL` fallback path is `GONE`, and a `NEW` `throws NoSuchElementException` appears at `WorkingCapitalLoanAllocationRequestFactory.getDefaultAllocationRule`.
- [x] Build before-and-after for `d602928ae`, same corpus. Expected: the boundary pair set is identical either side, because the change is a method move.
- [x] Build before-and-after for `745ae67de`, corpus `fineract-loan`, `fineract-savings`, and the `client`, `shareaccounts` and `savings` packages of `fineract-provider`. Expected: rows group by module, with a `NEW` throw for `amount.rounded.to.zero`.
- [x] Write `FINDINGS.md`: what each of the three produced, what was hand-edited, and the go/no-go. Two of three clean is a pass.
- [x] Commit.
