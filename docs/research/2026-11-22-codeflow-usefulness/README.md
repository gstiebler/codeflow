# Where codeflow helps: Fineract, Keycloak, Kafka, Odoo (2026-11-22)

**Answer.** Today codeflow is decisive on one bug in five in Fineract, but only one in 30–50 in Keycloak and
Kafka. Fineract's decisive cases are in its domain code.
Most of its value is planned, not built, and it comes from two queries:
1. **Before/after diff of a PR (blastRadius)**: refactors that silently change a path are the most common
   way bugs get introduced, in all four repos.
2. **diverges**: "path A does X, its sibling path B forgets to". Six verified Kafka cases with code:
   [kafka-sibling-paths.md](kafka-sibling-paths.md).

**Proposal:** build both on one per-root effect summary, so the PR check, `diverges` and agent queries are three
comparisons of the same list (see [Proposal](#proposal-the-change-that-adds-the-most-value)).

A Python port is worth building only as an Odoo-specific tool, and not before the Java tool covers its own gaps.

All scores are one model rater's judgement against [RUBRIC.md](RUBRIC.md). Codeflow was actually run only on
4 Keycloak fixes. Treat the numbers as rough proportions; they are not measurements.

**Reviewed the same day: [review.md](review.md).** It re-ran three cases. One claim here was wrong (casts did
not hide Keycloak #49604), and the order to build in changed. Corrections are marked in place below.

**Built since: [`scripts/diverges.py`](../../../scripts/diverges.py).** Run on the six Kafka sibling cases: 2 found,
1 found in part, 1 split visible, 2 missed. Per case: [kafka-sibling-paths.md](kafka-sibling-paths.md#measured-scriptsdivergespy-on-the-six-cases).

**Fixed since: the bug those runs found in the tool** (`d66e869`). A call chained on a type outside the corpus
dropped its receiver with no gap counted. Re-run: 3 found, 1 split visible, 2 missed.
[What moved](review.md#fixed-in-d66e869-and-what-moved).

**Reviewed again, by running 12 of the scored cases: [review-2.md](review-2.md).** Not yet applied below:
- The runs found the same bug in the tool (fixed in `d66e869`, above): 120 of 1,604 call sites in one Fineract
  file lost their receiver chain; after the fix, 0 of 1,814.
- Fineract's decisive bugs are 6 of 44 (one in seven), not 9 of 44, all six on today's tool.
- Neither D4=2 score survives a run.
- Improvement 4 (single implementation) has to come before the PR check, and `stem:` leaves the acceptance list.

## Sample

| Repo | Kind | Census | Deep-dived items | Review comments |
|---|---|---|---|---|
| apache/fineract | core banking, Java | last 100 merged PRs | 36 PRs + 26 JIRA bugs (62) | 48 |
| keycloak/keycloak | identity server, Java | last 100 merged PRs | 36 PRs + 32 bug issues (68) | 44 |
| apache/kafka | infrastructure, Java (+Scala) | last 100 merged PRs | 31 PRs + 22 JIRA bugs (53) | 54 |
| odoo/odoo | ERP, Python, *hypothetical port* | last 100 closed PRs | 29 PRs + 15 issues (44) | 34 |

Each item was scored per dimension: 2 = decisive, 1 = helps, 0 = none. Per-repo data: `<repo>/items.tsv`,
`<repo>/comments.tsv`, and `report.md` for Fineract and Keycloak.

## Scores: share of items scoring 2 (decisive) / 1 (helps)

| Dimension | Fineract | Keycloak | Kafka | Odoo (hyp.) |
|---|---|---|---|---|
| D1 Understand a codebase | 0% / 45% | 0% / 61% | 0% / 55% | 9% / 43% |
| D2a Check own PR | 3% / 36% | 0% / 0% | 0% / 15% | 0% / 10% |
| D2b Review others' PRs | 8% / 47% | 0% / 17% | 4% / 32% | 3% / 24% |
| D3 Solve a bug | **20%** / 39% | 3% / 59% | 2% / 49% | 8% / 56% |
| D4 Prevent a bug (CI gate) | 5% / 34% | 0% / 38% | 0% / 17% | 0% / 18% |
| Review comments surfaced | 6% / 31% | 0% / 25% | 6% / 37% | 6% / 35% |

How much of the value needs planned capability: Fineract 23 items need planned vs 15 existing, Keycloak 28 vs 10,
Kafka 19 vs 12. In Kafka, every non-zero D4 score needs planned capability.

## By your dimensions

### Understanding a new codebase: helps, almost never decisive
- It rarely scores 0. But nobody new gets the key fact from it, because understanding starts with structure:
  which service calls which, and how DI wires them. That is exactly where today's graph goes `EXTERNAL`:
  - Fineract REST → service → domain chains go through about 1,928 interface-typed injected service fields.
  - Keycloak uses SPIs (`session.users()`, `RealmModel`).
- Inside one class, it reads well: Fineract domain entities and schedule generators, a Keycloak provider class.

### Diffs, own PR: about zero
- Authors already know their own flow; Keycloak scores 0 on all 36 PRs.
- The one real use is a **purity check on refactors**: "I split this service, did anything start or stop
  flowing?". Fineract #6846 was a "pure split" that added a `saveAll` of installments. The reviewer caught it
  by hand ("not a refactor anymore"). A boundary diff shows it as a NEW db write.

### Diffs, reviewing others' PRs: useful on refactors and sibling paths only
- Fineract's census: 52 of 100 PRs are refactor series (JsonCommand removal, QueryDSL migration, service splits,
  module moves). Reviewers do impact analysis by hand on nearly every split or move. That is the strongest
  product signal in the study.
- Kafka #24108: the reviewer found KAFKA-21458 by comparing `classicGroupHeartbeatToConsumerGroup` with
  `consumerGroupHeartbeat`. That is a diverges query; it and five more are worked through, with code, in
  [kafka-sibling-paths.md](kafka-sibling-paths.md).
- Kafka #24223: a partition id comes from `toString().hashCode()`, so a cosmetic `toString` change remaps
  partitions. Today's graph shows this.
- Only **6%** of human review comments are dataflow facts codeflow surfaces directly. About 65% score 0: SQL
  equivalence (the biggest review workload in Fineract's QueryDSL migration), Spring Security, DI wiring,
  tests, naming, backport policy, concurrency.

### Solving bugs: the best dimension, strongest in business-logic Java
Decisive cases:
- **FINERACT-2917 / #6627**: `findById(request.getEntityId())` gets the group id, not the meeting id, and the
  attendees land in a local copy that never reaches the meeting field.
- **FINERACT-3197 / #6834**: `gson.toJson(errorInfo)` passes a String, so the `instanceof ErrorInfo` branch
  is dead and status never reaches the webhook.
- **KAFKA-21324 / #23916**: the builder's `timeFrom` flows into the field `timeTo`.
- **Keycloak #53471 → #54268**: the userinfo `email_verified` overwrites the ID token's value in a gated phi
  chain. Verified with today's `--json`.
- **Keycloak #53633 → #55027**: `policyCheck`'s `policy` parameter reaches nothing. Verified with today's
  `--summaries`.

Weak cases:
- Odoo and Kafka issues often arrive pre-diagnosed, with a "Cause:" section or a stack trace, so the tool
  adds little.
- Null guards and condition tweaks were confirmed weak again.

### Preventing bugs: only through planned features, and the target is refactors
- **Regressions introduced by refactors** show up everywhere:
  - Fineract: 2917 ← 2548, 3197 ← 1744, 3012 ← the LocalDate migration, 2944 ← 2169.
  - Kafka: KAFKA-21033, where a helper extraction moved a backoff `sleep` into the per-topic loop.
  - Odoo: 9 of 38 bugs cite the commit that introduced them.
- A blastRadius gate fits these: "this refactor PR has NEW/GONE boundary rows". Because inlining is per call
  site, a helper extraction leaves the graph unchanged, so whatever does move stands out.
- **Invariant checks** are the other shape. Kafka's "group state changes only through record replay" is a
  heap-write reachability property (#24128).

## What it never sees
These are consistent across all repos, and are out of scope for codeflow, not gaps to fix. A code example for
each: [never-sees.md](never-sees.md).
- concurrency, races and transactions;
- SQL text and DDL;
- config and security DSLs;
- error-code policy;
- iteration-order semantics (Kafka #24269);
- string semantics (Keycloak's dotless ı);
- TypeScript, QWeb, Scala;
- field metadata in Odoo.

Kafka is the contrast case: 62% of its bugs are not dataflow-shaped. The predicted "config never reaches its
reader" bugs barely exist there.

## Other language: Python (Odoo)
- **Generic Python port: about 24% of the value survives.** A pyright-level resolver sees `BaseModel` and
  descriptors, so the flow gets lost in:
  - `_inherit`/`super()` chains whose order depends on which addons are installed;
  - string-keyed dicts (vals, `**values`, `env.context`);
  - ORM triggers with no call site (`@api.depends`, inverse, onchange);
  - domains and SQL on the read side.
- **Odoo-specific port: an estimated 60–70%.** It needs a static model registry for a chosen addon set, which
  plays the role javac plays for Java. Then:
  - recordsets and fields as heap;
  - constant-key dicts as records;
  - ORM trigger edges;
  - domains and create/write as typed db boundaries.
- `self.env['model']` is not the problem: 206 of 209 lookups use a literal name.
- **Verdict: not now.** The decisive Odoo cases rely on the same two queries (diff, diverges) that Java still
  lacks. Build those on Java first. A port's value would then come from the registry work alone.
- Untested hypothesis: TypeScript has a real type checker, so it would be a closer fit to the "ask the
  compiler" rule than Python.

## Suggested improvements, ranked by value ÷ cost

| # | Change | Why (evidence) | Cost |
|---|---|---|---|
| 1 | ~~**Model `TYPE_CAST`**~~ **done 2026-11-22**: a reference cast is the same object, a primitive cast is a `to int` operator (`docs/decisions/constructs.md`). Not narrowed into dispatch: `Frame.resolve` asks the constructed type, and a cast is only the author's claim. | Was missing from `MODELLED_EXPRESSIONS`: any method with a cast exited non-zero (22 in Fineract's smoke; OIDCIdentityProvider has 18). **Corrected:** it did not hide Keycloak #49604. A before/after run shows no flow changed; see row 13 and [review.md](review.md#keycloak-49604-run-before-and-after-casts). | small |
| 2 | **Ship blastRadius as a PR check, with a "refactor purity" mode**: NEW/GONE boundary rows on a PR that claims to be a refactor | The #1 way bugs are introduced in all 4 repos. Fineract #6846, FINERACT-2917/3197, KAFKA-21033. Covers D2a, D2b and D4 at once. | medium (already designed) |
| 3 | **diverges / per-path effect comparison**: which fields, boundary calls and exceptions handler A writes that its sibling B doesn't | Kafka: 7 bugs and about 6 comments. Keycloak: 4 best cases. Odoo #286555. The only capability that turns several 1s into 2s. **Measured:** a script over today's `--summaries` finds Kafka #24128 ([review.md](review.md#kafka-24128-run-on-the-pre-fix-sources)). | medium; a script first |
| 4 | **Resolve DI-injected interface fields** when the corpus has exactly one implementation, or an `@Bean`/`@Service` returning it. Draw it as an *assumed implementation* with its own label, so the frontier stays visible. | Every Fineract REST→service→domain chain is `EXTERNAL` today. Tension: `calls-and-dispatch.md` calls picking one implementation a guess. The label keeps it honest; reusing the existing "assumed implementation" boundary path may be enough. | medium |
| 5 | **Constant-keyed map slots**: `put/get`, `setNote/getNote` and `getAttribute` with a compile-time constant key become a named heap slot | Keycloak: 9 of 32 issues turn on auth/client notes, `contextData`, claims or attributes. The same shape appears in Odoo's vals and context. | medium |
| 6 | **Exceptions as outputs**: a throw escaping to a root becomes a boundary row | Kafka #24216, #24187, #24151, KAFKA-16304; Fineract FINERACT-3197-like paths. Already planned. | medium |
| 7 | **Flows through JDK functional APIs**: `Optional.map/orElse`, `stream().map`, `CompletableFuture.thenApply` pass values into their lambda | Kafka KAFKA-20734 and the coordinator's async writes; the Kafka client futures. | medium |
| 8 | **Root discovery**: REST resources, command handlers, `@Scheduled`, or "every public method of the changed classes" | Roots are named one by one with `--from`. A PR check needs them derived from the diff. | small |
| 9 | **Generated and Lombok sources without manual setup**: generated source dirs on the path, delombok run automatically | Kafka `*RequestData` is generated from JSON, so every request/response field becomes Opaque. Lombok touches 2,594 of Fineract's 5,585 main files. | small–medium |
| 10 | **Split control from data dependence in `--summaries`** | Keycloak: 25 sources reach one `put`, too many to point at anything. The graph already has CONDITION vs FLOW; the summary loses it. | small |
| 11 | **`writers(field)` and `readers(field)` queries** | Fineract and Odoo writer/reader mismatches. Fits the DolphinScheduler finding that an index must cover writers as well as readers. **Measured:** Keycloak #49604 is a `readers` answer. `build` reads 13 of `SP`'s 14 fields and the 14th is the bug, read off today's gap rows. | small; a script today |
| 12 | **Agent-first surface**: MCP queries returning small JSON with file:line | Copilot and Claude reviewers already comment on most Kafka PRs. Codeflow fits best as the deterministic evidence those reviewers cite, not as a second reviewer. | medium |
| 13 | **Field reads through an object with no tracked origin** (added by the review). First give them their own gap kind, so `unmodelled` means a construct again. Then keep the value, as an atom naming the origin and the field. | The largest wall measured: 18,742 gap rows in Fineract, about half of all. It is what hides Keycloak #49604 (`SP sp = adapter.getSps().get(0)`). | small for the kind; unknown for the value, which needs a design in `docs/decisions/summaries.md` |

**Order I'd take** (revised by [review.md](review.md#where-i-lean); the first version put 2 + 8 before 3, on
judged evidence):
- 1 is done.
- Then 3 and 11 as scripts over today's `--summaries` JSON. Two real bugs came out that way; none has yet for 2.
- Then 13's gap kind, so the numbers can be read.
- Then 2 + 8, which make a usable PR check on Fineract refactors.
- 4 and 5 widen how much the graph reaches; 6–7 deepen it.
- From now on a row gets its rank from a before/after run on the case it cites, not from a judgement.

**What not to build:** concurrency, SQL equivalence, config semantics. They are the biggest blocks of review
work, but a value-dataflow graph cannot see them, and an imitation would be the "silently wrong graph" this
project exists to avoid.

## Proposal: the change that adds the most value

**One engine, three uses: a per-root effect summary.** For each root, a small, stable, diffable list of what it
does to the world. The PR check, `diverges` and an agent's questions all become comparisons of that list. Most
of the planned work in the table above is one of those comparisons, or widens what the list can see.

**What a row is:**
- a field written, including an opaque mutator (`put`, `add`, `remove`) on a `this.<field>` receiver,
  which Kafka #2 and #6 need;
- a boundary call (db, net, event) with the **origin atoms of each argument** (Fineract #6627, Kafka #3);
- an exception escaping the root;
- the **gate** each row sits under: ungated, or `cond`/`!cond` (Kafka #4, Keycloak #53471);
- `file:line` for every row;
- a **frontier**: the `EXTERNAL`, `dispatch` and `unmodelled` gaps it could not see through.

Summaries already compute most of this per method (`--summaries`: sinks, effects, gaps). The work is
instantiating it transitively per root and adding gates, mutator writes and exceptions.

**The three uses:**

| Use | Comparison | Dimension | Evidence |
|---|---|---|---|
| **PR check** (blastRadius + refactor purity) | the same roots, before vs after the diff; roots derived from changed classes (#8) | D2a, D2b, D4 | Fineract #6846, FINERACT-2917/3197, KAFKA-21033: the #1 bug source in all 4 repos |
| **`diverges`** | sibling roots against each other (overrides, call sites, name stems, co-writers), or the fields of one class against each other, ranked by majority vote | D2b, D3, D4 | 6 Kafka cases, 4 Keycloak, Odoo #286555. Measured by script: Kafka #24128, Keycloak #49604 |
| **Agent queries** (MCP, small JSON) | one root's rows, or `writers(field)` | D1, D3 | AI reviewers already comment on most Kafka PRs; this gives them evidence to cite |

**The rule that keeps it honest.** A row missing from a root with a gap on a path that could reach it is
**unknown**, not absent. That holds for both the before/after diff and the sibling vote. Without it, a PR check
says "pure refactor" because both sides go `EXTERNAL` at the same interface, which is the silently wrong
answer this project exists to avoid.

**Then widen what it sees, in the order the study hit the walls:**
1. Field reads through an object with no tracked origin (improvement 13). Added by the review: it is the
   largest gap kind measured, and the wall on Keycloak #49604.
2. Lambda parameters of `forEach`/`Optional`/`stream` bound to the element origin. Today they are
   `Identity.Unknown`, and Kafka #3, #5 and #6 all go through them.
3. DI interface fields with one implementation in the corpus, drawn as an **assumed implementation** (the
   Fineract REST→service→domain chains).
4. Constant-keyed map slots (Keycloak notes and attributes, Odoo vals).
5. Generated and Lombok sources set up automatically (Kafka `*Data`, Fineract Lombok).

**Start as a script.** Both measured cases came out of today's `--summaries` JSON with under 20 lines of Python
each ([review.md](review.md)). So the first version of `diverges` is a script over that JSON, run on the cases
below. The engine work starts where the script stops finding them.

**Acceptance: real cases, not new fixtures.** The engine is done when each of these comes out from the pre-fix
source with no hand-picked roots. Only the two marked "found by script" have been run; the rest are predictions.
- **Kafka #24128** (found by script): `diverges overrides:getOrMaybeCreateMember` shows `put` on `this.members`
  in `ShareGroup` only.
- **Keycloak #49604** (found by script): `diverges fields:SP` shows `turnOffChangeSessionIdOnLogin` as the one
  field `DeploymentBuilder#build` never reads.
- **Fineract #6846:** the effects diff shows a **NEW** `saveAll` db write on a PR titled as a refactor.
- **KAFKA-21458:** `diverges stem:*GroupHeartbeat*` flags `classicGroupHeartbeatToConsumerGroup` for missing
  `updateSubscriptionMetadata`/`maybeUpdateTargetAssignment`.
- **KAFKA-21303:** `diverges callers:scheduleConsumerGroupSessionTimeout` shows `onLoaded`'s argument fed by
  group config while the classic sites use the member's timeout.
- **Keycloak #53471:** a gate row shows userinfo's `email_verified` overwriting the ID token's.
- The same runs report **unknown**, not absent, wherever the path crosses an interface with two implementations
  (Kafka #4).

**What this deliberately is not.** It is not a second reviewer and not a Python port. It does not model
concurrency, SQL or config ([never-sees.md](never-sees.md) shows each with code). It is deterministic evidence,
with file:line, that a human or an AI reviewer can check in one click.

## Caveats
- One model rater per repo, and hindsight risk on D4: introducing PRs were scored from the fix's description.
- Codeflow was run on 4 Keycloak cases in the study. The review re-ran one of them (#49604, before and after
  casts) and ran Kafka #24128. Every other score is a judgement about it, and no score was revised.
- The "Today?" paragraphs in [kafka-sibling-paths.md](kafka-sibling-paths.md) are predictions, except case 2.
- Each census is a 2–3 week window. Fineract's window was dominated by refactor series, which contradicts an
  earlier 40-commit pass that found about half of commits were docs/CI/deps/tests.
