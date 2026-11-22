# Study rubric: where would codeflow help?

## What codeflow is (be accurate; do not over-credit it)

EXISTS today (Java only):
- Parses Java with javac (attributed, no classpath needed), lowers each method to instructions, then builds a
  **dataflow graph from a root method**: which value reaches which, through which operators, calls, fields.
  Calls are inlined per call site; virtual calls dispatch on the receiver's constructed type; ambiguous → EXTERNAL.
- Joins are phis; if/switch phis are **gated by the condition** (so "which condition chose this value" is visible).
- Heap edges: a field write → field read flows even without a call edge between them.
- `--boundary Class`: treat a class as an opaque I/O boundary (params → call → return).
- `--summaries`: per-method summary (which param reaches return / which field / which boundary call).
- Interactive HTML viewer: opens on the entry method's inputs/returns, click to expand.
- Fails loudly on unmodelled constructs (never silently drops).

PLANNED (credit as "planned", separately):
- **blastRadius / boundary diff**: run before and after a PR, set-diff the graphs → rows NEW / GONE / PATH-changed
  ("value X no longer reaches DB column Y", "new exception escapes to REST endpoint Z").
- Typed I/O boundaries per repo (net:in REST, db:in/out repository, net:out events), exceptions as outputs.
- Agent queries: slice_back(output), slice_fwd(input), reaches(a,b) → bool + path, diverges (two paths that
  should follow the same rule but don't), with "complete except through …" frontier.
- SCC collapse (fixed-point loops shown as one unit).

DOES NOT and will not see: concurrency/locking/races, SQL text/DDL/schema/migrations, config/YAML/XML/properties,
build files/deps, reflection/DI wiring beyond what types say, runtime/env/timing, UI/CSS, string semantics,
performance (N+1 queries), external services' behaviour, non-Java code.

## Dimensions (score each item on each)
- **D1 Understand** – would the graph help someone new understand the code this item touches?
- **D2a Own PR** – would running it before opening the PR have caught the problem / a reviewer comment?
- **D2b Review others** – would it have let a reviewer raise a real issue (or a missed one) on this PR?
- **D3 Solve bug** – for a bug (issue or fix PR): would it shorten localization/root-cause?
- **D4 Prevent** – would a CI check built on it (blastRadius gate, reaches-assertions) have blocked the bug
  from being introduced?

Score: **2 = decisive** (the key fact is a dataflow fact codeflow surfaces directly), **1 = helps** (useful
context, not the key), **0 = none**, **n/a**. Mark whether it needs EXISTING or PLANNED capability.
Be skeptical: a null-check fix where the bug is "value can be null" is usually 1 at most; config/SQL/
concurrency/docs/deps/tests-only items are 0. Hindsight bias is the main risk: ask "would someone who did not
know the answer have been pointed at it?".

## Item categories
PR kind: bugfix | feature | refactor | perf | security | test-only | docs | build/CI/deps | config | other
Bug root cause (bugs only): wrong-value/arg (wrong var, wrong formula, wrong field) | missing-propagation
(value not passed/copied/persisted) | condition/logic | null/missing-guard | state/persistence mismatch
(writer vs reader, entity vs column) | exception-handling | api-contract/serialization | concurrency |
sql/schema/migration | config/wiring | dependency/build | ui | other
Review comment kind: correctness/logic | missing-case/edge | data-propagation | test-request | design/architecture
| naming/style | perf | security | docs | question/clarification | other

## Output (write both files to your repo folder)
1. `items.tsv` – one row per PR/issue: id, url, kind, root_cause, files_changed, D1, D2a, D2b, D3, D4,
   capability(EXISTING/PLANNED/-), one-line reason.
2. `comments.tsv` – one row per substantive review comment sampled: pr, comment_kind, codeflow_could_surface(0/1/2), reason.
3. `report.md` – ≤ 1,200 words: sample method (how picked, dates, n), census counts, score distribution per
   dimension, 5–8 concrete best cases with links and the exact dataflow fact, 3–5 cases where it looked
   useful but is not (and why), surprises, and what capability would most raise the scores in this repo.
