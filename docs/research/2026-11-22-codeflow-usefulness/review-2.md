# Second review: the scores, re-checked by running the tool (2026-11-22)

**Answer.** The study's direction holds, its headline does not, and the order to build in changes.
- **The runs found a bug in the tool that outweighed every score. It is fixed (`d66e869`).** A call chained on
  a type outside the corpus, `t.a(d).b()`, was drawn as `b` alone: `t`, `a` and `d` vanished, with no gap
  counted and exit 0. In one Fineract file that was 120 of 1,604 call sites; after the fix, 0 of 1,814.
  See [the bug](#a-bug-the-runs-found-chained-calls-are-dropped). The runs in the table below were made before the fix.
- **Fineract's decisive bugs are 6 of 44, not 9 of 44**, and all six on today's tool. The three that fail are
  the ones that needed a planned feature to work as described.
- **"Prevents bugs" has no supported decisive case.** Both D4=2 scores rest on a diff row that a run shows
  does not exist.
- **Single-implementation dispatch has to come before the PR check.** On the PR check's own acceptance case
  (Fineract #6846) the affected callers show 0 rows today and 6 with it simulated.
- **`diverges` earns a third hit, but only with declared siblings.** With `stem:` it is 1,398 rows.
- In every case that holds, the answer was known first: the tool confirms a bug that is already located.

I ran codeflow on the pre-fix source of 12 cases the study scored without running. Scripts and commands:
[Reproduce](#reproduce).

## What the runs say

| Case | The study said | The run says |
|---|---|---|
| FINERACT-3022 / #6788 | D3=2, existing | **Holds.** A 9-node back-slice from the new deposit's amount ends at `assembleFrom(depositAccountId)`, the matured account; the receiver comes from `reinvestedDepositId`. |
| FINERACT-2993 / #6740 | D3=2, existing | **Holds.** `createFrom@174` in the second loop is fed by `feeChargesPortion@131`, the first loop's header, itself fed by `zero@98` and `payFeeChargesComponent@139`. |
| KAFKA-21324 / #23916 | D3=2, existing | **Holds.** The summary reads `P1 → RET.WindowKeyQuery.timeTo`, `P2 → RET.WindowKeyQuery.timeFrom` for `(key, timeFrom, timeTo)`. 0 gaps. |
| FINERACT-2917 / #6627 | D3=2, D4=2, planned | **Half.** "Attendees go to a local copy" is in today's graph: `addAll@103 ← clientsAttendance@55 ← getClientsAttendance ← new HashSet@204`, while `meeting` goes to `saveAndFlush` alone. "`findById` gets the group id" is not reachable from the REST root: the resource calls `dispatcher.dispatch(command)`, and a command dispatcher has no static edge. |
| FINERACT-3197 / #6834 | D3=2, D4=2, planned ("a GONE row") | **D3 holds with today's graph, D4 does not.** `toJson@235 → result@388 → instanceof@425` is drawn. But the graph is type-blind: `errorInfo → toJson → result → ex → getMessage → put` is still drawn in the buggy version. A before/after diff shows one extra hop, not a GONE row. |
| Fineract #6846 | D2a=2, D2b=2, planned; acceptance case | **Holds at the changed method, fails at the PR.** See [below](#the-pr-check-on-its-own-acceptance-case). |
| KAFKA-21458 / #24108 | D2b=2, planned; acceptance `diverges stem:` | **Found with declared siblings, lost with `stem:`.** See [below](#diverges-on-the-kafka-heartbeat-case). |
| KAFKA-21303 / #23856 | D3=1, planned; acceptance `diverges callers:` | **Partly, as predicted.** The difference is in today's summaries, 6 atoms of about 25. The sink merges all arguments, and the classic site's atom is named `get`: `member.classicProtocolSessionTimeout().get()` lost its receiver to the bug below. |
| FINERACT-2953 / #6663 | D3=2, planned ("a taint-style query") | **Holds, on today's graph.** The scope check takes `appTableId` alone. The DELETE's value is a gated phi: `datatableId == null` true → `appTableId`, false → `datatableId`. 67 nodes. |
| FINERACT-2909 / #6761 | D3=2, planned ("reaches=false + diverges") | **Holds, on today's summaries, as an absence.** `batchUpdateJournalEntries` has no `getOverdraftAmount` source, 0 gaps; both manual processors have `getOverdraftAmount → createCashBasedJournalEntries…`. One level up, in `batchUpdate`, the answer flips: the two lists share the sink name `add`. `diverges` lists it as one row of 36. |
| FINERACT-2996 / #6741 | D3=2, planned ("diverges") | **Does not hold.** No family: before the fix the cap's only caller is `LoanChargeService`, a file the PR does not touch. "Uses the percentage, never reads the cap" is 45 of 221 methods; the 6 fixed sites are among them. |
| FINERACT-3034 / #6868 | D3=2, D2b=2, planned ("diverges") | **Does not hold.** `rescheduleNextInstallments` ends by calling `generate`, which is inlined, so both have `getChangedActualRepaymentDate`: no difference at method level. And the bug's own line is drawn wrong, see below. |

Not re-run: Keycloak #53471 (the study ran it) and FINERACT #6896.

### A bug the runs found: chained calls are dropped

**Fixed in `d66e869` ("only a name can be a type name at a receiver"), and re-run on the same inputs:**

| Input | Before | After |
|---|---|---|
| `review-2/chain-repro` | 3 of 5 methods lose `P0 → RET` | all 5 keep it; `argInChain` keeps `P1 → RET` too; 0 gaps |
| Fineract `rescheduleNextInstallments` | 120 of 1,604 call sites lost their chain; 10,194 nodes | 0 of 1,814; 10,812 nodes |
| Line 2339 of that file | `getChangedScheduleDate` with only the event in | `getScheduledDateGenerator → adjustRepaymentDate` (4 inputs) `→ getChangedScheduleDate → lastInstallmentDate` |
| Kafka `classicGroupHeartbeatToConsumerGroup`, `consumerGroupHeartbeat` | 3 of 274 to 12 of 570 | 0 of 291, 0 of 685 |

The rest of this section describes the tool as the twelve runs met it.

The line FINERACT-3034 fixes is
`lastInstallmentDate = getScheduledDateGenerator().adjustRepaymentDate(actualRepaymentDate, …).getChangedScheduleDate();`
The graph drew `getChangedScheduleDate` with the event as its only input. `adjustRepaymentDate`, its
receiver and its three arguments were not in the graph. Nothing was counted as a gap; the run exited 0.

| Written as | Type of `t` | Summary, before the fix |
|---|---|---|
| `t.a().b()` | not in the corpus | `P0 → RET` missing |
| `t.inner.b()` | not in the corpus | `P0 → RET` missing |
| `t.a(d).b()` | not in the corpus | `P0 → RET` and `P1 → RET` missing |
| `Dto x = t.a(d); x.b()` | not in the corpus | correct |
| `s.trim().length()` | `String` | correct |

- **Cause.** `Lowering.isTypeName` asked javac what the receiver resolved to. A call on an error-typed receiver
  comes back as an `Element` of kind `CLASS` (`Symbols.element` documents this). So the receiver `t.a(d)` was
  taken for a type name, like the `Math` of `Math.abs`, and never evaluated.
- **How often.** Call sites that lost their receiver chain, counted by `review-2/dropped.py`:
  120 of 1,604 in `AbstractCumulativeLoanScheduleGenerator` (getter chains such as
  `scheduleParams.getOutstandingBalance().isZero()`); 3 of 274 to 12 of 570 in the Kafka heartbeat handlers
  (builder chains such as `new ConsumerGroupMember.Builder(member).setMemberEpoch(0).build()`).
  Both are single-file corpora, the worst case: the more of the code is in the corpus, the fewer.
- **What it put in doubt.** Everything that reads an absence: `diverges` rows marked firm, a GONE row, a
  `reaches = false`. The cases above that hold were checked against the source, so they stand. One reading
  changes: the #6868 row's "drawn wrong" no longer applies, but its score does not move, since the two
  siblings still show no difference at method level.
- Repro: `review-2/chain-repro/B.java`, 23 lines, five methods: before the fix three lost `P0 → RET`.

### The PR check on its own acceptance case

The bad revision is `ec7b4f3`, the one the reviewer objected to; the merged PR no longer has the line.
Base: merge-base `d63beed`.

| Corpus | Diff | Result |
|---|---|---|
| The helper, its interface, one caller | flows per method, positions stripped | 1 of 31 methods changed, 4 NEW rows, among them `P0 → ext-arg:saveAll`. The reviewer's catch, from 30 lines of Python over today's `--summaries`. |
| The 7 main files of the PR | same | 56 of 132 methods changed, 6,167 NEW and 6,352 GONE rows. 900 moved lines bury the 4. |
| same | external-call names per method, moved methods matched by name | 22 methods changed. In 14 of them `saveAll` is **GONE**: they used a private copy that had it, and now call the shared helper through an interface. The sign is wrong. |
| The caller, in an unchanged file | flows per method | **0 rows.** 17 `dispatch` gaps. |
| The caller, field typed as the one implementation (simulated in a scratch copy) | same | 7 of 31 methods changed: the helper and 6 caller methods, `makeRepayment` among them. All NEW, none GONE. |

- The reviewer's sentence was "it's called from `LoanAccountDomainServiceJpa` (so every repayment)". That row
  appears only in the last line of the table.
- **Roots "from the changed classes" (improvement 8) miss it.** The affected roots are in files the PR did not
  touch. The roots are every root that reaches a changed method, which needs the same dispatch.
- The "unknown, not absent" rule is not optional: without it this PR reports 14 removed `saveAll` calls.

### `diverges` on the Kafka heartbeat case

`GroupMetadataManager.java` at `146cf78`: 284 methods, 133,890 pairs, 48 gaps, 1.6 s.

| Family | Result |
|---|---|
| `stem:*GroupHeartbeat*` (`scripts/diverges.py`, as it stood, untracked) | 16 siblings, 1,398 rows. A majority vote over the 9 non-trivial ones flags the buggy handler for **0** sinks. |
| The four siblings the reviewer compared, named one by one | The first group is "in 2,3,4, not in 1", the buggy handler: 72 rows, 38 distinct effects, firm (0 gaps). The first row is `call setMetadataRefreshDeadline`, the missing refresh. |

- The study's sentence, "flags B for missing `updateSubscriptionMetadata`/`maybeUpdateTargetAssignment`", cannot
  come out of `--summaries`: in-source callees are inlined, only their external leaves remain. Read off the
  METHOD boxes of `--json`, both names are among 21 flagged.
- The family is the hard part. The reviewer knew which three handlers were siblings; the name does not say it.

## Scores I would change

**D3, decisive, by what backs the score:**

| Repo | Study | Of those, on today's tool by the study's own labels | By run |
|---|---|---|---|
| Fineract | 9 of 44 (20%) | 3 of 44 (7%) | **6 of 44 (14%)**: #6788, #6740, and four labelled planned whose fact is in today's output (#6834, #6663, #6761, half of #6627). Not: #6741, #6868, and #6896 by the study's own report. |
| Keycloak | 1 of 32 | 1 | not re-run |
| Kafka | 1 of 47 | 1 | confirmed |
| Odoo | 3 of 39 | none: no Python tool exists | not runnable |

- **Headline.** "Today, decisive on one bug in five in Fineract" should read "one in seven". All nine claims
  are now checked, eight by run.
- **#6741 and #6868 go from 2 to 1.** Both needed `diverges` to single out a method, and it does not.
- **D4.** Fineract's two 2s (#6834, #6627) go to 1. Decisive D4 is then 0 of 168 scored items in the four
  repos.
- **Capability labels are wrong in both directions.** Four "planned" bugs are visible today with no new
  feature. The planned features are what failed: no `diverges` difference (#6868), no family (#6741), no GONE
  row (#6834).
- **#6896** is D3=2 in `items.tsv` and "looked useful, isn't, score 1" in `fineract/report.md`. Take 1.
- **Keycloak #53633** is in the README's list of decisive cases and scored 1 in `items.tsv`.
- **Kafka #24108 D2b=2** holds only with declared siblings. Keep the 2, change the acceptance line.
- **A stricter bar** (my judgement, not a run): in #6788 and KAFKA-21324 the bug is 3 lines apart or in the
  parameter names, and the source shows it as fast as the graph. The cases where the graph beats reading are
  #6740 (a value carried across two loops), #6761 (an absence across a 300-line class) and Keycloak #53471
  (a gated phi).

## Problems with the method

- **"2" means three things.** The fact is in the output; the output points at it without the answer in hand;
  nothing cheaper shows it. The study scored the first with the answer known. A run can only check the first.
- **The table's rows do not share a denominator.** Keycloak scores D1 and D2 on 36 PR rows and D3 and D4 on 32
  issue rows. Fineract is 62 / 36 / 44, Kafka 53 / 47 / 48, Odoo 44 / 29 / 39.
- **The percentages are over curated items, not PRs.** 43 of Fineract's 62 items are bugfixes; in its census,
  19 of 100 PRs are, and 52 are refactors. Per PR opened, the value is lower than the table reads.
- **Odoo is scored for a tool that does not exist.** It belongs in its own table.
- The arithmetic is right: every percentage in the score table reproduces from the TSVs.

## What changes in the order to build

0. **Fix the dropped chain first.** Done, in `d66e869`. It was not on the list of 13, and every feature below
   reads absences.
1. **`diverges` stays first, with a narrower promise.** Families the code gives (`overrides:`, `fields:`) and
   siblings a person names: three measured hits. Take `stem:` out of the acceptance list.
   - It compares whole methods. A sibling that ends by calling the other one (#6868) shows no difference.
2. **Improvement 4 (one implementation in the corpus, drawn as assumed) moves ahead of the PR check.** It is
   the difference between 0 and 6 caller rows on #6846, and between a GONE and a NEW `saveAll`.
3. **The PR check** then needs three things the study did not list: roots that *reach* a changed method, moved
   methods matched across classes, and external-call names as the first, coarse row.
4. **Two small candidates the runs turned up:**
   - *A gate that cannot hold at this call site* (FINERACT-3197). The argument's static type is `String`, the
     pattern's is `ErrorInfo`; javac can say they are disjoint. Without it the predicted GONE row never exists.
   - *Crossed names* (KAFKA-21324): a parameter named `timeFrom` reaches the field `timeTo` while a parameter
     named `timeTo` exists. Needs parameter names in the summary. Noise on a corpus is not measured.
5. **The proposal costs more than "summaries already compute most of this".** Three parts of a row are not in
   today's output:
   - the gate on a call: #6834's graph has three `if`s and no CONDITION edge, because only phis are gated;
   - the origins of each argument: `ext-arg:schedule` merges five arguments (KAFKA-21303);
   - in-source callee names: inlined away (KAFKA-21458).

## Would I go deeper

Yes, in one direction only: runs, not more scored items.
- Done here: every Fineract D3=2 claim. Twelve runs moved four scores (two D3, two D4) and found one bug in
  the tool; scoring by judgement had found none.
- The chain fix is in, and `dropped.py` reads 0 on the three graphs above. Still to re-run: the absence cases
  (#6761, KAFKA-21458, KAFKA-21303) and `dropped.py` on the smoke corpus.
- One blind pass: the pre-fix source and the issue text, no fix. That measures the second meaning of "2".
- Not worth it: widening the sample, or re-scoring D1.

## Reproduce

JDK 25, `./gradlew installDist`, `APP=app/build/install/app/bin/app`. Scripts in [review-2/](review-2/). Each
case's files go in their own empty directory.

```shell
review-2/parent.sh apache/fineract 6788                   # <merge> <pre-fix parent>
review-2/fetch.sh apache/fineract <ref> <dir> <path>...   # the files at that ref
$APP <dir> --summaries > sum.json
$APP <dir> --json --from Class#method > g.json
python3 review-2/q.py g.json nodes <line>                 # the boxes on a line
python3 review-2/q.py g.json back <id> 6 FLOW,TRUE,FALSE  # what reaches a box
python3 review-2/sumdiff.py before.json after.json        # flows per method, NEW and GONE
python3 review-2/effdiff.py before.json after.json        # external-call names, moves matched by name
python3 review-2/vote.py sum.json '<regex>' <min flows>   # what one sibling lacks and the others have
python3 review-2/dropped.py g.json <source.java>          # call sites that lost their receiver chain
$APP review-2/chain-repro --summaries                     # the bug: before d66e869, three methods without P0 → RET
```

| Case | Pre-fix ref | Files | Root or query |
|---|---|---|---|
| #6788 | `49a0b7d` | `DepositAccountWritePlatformServiceJpaRepositoryImpl.java` | `#updateMaturityDetails`, back from `handleFDDeposit@1233` |
| #6740 | `e0ae48b` | `RBILoanRepaymentScheduleTransactionProcessor.java` | `#handleTransactionThatIsALateRepaymentOfInstallment`, back from `createFrom@174` |
| #6627 | `6e36068` | the `meeting` package, 28 files | `MeetingAttendanceWriteServiceImpl#updateMeetingAttendance`, back from `addAll@103` |
| #6834 | `c86b028` | `SynchronousCommandProcessingService.java` | `#publishHookErrorEvent`, forward from `errorInfo@233` |
| #6846 | `d63beed` → `ec7b4f3` | `LoanAccountServiceImpl`, `LoanAccountService`, `LoanAccountDomainServiceJpa`; then the PR's 7 main files | `sumdiff.py`, `effdiff.py` |
| KAFKA-21324 | `7d07ebf` | `WindowKeyQuery.java` | `--summaries` |
| KAFKA-21458 | `146cf78` | `GroupMetadataManager.java` | `vote.py`, `scripts/diverges.py` |
| KAFKA-21303 | `a3444d8` | `GroupMetadataManager.java` | sources of `ext-arg:schedule@…:5509`, per caller |
| #6663 | `c01580c` | `DatatableWriteServiceImpl.java` | `#deleteDatatableEntry`, back from `update@1317` and from `checkMainResourceExistsWithinScope@1304` |
| #6761 | `f375431` | `SavingsSchedularInterestPoster`, `AccrualBasedAccountingProcessorForSavings`, `CashBasedAccountingProcessorForSavings` | `--summaries`, sources named `getOverdraftAmount`, per method |
| #6741 | `3a7b5da` | the PR's 4 files, `LoanCharge`, `LoanChargeService` | `--summaries`, methods with `getPercentage` and without `LoanCharge.minCap` |
| #6868 | `9da98fd` | `AbstractCumulativeLoanScheduleGenerator.java` | `--summaries`; `#rescheduleNextInstallments`, boxes on line 2339; `dropped.py` |

The single-implementation run is a scratch copy of the caller with one line changed: the field
`LoanAccountService loanAccountService` typed as `LoanAccountServiceImpl`.
