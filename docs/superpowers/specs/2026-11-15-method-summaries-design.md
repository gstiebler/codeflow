# Plan B: method summaries, then each body drawn once

Status: **approved** (2026-11-15; the user approved it without a review round). Phase 1 is planned in
`docs/superpowers/plans/2026-11-15-method-summaries-phase-1.md`. Phases 2 and 3 get a plan each once
Phase 1 has landed and its numbers are in.

## Why

Today every call site re-walks its callee (`Frame.call` → `enter`). The cost grows with call sites,
not with source size. On Fineract progressive-loan (658 roots) that is 2.24M nodes, 4.49M edges,
742 MB of JSON and about 63 s. Three things follow from that:

- **A root is expensive**, so "every public method is a root" (`if-written-again.md` §7) can't
  become the default. The product needs it: the agent queries (`reaches`, `slice_back`,
  `slice_fwd`) and the PR boundary diff (`blastRadius`) need a whole-module index, and they come
  before the visuals (Obsidian topic page, "Decisions").
- **Recursion is cut.** The recursive call is drawn as `EXTERNAL`, so what it returns isn't
  modelled.
- **The documents are huge.** The progressive-reveal viewer exists to make that survivable.

Evidence collected before this design (2026-11-15):

- **Summaries can do the analysis.** The throwaway spike (branch `spike-summaries` @ `6a7d33f`)
  computed summaries bottom-up and graded them against the inlined graph as the answer key.
  - 369 methods, 1111 input→output pairs.
  - Recall 99.4%, precision 97.1%.
  - 45 ms against 22.5 s.
  - Its misses went through lambdas and method references. Dispatch on the object was barely
    exercised by that corpus.
- **Drawing each body once is about 116× smaller.** The 677 distinct methods are drawn today as
  91,690 blocks.
  - Bodies drawn once: 14,266 leaf nodes against 1,659,983.
  - Edges inside bodies: 13,893 against 1,685,601.
  - Today 345,234 edges cross between blocks.
- **Recursion cuts are rare on Fineract**, so a fixpoint there is cheap. On other code it is the
  only correct answer.

## What it is

Two stages: a per-method analysis, then a document built from it.

1. **Summaries (the analysis).**
   - Every method in the sources is analysed into a `Summary` that says which of its inputs reach
     which of its outputs.
   - At a call site, summaries are composed instead of inlined.
   - Recursion and loops become a fixpoint over summaries.
   - Every method is a root at no extra cost.
2. **Bodies drawn once (the document).**
   - Each method body is drawn as one block.
   - A call site is a box whose argument→result and argument→field edges come from the callee's
     summary, plus a link to the callee's block.
   - Inlining stops being what the builder did a thousand times in advance. It becomes what "open
     this call" means in the viewer: a summary instantiated on demand.

## Goals

- A per-method summary for every method in the sources, in one run, in seconds on Fineract.
- Precision equal to per-call-site inlining for what inlining already models. `f(a)` and `f(b)`
  stay separate because a summary is instantiated per call site, not drawn per call site.
- **No silently missing flow.** Wherever a summary is incomplete, it carries a gap that names its
  kind and source position ("complete except through …"). This is the project rule
  ("a silently wrong graph is worse than a failure") applied to summaries.
- Recursion produces modelled flows instead of an `EXTERNAL` cut.
- Machine-readable output that the query and diff tooling can read: `--summaries`.

## Non-goals (for now)

- The agent query surface itself (MCP tools, `reaches`/`slice` commands) and the `blastRadius`
  script. Phase 1 produces what they read, not the tools.
- An array element model, collections, and lambdas invoked by code in the sources (a lambda is a
  value. What runs it is outside the sources, the same as today).
- Replacing `--json`, `--html` or Mermaid in Phase 1. They stay byte-identical until Phase 2 adds a
  mode next to them.

## Anti-goals: failure even if it "works"

- **A summary that drops a flow without a gap.** The spike's 97–99% is fine only if the missing
  2–3% is named on the output.
- **A second interpreter that drifts from the builder.** The spike repeated the builder's
  last-write-wins field bug, which the builder has since fixed. Parity with the builder is a test,
  not a hope.
- **Golden churn in Phase 1.** No existing golden may move. Phase 1 adds; it does not change any
  existing output.

## Approaches considered

| | Approach | Trade-off | Verdict |
|---|---|---|---|
| A | **Summaries as the analysis, plus each body drawn once** | Two interpreters over one IR until Phase 3 retires per-call-site inlining. The parity test is the guard. | **Picked**, by the user's direction and the measurements |
| B | Summaries as an index only; inlining stays the document | Cheap. Queries work, but the 742 MB documents and recursion cuts stay. | Rejected: leaves half of the evidence on the table |
| C | Bodies drawn once with lazy inlining, no summaries | Document 116× smaller and exactly today's precision, but no whole-module query index, and a body drawn once without summaries can't connect `f(a)` and `f(b)` precisely. | Rejected: does nothing for queries or the diff, which come first |

## Design

### The model (`ir/Summary.kt`)

An abstract value is a set of **atoms** that flow into it plus a set of **abstract objects** it
can be:

- **Atoms** (inputs):
  - `Param(i)`
  - `Field(path, field)`, where `path` is rooted at `this`, `Param(i)` or a static class, with a
    field depth cap of 2. This is the spike's `PathObj`.
  - `ExtResult(site)`: the value returned by an opaque call at a source position. The position is
    absolute, so it means the same thing in every caller.
- **Objects**:
  - `Path(root, fields)`: an object reached through the method's interface.
  - `Alloc(site)`: an object allocated inside the method or a callee. Its name is the chain of call
    sites that led to the allocation, bounded so a fixpoint terminates: past 2 sites it keeps the
    outermost call site and the allocation site, and drops the ones between.
- **A summary** has:
  - `ret`: an abstract value.
  - `effects`: the final value of every field written on a `Path` object or on a kept `Alloc`
    object.
  - `sinks`: the `ExtArg(site)` outputs, meaning what flows into each opaque call's arguments and
    receiver.
  - `gaps`.

A pair of a summary is `input atom → output`. The outputs are `RET`, a field written on a path, or
`ExtArg(site)`. Field names are written as the declaring class's qualified name plus the field name,
taken from the `Element`, never from text.

### The interpreter (`ir/Summarizer.kt`)

`Summarizer` walks the same `MethodBody.instructions` that `Lowering` produces and the builder
consumes. It reuses `IrGraphBuilder.bodyOf` / `initializersOf` caching, or a shared lowering cache
lifted out of the builder (the builder's call). Its rules mirror the builder's: whatever edge the
builder draws, the summarizer treats as a flow.

- **Values.**
  - `Const` brings nothing in.
  - `BinOp`, `UnOp`, `Opaque` and `Unmodelled` carry their inputs.
  - `Select` carries its inputs as flows and its `alternatives` as objects.
  - `Phi` is the union of its paths, plus the gate's flows (the builder draws `CONDITION`).
  - `Bind` follows its `Identity`.
- **Fields at joins: the same `FieldFork` / `FieldArm` / `FieldJoin` semantics as the builder**
  (`docs/decisions/joins.md`, "Fields join too"). A write journals; an arm undoes; the join merges
  every arm, including those that return or throw.
  - The builder's known gaps carry over as they are: no loop back edge for fields, `?:`/`&&`/`||`
    not forked, strong update through several holders.
  - The summarizer does not quietly do better or worse than the builder. Divergence shows up in the
    parity test.
- **Calls.** These are resolved by the builder's own dispatch rule, extracted into one shared
  function so the two interpreters can't drift (`ir/Dispatch.kt`: `isVirtual`, `resolve`).
  - **Resolved to a source method:** instantiate the callee's summary.
    - Map `Param(i)` to the argument's value, varargs collapsing as the builder does, and `this` to
      the receiver's objects.
    - Map `Path` objects through the caller's abstract heap and rename `Alloc`s by call site.
    - Apply `effects` to the caller's heap: a strong update for one callee, a weak one for several.
  - **Unresolved, or resolved outside the sources** (the builder's `EXTERNAL`):
    - The result is `ExtResult(site)` plus the receiver's and arguments' flows. This is exactly the
      reachability the builder's `EXTERNAL` node gives.
    - Record `ExtArg(site)` ← receiver and argument flows.
  - **Dispatch that the summary can't decide.** A virtual call on a `Path` receiver (a parameter's
    or `this`'s object, whose class the caller knows and the summary doesn't), where the sources
    override the method:
    - **`this` receivers are specialised.** The summary is keyed by `(method, receiver class)` and
      computed lazily for each concrete receiver class a caller instantiates it with. A root or an
      unknown receiver uses `null`, meaning static resolution, which is what the builder does for an
      untyped receiver.
    - **Parameter receivers are not specialised in Phase 1.** The call becomes an ext-style opaque
      call with a `dispatch` gap at its position. That is loud, not a guess.
- **`New` / `Delegate` / initializers**, the same as the builder:
  - A constructor that doesn't delegate to `this(...)` runs the class initializers first.
  - `new` of a class with no constructor runs the initializers on the new `Alloc`.
  - `super(...)` / `this(...)` run on the current `this`.
- **Fixpoint.**
  - Summaries are solved by a worklist with dependents. When a summary changes, every summary that
    instantiated it is queued again.
  - Recursion needs no cut: a callee still in progress contributes its current approximation, which
    starts empty, and the worklist reaches the fixpoint.
  - Loop bodies with back edges iterate until stable, as in the spike.
  - Both have an iteration budget. Running out of budget records a `fixpoint` gap and keeps the last
    approximation. It is never silent.
- **Gaps** (each with a kind, a `file:line:col` and a detail):
  - `dispatch`: the case above, or several implementations.
  - `fixpoint`: the budget ran out.
  - `depth`: a field path was cut at the cap, so reads deeper than 2 are not distinguished.
  - `unmodelled`: an `Unmodelled` instruction.
  - `body`: lowering threw for this method. Its callers see an opaque call plus this gap.
  - `alias`: two distinct callee roots or paths (`P<i>`, `P<j>`, `this.a`, `this.b`) are one
    object in the caller, and the callee reads a field through one and writes it through the
    other, or writes one field through both. The summary was computed as if they were distinct,
    so the instantiated flow would be wrong. Context-insensitive by design, so it may over-report:
    noise, not silence.
  - A caller copies its callees' gaps, each keeping the callee's own position, so the output says
    "complete except through …" for every root.

### Output (`--summaries`)

`--summaries` is a new format flag next to `--json`, `--html` and `--graphml`. It writes one JSON
document to stdout. `Args` still refuses unknown flags.

```json
{"format": "codeflow-summaries", "version": 1,
 "methods": [
   {"method": "pkg.Loan#interest(java.math.BigDecimal,int)", "at": "pkg/Loan.java:40:5",
    "flows": [["P0", "RET"], ["this.pkg.Loan.rate", "RET"], ["P1", "ext-arg:save@pkg/Repo.java:12:9"]],
    "gaps":  [{"kind": "dispatch", "at": "pkg/Loan.java:44:16", "detail": "Shape#area on P1"}]}
 ],
 "counts": {"methods": 677, "pairs": 0, "gaps": {"dispatch": 0}}}
```

- **Every method in the sources is listed**, sorted by `method`, with pairs sorted, so the
  document is byte-stable and diffs are clean.
  - `--from` narrows the list to the methods named. It does not change what is analysed.
  - Counters go to stderr. `counts` and the stderr totals count emitted rows, so an inherited gap
    counts once for every caller that lists it.
- **Atom spellings:**
  - `P<i>`
  - `this.<qualified field>[.<qualified field>]`
  - `P<i>.<qualified field>…`
  - `static.<qualified field>`
  - `ext:<label>@<file:line:col>`
  - Outputs add `RET`, the same field spellings, and `ext-arg:<label>@<file:line:col>`.
  - An array's length is a field of the array, spelled `P<i>.Array.length`.

### Tests

- **Behaviour (`SummarizerTest`).** Small Java sources, asserting on pairs and gaps. Each one has to
  be confirmed failing against a wrong implementation, per `testing.md`. They cover:
  - params to return
  - field write-then-read
  - field joins in `if`, `switch`, `try` and loops
  - effects through a parameter seen by the caller
  - factory allocation
  - `ExtResult`/`ExtArg`
  - recursion converging
  - specialised dispatch on `this` (`templateMethod`)
  - the `dispatch` gap on a parameter receiver
  - varargs
  - initializers and `super(...)`
- **Parity (`SummaryParityTest`).** This is the spike's oracle made a test.
  - For every lowerable fixture, every non-constructor method with a return value is built as an
    inlined root (`IrGraphBuilder.build`, statics forgotten between roots, as `build(roots)` does).
  - Walking ids, it collects `P<i> → RET` reachability from the root block's parameter nodes to its
    return node.
  - Summaries must find every such pair, or the pair is in an explicit, commented
    known-differences list. Extra pairs must be in the list as well.
  - Expected entries: recursion, where a summary is more precise than the builder's `EXTERNAL` cut,
    and the `dispatch` gap.
  - The list is the record of where the two interpreters disagree.
- **Smoke.** `scripts/smoke.sh` also runs `--summaries` on Fineract and reports the time, size,
  pair count and gap counts. It fails on a crash or a non-JSON document. Gaps are reported, not
  failed on, matching the unmodelled counter.
- **No existing golden or test changes.**

### Phase 2: bodies drawn once (design level; planned after Phase 1)

- **A new document mode** (`--bodies`, JSON and HTML).
  - The builder runs each method once, with params as input nodes that have nothing flowing in.
  - It runs with an `Interface` heap: one `MemPos` per parameter and one for `this`, typed `null`,
    so dispatch on them follows the existing untyped rule.
- **A call doesn't `enter`.** It draws a `CALL` node:
  - Argument and receiver edges come in, and the result goes out.
  - The `arg → result` edges come from the callee's summary, as a new `EdgeKind.SUMMARY`.
  - Each field the summary writes on a caller-visible object becomes a field node filed in the
    caller's heap (`record`), with summary edges from the flows into it. A later read in the caller
    connects to it as it does today.
  - The JSON gets `calls: [{site, callee, specialisation}]` linking call boxes to callee blocks.
- **Precision is the summary's**, not a context-insensitive param↔arg wiring. `f(a)` and `f(b)`
  never meet inside `f`'s block.
- **Moving goldens:** none. `--bodies` is a new mode with fixtures and goldens of its own.

### Phase 3: viewer and defaults (design level)

- **The viewer opens a call by instantiating the callee body inline.** This is the current
  rendering, computed on demand from the `--bodies` payload. The `neighbourhood` / revealed-set
  model stays.
- Once `--bodies` plus on-demand inlining covers the browser tests, it becomes the default for
  `--html` / `--json`. Per-call-site inlining stays only as the test oracle (`SummaryParityTest`)
  and for Mermaid goldens.
- The every-public-method default for roots (§7) becomes the default here.

## Calls left to the builder

These are marked as the builder's call. A sensible builder settles each without changing what gets
built:

- Whether the lowering cache moves out of `IrGraphBuilder` or the summarizer borrows it.
- The bound on `Alloc` call-site chains: k-limit 2 (built: outermost call site and allocation site).
- The iteration budgets.
- The exact atom spellings for nested classes. Use `Element`-derived binary names consistently.
- Gap de-duplication and how callee gaps are referenced in the output, as long as every root's gaps
  are listed or reachable from it.
- Where `--summaries` writing lives: `SummariesExporter.kt` next to the others.

## Order

1. Phase 1: shared dispatch → model and intra-method interpreter → calls and composition → fixpoint
   → `--summaries` → parity test → smoke → docs (`docs/decisions/summaries.md`, CLAUDE.md row,
   `if-written-again.md` §2 status).
2. Measure on Fineract: time, pairs, gaps, and the parity on its roots, run once by hand.
3. Phase 2 plan, then Phase 3.

## Changes during implementation

The design above has been edited in place to match what was built. What changed:

1. **`alias` gap kind.** Added (see Gaps). Summaries are context-insensitive, so two callee roots or
   paths that are one object in the caller cannot be instantiated exactly; that is a gap, not a
   guess.
2. **The k-limit keeps [outermost call site, allocation site].** Keeping the 2 newest lost the
   allocation site and merged distinct objects. A `depth` gap is recorded only when the truncation
   actually merges two distinct chains.
3. **Callee gaps are copied into the caller**, each keeping the callee's position, replacing "as
   references, not copies". Copies are what the exporter prints.
4. **Array length is spelled `P<i>.Array.length`**, which the atom list did not have.
5. **Counts are per emitted row**, so an inherited gap counts once per caller.

The reasoning and the Fineract numbers are in `docs/decisions/summaries.md`.
