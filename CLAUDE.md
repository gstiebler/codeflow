# CLAUDE.md

codeflow reads Java source and emits a dataflow graph: which value reaches which, through which
operators and calls. It is pointed at code nobody has read yet and must be believed, so **a silently
wrong graph is worse than a failure** — a wrong diagram is readable, plausible, and gives no sign
anything is missing. Nearly every rule below follows from that.

The reasoning behind each rule is in `docs/decisions/` (index at the bottom). Read the matching page
before changing the area it covers; most rules there were paid for by a real bug.

## Commands

```shell
./gradlew build                      # compile + test
./gradlew test --rerun-tasks         # tests, ignoring up-to-date checks
./gradlew test --tests '*ternary*'   # one test
UPDATE_SNAPSHOTS=1 ./gradlew test    # rewrite the golden files
./gradlew run --args="path/to/java/dir"
./gradlew run --args="path/to/java/dir --html"                 # interactive page, to stdout
./gradlew run --args="path/to/java/dir --from Report#total"    # root(s) other than main; repeatable
./gradlew run --args="path/to/java/dir --summaries"            # every method's summary, as JSON
./gradlew run --args="path/to/java/dir --boundary Gateway"     # Gateway's methods not read: params → call <name> → result; repeatable
./gradlew run --args="path/to/java/dir @roots.args --html"     # @file: its lines' arguments in place (# comments, blank lines skipped)
scripts/smoke.sh app/build/install/app/bin/app path/to/java/dir  # every method a root; fails on a crash or a skipped root

npm test                             # typecheck, bundle freshness, viewer unit tests + corpus sweeps
npm run test:browser                 # the exported page, in a real browser (Playwright)
npm run check:bundle                 # committed bundle matches its sources
```

- `npm install` once. `npm test`'s corpus sweeps read the `graph.json` files `./gradlew test` writes,
  so run Gradle first.
- **JDK 25, everywhere.** codeflow parses with the running JDK's javac, and javac 21 has a silent
  `Attr.visitYield` bug on real input. `jvmToolchain(25)` covers build, test and `run`; start Gradle
  with `JAVA_HOME` = a 25 too. The installed CLI (`./gradlew installDist`) uses whatever `JAVA_HOME`
  names. → `docs/decisions/toolchain.md`
- `scripts/smoke.sh` is CI's real-corpus check (`.github/workflows/smoke.yml`, Fineract at a pinned SHA). It
  needs `./gradlew installDist` first. Unmodelled counts are reported, not failed on.
- `scripts/delombok.sh <src> [out]` preprocesses a Lombok corpus; codeflow parses with `-proc:none`
  and no classpath and never runs Lombok itself.
- The viewer bundle (`app/src/main/resources/viewer/reactflow.bundle.js`) is **committed**; Gradle
  never runs npm. Rebuild with `scripts/build-viewer.mjs` after any viewer source change.
  `package.json` has no `"type"` key on purpose.

## Pipeline

`AstReader.process`: javac parse + `analyze()` → `AstProcessor` (methods and classes by `Element`)
→ `ir.Lowering` (trees to instructions, **once per method**) → `ir.IrGraphBuilder` (instructions to
graph, **once per call site**, from the entry point) → an exporter (Mermaid default, `--graphml`,
`--json`, `--html`). Keep the Lowering / IrGraphBuilder split: what a name means is per method,
which box a value is drawn as is per call site. → `docs/decisions/pipeline.md`
`ir.Summarizer` reads the same instructions into a summary **once per method**, behind `--summaries`. → `docs/decisions/summaries.md`

## Rules

- **stdout is the document.** Every exporter writes there and nothing else may; logs and counters go
  to stderr (`logback.xml` pins its appender to `System.err`).
- **`Args` refuses unknown flags** rather than falling through to Mermaid.
- **Ask javac, never match strings.** A name is looked up by the `Element` it resolved to, and an
  `Element` is believed only when its kind is the kind expected at that site:
  `symbols.element(tree, ElementKind.METHOD)`. Wrong kind → `Opaque` / `EXTERNAL`.
  → `docs/decisions/attribution.md`
- **Drop what javac added, not what the author wrote** — `Symbols.isWrittenInSource`.
- **Dispatch on the object.** `Frame.resolve` asks the receiver's constructed type; several
  implementations or untyped objects → `EXTERNAL`, never a guess. `super.f()` does not dispatch.
  → `docs/decisions/calls-and-dispatch.md`
- **Two identities, never conflated.** `GraphNode.serial` = which box (per occurrence);
  `JNodeId` = which variable (per declaration × object). **Never derive a rendered id by hashing
  attributes.** A variable points at a *set* of `MemPos`. → `docs/decisions/identity-and-objects.md`
- **Joins are phis; `if`/`switch` phis are gated** by what chose, and the gate is not in
  `Phi.paths`. Edges carry an `EdgeKind` (`TRUE`, `FALSE`, `CONDITION`, `FLOW`, `SHARED`, `ORDER`, `EVENT`); colours are
  repeated in `MermaidExporter` and `theme.ts` and must match. → `docs/decisions/joins.md`
- **A local with no reaching definition fails loudly** at the read. A field or enum constant with
  no write is a value with nothing flowing in. Do not widen either gate.
- **Mermaid placement:** `MermaidExporter.placeEdges` writes each edge in the innermost block
  enclosing both endpoints, and `linkStyle` indexes are global — moving an edge renumbers the rest.
- **Viewer: `model.ts` decides, the renderer draws.** Every visibility decision is a pure function
  of `(payload, revealed)`; `revealed` is the only state. ELK options are **strings**. A parent node
  must precede its children. → `docs/decisions/viewer.md`
  The condensed view (`condensed.ts`) follows the same contract; a group is a boundary `method`
  read off the box, never a label. Positions and motion are renderer state (`stabilise`,
  `transition.ts`); `window.drawn` counts only once a click's move has ended.

## Adding a Java construct

1. Write the visitor in `Lowering`, emit an instruction with `ctx.location(tree)`.
2. Add its `Tree.Kind` to `MODELLED_EXPRESSIONS`. Never widen that set without a visitor behind it.
   Anything unlisted becomes an `UNMODELLED` node, is counted on stderr, and exits non-zero.
3. A new `Insn` needs a branch in `Frame.draw` (sealed `when`, so the compiler tells you).
4. A statement that **binds a name** needs a visitor even if it produces no value (emit a `Bind`
   with the right `Identity`).
5. Use `evaluate(tree, ctx)`, not `tree.accept(...)`, for anything that needs a value.
6. Operator labels go through `binaryOperatorLabel` / `unaryOperatorLabel` /
   `compoundAssignmentLabel` — raw `/`, `|`, `&` corrupt Mermaid.

→ `docs/decisions/constructs.md`

## Testing

- **Goldens** (`app/src/test/resources/<fixture>/snapshot.md`) certify *unchanged*, not *correct*.
  Written only when missing or under `UPDATE_SNAPSHOTS=1`.
- **New behaviour needs a behaviour test** (`edgeLabels` or `reaches()`), not just a regenerated
  snapshot. Confirm it fails against a *wrong* implementation, not just an absent one.
- **When snapshots move**, diff them structurally (sorted multisets of `label:TYPE` nodes and edges,
  ids stripped, old from `git show HEAD:<path>`) and explain every leftover.
- Suite-wide invariants run on every fixture (`AppTest`, `InvariantsTest`); `LoweringTest` asserts
  on instructions; viewer sweeps live in `app/src/test/js/unit/`.
- Each fixture also gets gitignored `ir.txt`, `graph.json` and `graph.html` beside it, for reading
  side by side. Nothing asserts on them.
- Browser tests go through the **DOM** and wait on `window.drawn`; pair every negative assertion
  with a positive one.

→ `docs/decisions/testing.md`

## Decision records

| Page | Covers |
|---|---|
| `docs/decisions/toolchain.md` | the two JDKs, delombok, bundle and `package.json` |
| `docs/decisions/pipeline.md` | the pipeline split, entry points, exporters, Mermaid placement |
| `docs/decisions/viewer.md` | model/renderer split, progressive reveal, badges, stubs, `openBoxes` |
| `docs/decisions/attribution.md` | javac attribution without a classpath, kind checks, `Symbols` |
| `docs/decisions/calls-and-dispatch.md` | per-call-site inlining, dispatch on the object, `--boundary` |
| `docs/decisions/identity-and-objects.md` | `serial` vs `JNodeId`, `MemPos` sets |
| `docs/decisions/joins.md` | SSA, gated phis, guarded returns, edge kinds, loops, try |
| `docs/decisions/constructs.md` | the `MODELLED_EXPRESSIONS` gate and why it is not a throw |
| `docs/decisions/testing.md` | goldens vs behaviour tests vs invariants, viewer test traps |
| `docs/decisions/summaries.md` | method summaries, atoms, gap kinds, parity with the builder |
| `docs/if-written-again.md` | what a rewrite would change, and how much of it has landed |
