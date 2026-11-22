# Review of the study: what held, what was wrong, where I lean (2026-11-22)

Back to the [main report](README.md).

**Answer.** The quotes and diffs in the reports hold. Two claims did not survive running the tool, and one run
changed what I would build first:
- **Casts did not hide Keycloak #49604.** The case is unchanged by modelling them. The wall is an object with no
  tracked origin, and the bug was readable in the gap rows the whole time.
- **The deconstruction pattern was not just "the gap left".** A body that read its names ended the run. Fixed
  in `5ab6ba3`.
- **`diverges` already works as a short script over `--summaries`** on two real bugs, so I would start there.

Written by the same rater that wrote the study, a few hours later, after re-running cases instead of judging them.

## What was checked, and what came out

| Claim in the reports | What a run showed | Status |
|---|---|---|
| `TYPE_CAST` hid Keycloak #49604 (improvement #1's evidence) | Same result before and after casts: 0 `SP → deployment` flows. See [below](#keycloak-49604-run-before-and-after-casts). | **Wrong** |
| Kafka #2 is visible "very nearly today" | Sink diff of the two overrides finds the forgotten write. See [below](#kafka-24128-run-on-the-pre-fix-sources). | **Confirmed** |
| Modelling casts clears Fineract's 22 unmodelled constructs | CI smoke on `9b32bfa`: 658 of 658 roots, none reported. See [below](#fineract-summaries-what-unmodelled-counts). | **Confirmed** |
| The record pattern is "the gap left", drawn as a node | `if (o instanceof Point(int x, int y)) { … x + y … }` exited 1 with a `GraphException` and no document. | **Wrong**, fixed |
| Quotes in [kafka-sibling-paths.md](kafka-sibling-paths.md) | All seven match GitHub (`gh api`). | Confirmed |
| Diffs in [never-sees.md](never-sees.md) | All ten match `gh api …/pulls/<n>/files`. | Confirmed |
| The other five Kafka "Today?" paragraphs | Not run. They are predictions from reading the code. | **Unverified** |
| The proposal's acceptance cases | Not run. | **Unverified** |
| The scores | One rater's judgement. Unchanged. | Unverified |

### Keycloak #49604, run before and after casts

`DeploymentBuilder#build` copies an `SP` config object into a deployment, one `deployment.setX(sp.isX())` per
field. The fix adds the one pair nobody wrote: `turnOffChangeSessionIdOnLogin`. Sources: the three pre-fix files
(`SP`, `DefaultSamlDeployment`, `DeploymentBuilder`), `--summaries`.

| | Before casts (`d449caa~1`) | After (`5ab6ba3`) |
|---|---|---|
| Exit | 1 (5 `TYPE_CAST`) | 0 |
| `build`: flows | 182 | 183 |
| `build`: `SP` field → deployment field flows | 0 | 0 |
| `build`: gaps | 20 `unmodelled` (5 casts, 15 no origin), 15 `depth` | 15 `unmodelled` (all no origin), 15 `depth` |
| `SP` fields the gap rows name as read | 13 of 14 | 13 of 14 |

- **The wall.** `SP sp = adapter.getSps().get(0);`. The object comes out of a parser outside the corpus and a
  `List.get`, so it has no tracked origin. Each `sp.isX()` resolves, and its field read becomes a gap:
  `SP.forceAuthentication read on an object with no tracked origin`. The value is dropped.
- **What I missed.** Those gap rows name every `SP` field `build` reads. `SP` declares 14 fields, the rows name
  13, and the missing one is `turnOffChangeSessionIdOnLogin`: the bug. Ten lines of Python over today's output
  find it. In the trial I looked at the flows, saw nothing, and blamed the first error on stderr.

### Kafka #24128, run on the pre-fix sources

`ConsumerGroup`, `ShareGroup` and `ModernGroup` at `8e75faf`, `--summaries`: exit 0, 132 methods, 2,318 pairs,
5 gaps. Comparing the `ext-arg` sinks of the two `getOrMaybeCreateMember` overrides:

| | Sinks only this override has |
|---|---|
| `ShareGroup` | `put`, `set`, `compute`, and six more (`size`, `isEmpty`, `values`, `forEach`, `memberId`, `byNameCount`) |
| `ConsumerGroup` | none |

- The forgotten-sibling shape is there: one override mutates, the other does not.
- **Field writes are empty for both.** The write is `ext-arg:put`, because `TimelineHashMap` is outside the
  corpus. The query has to count an opaque mutator on a `this.<field>` receiver as a write, as the proposal says.
- **Signal is 3 of 9.** A raw sink diff needs a mutator vocabulary or it buries the `put`.
- `ShareGroup`'s side has one `dispatch` gap (`maybeUpdateGroupSubscriptionType on this`), so "absent in
  `ConsumerGroup`" is safe here but "absent in `ShareGroup`" would not be.

### Fineract summaries: what `unmodelled` counts

CI smoke, before (`e2945d3`) and after casts (`9b32bfa`):

| | Before | After |
|---|---|---|
| Unmodelled constructs (graph run) | 22, all `TYPE_CAST` | none |
| Pairs | 859,155 | 860,619 |
| Gap rows | 35,935 | 35,483 |
| of which `unmodelled` | 19,194 | 18,742 |

Every other gap kind is unchanged. So none of the remaining 18,742 rows is an unmodelled construct. They are
field reads and writes through an object the summary cannot name, and they are 53% of the corpus's gap rows.

## What I learned

- **A blocker is proven by removing it and re-running, not by reading stderr.** I ranked casts first because a
  cast was the first error printed on the case. Removing it changed one exit code and no flow.
- **Removing one wall shows the next.** The real wall, objects with no tracked origin, is also the largest by
  count: 18,742 rows in Fineract, 15 of 30 gaps in the SAML trial.
- **Gap rows are evidence, not only apologies.** A row saying "field F read on an object I cannot name" still
  says F is read, where, and by whom. Both real bugs found in this review came from rows the study skipped.
- **"Sibling" is wider than paths.** #49604 is a sibling case too: 13 fields of one class are read by one
  method, and one is not. The majority vote that finds a forgotten handler finds a forgotten field.
- **A fixture for a construct that binds names has to read them.** The `unsupported` fixture bound `x` and
  never read it, so it could not show that reading `x` ended the run.
- **Choose the assertion by trying the wrong implementations.** Binding the pattern's names to nothing, or
  straight to the tested value, both pass the summary's pair assertion, because the `instanceof` gate already
  carries `P0 > RET`. Only the graph's edge assertion tells the three apart.
- **Casts were still worth it,** for a different reason than the one given. Fineract's smoke reports no
  unmodelled construct any more (22 before) and its summaries gained 1,464 pairs: flows through a cast used to
  stop at a node.

## Where I lean

In order. Each is a leaning with its evidence, not a decision.

1. **Build `diverges` first, as a script over `--summaries` JSON.** Two real bugs came out of today's output
   with under 20 lines each: Kafka #24128 (`overrides:`) and Keycloak #49604 (a new family, `fields:`). That is
   two measured hits against zero for the PR check, which the main report put first on judged evidence. Run it
   on the other five Kafka cases before writing any engine.
2. **Treat a read through an unnamed object as a read fact.** Smallest step: give it its own gap kind
   (`origin`) so `unmodelled` means a construct again and the numbers can be read. Larger step: keep the value,
   as an atom naming the origin and the field, so `sp.isX()` flows into `setX`. That would turn the largest gap
   kind into pairs. It needs a design in `docs/decisions/summaries.md` first: two calls that return the same
   object would be two atoms, and that has to be stated, not guessed. Cost unknown.
3. **Then the PR check** (blastRadius, roots derived from the diff), with Fineract #6846 run as its first
   acceptance case before any code.
4. **Model the record pattern properly, later.** A component read is a field read on the record. Small, now
   that the names bind. Low priority: Fineract's smoke has none.
5. **Rank by run from now on.** Nothing enters the improvements table without a before/after run on the case it
   cites.

## What I would do differently

- **Run the tool on a case before scoring it or ranking by it.** Four runs in the study, three more in the
  review, and the three overturned more than the rest of the review did.
- **When a run shows nothing, read the gap rows before calling it a miss.**
- **Check subagent output before committing it,** not after pushing. It all held this time; that was luck in
  the order of events, not a method.
- **Run the smoke locally before pushing.** CI passed, but it was the first time that corpus saw the change.
- **Mark predictions as predictions in the text.** The "Today?" paragraphs read like results.

## Reproduce

```shell
# JDK 25. <dir> holds the pre-fix .java files of one case.
./gradlew -q run --args="<dir> --summaries" > out.json
```

- **Kafka #24128:** `ConsumerGroup.java`, `ShareGroup.java`, `ModernGroup.java` at `8e75faf`. Diff the
  `ext-arg:` targets of the two `getOrMaybeCreateMember` entries.
- **Keycloak #49604:** `SP.java`, `DefaultSamlDeployment.java`, `DeploymentBuilder.java` at the parent of the
  fix. Take `DeploymentBuilder#build`'s gaps, keep `SP.<field> read on …`, subtract from `SP`'s declared fields.
