#codeflow
# codeflow proposal — a dataflow hierarchy view

Date: 2026-09-01. Sixth doc in the codeflow series. A concrete proposal for what to build, given the evaluation in "codeflow usefulness - rate factor bug and ten other Fineract PRs". Direction fixed by the author: **not a graph picture — an IntelliJ-style call hierarchy panel, but the edges are dataflow instead of calls.**

---

## 1. The one idea

IntelliJ's call hierarchy answers "who calls this?" as an expandable tree, one method per node, drill down on demand. Codeflow answers **"who reads what this line wrote?"** (forward) and **"where did the values this line reads come from?"** (backward) — same panel, same interaction, but nodes are statements and the edge is a value, not a call.

Everything else in this proposal is what falls out of taking that seriously.

### The node

```
RepaymentPeriod.java:142   getEmi().minus(getCalculatedDueInterest())      [via field emi, calculatedDueInterest]
```

File:line, the statement text, and the *carrier* — which variable, field, parameter or return value carries the value along this edge. The carrier is what a call hierarchy can never show and is what made the rate factor bug hard: the value crosses `setRateFactorTillPeriodDueDate` → field → `getRateFactorTillPeriodDueDate` with no call between writer and reader.

### The three node decorations

| Decoration | Meaning | Why it earns its place |
|---|---|---|
| `⇥ net:in` `⇥ db:in` `⇤ net:out` `⇤ db:out` `⇤ throws` | Boundary leaf | The tree stops here; this is where the value enters or leaves the process. Exceptions are outputs. |
| `⟳ cycle (n stmts)` | Strongly-connected component, collapsed into one node | Fixed-point calculations (EMI) would otherwise expand forever. Expand to see members. |
| `NEW` `GONE` `PATH` | PR mode badge | What the PR did to this node or edge. |

---

## 2. The four views

All are the same tree component with a different root and direction.

### 2a. Forward from a line — "what does this change affect?"

Root: a statement. Children: statements that read a value it wrote. Default depth 2, expand on demand; **"boundaries only"** toggle collapses interior nodes and shows each boundary leaf once with its shortest path length.

Example — the rate factor bug, root `ProgressiveEMICalculator.java:425` `setRateFactorTillPeriodDueDate(...)`:

```
▼ ProgressiveEMICalculator.java:425  interestPeriod.setRateFactorTillPeriodDueDate(calculateRateFactorPerPeriodForInterest(...))
  ▼ InterestPeriod.java:85   .multiply(getRateFactorTillPeriodDueDate(), mc)        [field rateFactorTillPeriodDueDate]
    ▼ RepaymentPeriod.java:114  ...map(InterestPeriod::getCalculatedDueInterest)...   [return]
      ▼ RepaymentPeriod.java:142  getEmi().minus(getCalculatedDueInterest())          [return]
        ▼ RepaymentPeriod.java:147  max(getEmi().minus(getDueInterest()), getPaidPrincipal())
          ▶ ⟳ cycle (EMI fixed point, 14 stmts)   RepaymentPeriod:169 → InterestPeriod.updateOutstandingLoanBalance → ProgressiveEMICalculator:638..650 → RepaymentPeriod:110 …
          ▶ ProgressiveLoanScheduleGenerator.java:116  addPrincipalAmount(getDuePrincipal())   → ⇤ db:out m_loan_repayment_schedule.principal
      ▼ RepaymentPeriod.java:135  getPaidPrincipal().isGreaterThan(getCalculatedDuePrincipal())
        ▶ ProgressiveLoanScheduleGenerator.java:117  addInterestAmount(getDueInterest())     → ⇤ db:out m_loan_repayment_schedule.interest
        ▶ LoansApiResource.java:506  calculateLoanSchedule(...)                              → ⇤ net:out POST /v1/loans?command=calculateLoanSchedule
      ▶ ProgressiveLoanInterestScheduleModel.java:287  getTotalDueInterest()
        ▶ ProgressiveEMICalculator.java:336  diff = totalDisbursedAmount.plus(totalDueInterest).minus(totalEMI)
          ▶ :340  setEmi(getEmi().add(diff))   ⟳ (member of the EMI cycle)
```

With "boundaries only":

```
ProgressiveEMICalculator.java:425
  ⇤ net:out  POST /v1/loans?command=calculateLoanSchedule        depth 6
  ⇤ net:out  GET  /v1/loans/{id}?associations=repaymentSchedule  depth 8
  ⇤ db:out   m_loan_repayment_schedule.interest                   depth 6
  ⇤ db:out   m_loan_repayment_schedule.principal                  depth 7
  ⇤ db:out   m_loan_transaction.interest_portion_derived           depth 9   (via AdvancedPaymentScheduleTransactionProcessor:1210)
  ⟳ cycle    EMI fixed point (14 stmts) — includes RepaymentPeriod:110 rateFactor, ProgressiveEMICalculator:650 setEmi
```

That last line is the review comment: *"this touches the EMI, not just interest."* The full tree is there if the reviewer doubts it.

### 2b. Backward from a boundary — "where does this output come from?"

Root: a `net:out` or `db:out`. Children: statements whose values reach it. This is the debugging view: bug says "interest is wrong on the schedule preview", root is the `interestDue` field of `calculateLoanSchedule`, and the tree is the filtered call stack — every method that only *routes* the value (dispatcher, service, controller) is absent, because no value edge passes through it.

Example — root `⇤ net:out calculateLoanSchedule.periods[].interestDue`, first three levels:

```
▼ ⇤ net:out  LoanSchedulePeriodData.interestDue
  ▼ ProgressiveLoanScheduleGenerator.java:117  addInterestAmount(repaymentPeriod.getDueInterest())
    ▼ RepaymentPeriod.java:131-139  getDueInterest()
      ▼ RepaymentPeriod.java:123  getCalculatedDueInterest()
        ▼ RepaymentPeriod.java:126  += getPrevious().get().getUnrecognizedInterest()
        ▼ InterestPeriod.java:85   .multiply(getRateFactorTillPeriodDueDate(), mc)
          ▶ ProgressiveEMICalculator.java:425  setRateFactorTillPeriodDueDate(...)   ← the fix site
          ▶ ProgressiveEMICalculator.java:252  setRateFactorTillPeriodDueDate(BigDecimal.ZERO)
      ▶ RepaymentPeriod.java:142  getCalculatedDuePrincipal()
```

Note what is not in it: `SynchronousCommandProcessingService:128`, `LoanScheduleApiResource:77`, `getPeriodInterestTillDate:220`. They are on the call stack; they carry no value.

### 2c. PR mode — "what did this PR do to the boundaries?"

Root list: one entry per **boundary pair** touched, then one per changed line. Computed as boundary-pair diff of the before/after graphs. This is the default view on a PR page.

Example — `542da12e6` "Simplify allocation rule logic":

```
GONE  path   (no allocation rule configured) → List.of(DUE_PRINCIPAL) → ⇤ db:out m_wc_loan_transaction allocation
      ▶ WorkingCapitalLoanAllocationRequestFactory.resolveAllocationOrder  (deleted)   "No configured order: keep the legacy principal-only behaviour."
NEW   ⇤ throws  NoSuchElementException  ← getDefaultAllocationRule ... .findFirst().get()
      ▶ reaches ⇤ net:out POST /v1/workingcapitalloans/{id}/transactions  as HTTP 500
PATH  ⇥ db:in  m_wc_loan_payment_allocation_rule → ⇤ db:out allocation      (interior changed, endpoints same)
```

Commit says "simplify". Panel says: one legacy output path removed, one error path added. Two lines, and both are things a reviewer would want to ask about.

Example — `d602928ae` "Fix payment allocation rule processing" (130 lines moved into `WorkingCapitalLoanTransactionProcessor`):

```
Boundary pairs: 11 before, 11 after, 11 identical.
PATH  11 pairs — interior statements moved from WorkingCapitalLoanWritePlatformServiceImpl to WorkingCapitalLoanTransactionProcessor
NEW   0
GONE  0
```

That header is a statement no human reviewer can make about a 700-line diff. Expand any PATH row to see the old and new interior side by side.

Example — `745ae67de` "Enforce charge rounding" (8 files, four domains), boundaries-only, grouped by module:

```
PATH  loan     ⇥ net:in charge.amount → Money.of(currency, …).getAmount() → ⇤ db:out m_loan_charge.amount           (LoanChargeService)
PATH  client   ⇥ net:in charge.amount → … → ⇤ db:out m_client_charge.amount                                          (ClientCharge, ClientChargeWritePlatformServiceImpl)
PATH  savings  ⇥ net:in charge.amount → … → ⇤ db:out m_savings_account_charge.amount                                 (SavingsAccountCharge)
PATH  share    ⇥ net:in charge.amount → … → ⇤ db:out m_share_account_charge.amount, m_share_account_transactions.amount (ShareAccountCharge, ShareAccountTransaction, ShareAccountDataSerializer)
NEW   loan     ⇤ throws LoanChargeCannotBeAddedException "amount.rounded.to.zero"   ← validateChargeAmountNotZero
```

Review question it answers directly: four test classes were added — do they cover these five rows? (The `NEW throws` row has no dedicated test in the commit; that is the finding.)

Example — `eb24cdce0` survey SQL, **before** graph, backward from the `db:in`:

```
▼ ⇥ db:in  ReadSurveyServiceImpl  jdbcTemplate.query(sql, …)
  ▼ sql = "SELECT … FROM " + registeredSurveyName + " tz …"      [string concat, no transform]
    ▶ ⇥ net:in  registeredSurveyName  (path param)
```

A `net:in → db:in` edge whose only interior is string concatenation is an injection. The after graph inserts `sqlGenerator.escape(...)`. This is a check worth running on the whole codebase, not only on PRs.

### 2d. Agent mode — the same tree as text

Everything above is already text. Expose it as three calls an AI can make with a file:line or a boundary name — `forward`, `backward`, `boundary_diff` — returning exactly the indented output shown here, with a depth cap. For the rate factor bug this replaces reading ~3,000 lines of Java across four files with one `backward` call of ~40 lines, deterministic, and empty only when the value genuinely has no source (never silently, as jdtls did under Lombok).

---

## 3. What the graph must carry (learned from the ten-commit sample)

1. **Value edges** through fields, parameters, returns, collections, streams and method references (`InterestPeriod::getRateFactor` at `RepaymentPeriod:110` was the one grep missed).
2. **Control edges**, shown as a lighter node type: `guards` — `SavingsAccount: if (amount != null && …)` guards the tax computation. Without them the two NPE fixes and the state-machine change in the sample are invisible.
3. **Exceptions as `⇤ throws` boundaries**, resolved to the HTTP status they produce where a mapper exists.
4. **Boundary typing per framework**, as a small pattern file: JAX-RS params/returns = `net`, JPA entity fields with `@Column` + repository writes = `db:out`, `@Query`/JdbcTemplate/repository reads = `db:in`. Fineract needs about four such rules.
5. **SCC collapse** at query time, not build time, so a cycle can still be expanded.

## 4. What not to build

- Not the dataflow engine. Joern or CodeQL produce the graph; codeflow owns boundary typing, the diff, the SCC collapse, and the panel.
- Not a general "understand this code" browser. That is what an AI with an LSP does now.
- Not a rendered graph. The tree with expand-on-demand is the product; the "boundaries only" toggle is the feature.

## 5. The two-week test before committing

Run views 2c on `542da12e6`, `d602928ae`, `745ae67de` using Joern for the graph. Pass = two of three panels come out as shown above without hand-editing. Fail = Joern cannot see through Lombok/Spring/JPA well enough; then stop, having spent two weeks not two years.
