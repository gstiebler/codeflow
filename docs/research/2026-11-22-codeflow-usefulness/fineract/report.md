# Fineract: where codeflow would help

## Sample method
- **Census**: last 100 merged PRs, #6822–#7004, merged 2026-11-04 → 2026-11-22 (gh API). Classified by title, body and diff file list.
- **Deep dive**: 36 census PRs (`items.tsv`, rows `PR#…`): every production bugfix with a diff (19), plus refactors and features with substantive human review. Chosen before scoring.
- **JIRA**: the 60 most recently resolved Bug/Fixed issues (of 907, JIRA REST, no login). Kept 26 with a linked fix PR or a clear root cause (#6174–#6831, mostly 2025–2026). Diffs read.
- **Comments**: 48 substantive review comments from the deep-dive PRs (`comments.tsv`). Bots, LGTM and rebase requests skipped.
- 62 items, 43 bugs. Checkout (develop, Nov 2026) used to check types and DI.

## Census (n=100)
| kind | n |
|---|---|
| refactor | 52 |
| bugfix (incl. 1 security) | 19 |
| feature (incl. 1 security) | 8 |
| test-only | 9 |
| build/CI/deps | 8 |
| config | 2 |
| docs | 1 |
| perf | 1 |

**Counterexample to the prior "~half is docs/CI/deps/test":** here it is 20%. The window is dominated by planned refactor series: JsonCommand removal, QueryDSL migration ("SQL 0xx"), service splits and module extraction.

## Score distribution (n/a excluded)
| dim | n | mean | 0 / 1 / 2 |
|---|---|---|---|
| D1 understand | 62 | 0.45 | 34 / 28 / 0 |
| D2a own PR | 36 | 0.42 | 22 / 13 / 1 |
| D2b review | 36 | 0.64 | 16 / 17 / 3 |
| D3 solve bug | 44 | 0.80 | 18 / 17 / 9 |
| D4 prevent | 44 | 0.43 | 27 / 15 / 2 |

- Capability needed: EXISTING 15, PLANNED 23, none 24.
- Of the 12 decisive (2) scores on D3/D4, 7 need PLANNED queries (slice_back, diverges, blastRadius).
- Comments: 30 score 0, 15 score 1, 3 score 2. **Only 3 of 48 review comments (6%) are dataflow facts codeflow surfaces directly.**

## Best cases
1. **FINERACT-2917 → [#6627](https://github.com/apache/fineract/pull/6627)** (meeting attendance).
   - The bug: `meetingRepository.findById(request.getEntityId())` receives the group id, not the meeting id, and `clientsAttendance.addAll(result)` adds to a local copy.
   - Dataflow fact: the findById argument slices back to the `groupId` path param; the new attendees never reach `meeting.clientsAttendance`.
   - It came from the FINERACT-2548 refactor, so blastRadius on that PR would show a PATH-changed row for the findById argument and a GONE row for attendance → field. D3=2, D4=2 (PLANNED).
2. **FINERACT-3197 → [#6834](https://github.com/apache/fineract/pull/6834)** (failure webhooks).
   - The bug: `publishHookEvent(..., gson.toJson(errorInfo))` passes a String, so the `instanceof ErrorInfo` gate is never true.
   - Dataflow fact: `status`/`response` never reach the webhook payload. It is a gated phi whose condition can't hold for that argument type.
   - Introduced by FINERACT-1744 (a GONE row). D3=2, D4=2.
3. **[#6846](https://github.com/apache/fineract/pull/6846)** (a "split" refactor). The shared `saveAndFlushLoanWithDataIntegrityViolationChecks` gained `loanRepaymentScheduleInstallmentRepository.saveAll(...)`. The reviewer had to work this out by hand ("this isn't a refactor anymore"). A boundary diff rooted at the changed impl gives a NEW db:out row on a supposedly pure refactor. D2a/D2b=2 (PLANNED).
4. **[#6954](https://github.com/apache/fineract/pull/6954)**: the reviewer argued that `AccountTransferDTO.accountTransferDetails` is null at all 17 constructions, so the deleted branches were dead. That is a pure field-writers / slice_back fact. D2b=2 (PLANNED).
5. **FINERACT-3034 → [#6868](https://github.com/apache/fineract/pull/6868)** (holiday move lost on regeneration).
   - The retained-installment loop never writes `actualRepaymentDate` from `adjustedDateDetailsDTO`; full generation does. This is a loop-carried missing propagation plus a divergence between two paths.
   - The review listed other callers of `rescheduleNextInstallments`: a blastRadius root list.
   - D3=2, D2b=2.
6. **FINERACT-2993 → [#6740](https://github.com/apache/fineract/pull/6740)** and **FINERACT-3022 → [#6788](https://github.com/apache/fineract/pull/6788)** (domain-entity code, no DI in the way, EXISTING graph). In #6740, fee/penalty portions defined in the outer scope are reused for the next installment (a stale loop-carried def). In #6788, the reinvested FD's balance slices back to the matured account (`account` vs `reinvestAccount`).
7. **FINERACT-2909 → [#6761](https://github.com/apache/fineract/pull/6761)** and **FINERACT-2996 → [#6741](https://github.com/apache/fineract/pull/6741)** (diverges). In #6761, the job path has reaches(overdraftAmount, GL booking)=false while the manual processors split it. In #6741, the min/max cap is applied on the charge but not in 4 schedule paths. D3=2 (PLANNED).
8. **FINERACT-2953 → [#6663](https://github.com/apache/fineract/pull/6663)**: the office-scope guard is checked on `apptableId` while the DELETE/UPDATE is keyed on `datatableId`. "The guarded value is not the sink value" is a reaches/taint query. D3=2 (PLANNED).

## Looked useful, isn't
- **QueryDSL "equivalence" reviews** (#6969, #6977, #6962, #6940, #6952, #6921). The largest review workload. The fact lives in SQL text; Java dataflow is identical on both sides. Score 0.
- **Pure-move verification** (#7001, #6860, #6974, …). It looks like a blastRadius showcase, but the reviewer's check (identical bodies, `@Transactional` kept) is answered by `git diff --color-moved` plus annotations. codeflow sees no annotations, and new interfaces make rows noisy. Score 1.
- **#6929 OIDC "usernameClaim has no effect"**. It is a reads-of-a-field fact, but the value arrives by `@ConfigurationProperties` binding, and "nobody calls the getter" is an IDE find-usages. Score 1.
- **FINERACT-3196 (stale value after concurrent update)**. It reads like a wrong-value bug, but it is REPEATABLE_READ snapshot isolation. Score 0.
- **#6896 / #6951 `parameterError` argument order**. Arg→param binding is decisive and EXISTING, but IDE parameter hints show it equally well. #6951's real issue (the import path doesn't reach the RD override) crosses two injected interfaces and shows as EXTERNAL. Score 1.
- **Null-guard / condition tweaks** (#6849, #6975, FINERACT-2881, 2950): weak shape confirmed.

## Surprises
- Refactors, not docs or CI, dominate, and reviewers do impact analysis by hand on nearly every split or move PR. That is the strongest product signal for blastRadius in this repo, despite the noise caveats above.
- Several bugs in the JIRA sample were *introduced by refactors* (FINERACT-2917 ← 2548, 3197 ← 1744, 3012 ← the 2022 LocalDate migration, 2944 ← 2169). D4 should be credited at the introducing PR, not the fix.
- The high-scoring bugs sit in domain entities and schedule generators (SavingsAccount, LoanScheduleGenerator, processors), which codeflow can analyse today. Bugs in the REST/service/Spring layer are mostly config, security DSL, Jackson or SQL, and score 0.

## Tool problems / limits
- **Codeflow was not run; all scores are judgement.**
- The gh API and JIRA REST worked with no rate limits. A few JIRA bugs had no linked PR (FINERACT-1980 was closed with no PR), so they were dropped. The diff for #6620 was not fetched, so its file count is "-".
- One rater; hindsight risk is real.

## Capability that would most raise the scores
**Resolving interface-injected services.** Fineract wires everything through interface-typed fields: about 1,928 `private final …Service` fields and 76 `*Configuration` @Bean classes using `@ConditionalOnMissingBean`.

- `Frame.resolve` falls back to javac's bodyless interface method, so every REST → service → domain chain is EXTERNAL unless the user hand-lists `--boundary` classes.
- With a fallback of "single implementation in corpus, or the @Bean factory's return type," #6951, #6846 (callers), #6929 and FINERACT-2917 (REST root) would each move up a point, and blastRadius roots could be REST endpoints instead of hand-picked methods.
- Second: exceptions-as-outputs (#6897, #6887/#6864, #6929, FINERACT-2916).
- Third: a cheap `writers(field)` query (#6954, FINERACT-2999).
- Setup cost on every item: Lombok in 2,594 of 5,585 main files needs `scripts/delombok.sh`.
