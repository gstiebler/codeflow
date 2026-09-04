# What the tool prints

Every block below is real output, copied unedited from a run over Fineract. The corpus for the
first four is `fineract-working-capital-loan/src/main/java` (316 files, 46k graph nodes) at
`542da12e6`; the diffs at the end are the three acceptance commits from FINDINGS.md.

Reproduce any of them with `spike/build-cpg.sh` and `spike/dump.sh` (see README.md), then the
command shown above each block.

---

## `boundaries` - where values enter and leave the process

```
$ node spike/src/cli.ts boundaries spike/out/542/after.json

in  net    POST /v1/internal/working-capital-loans/{loanId}/place-lock/{lockOwner}   InternalWorkingCapitalAccountLockApiResource.java:74
out net    POST /v1/internal/working-capital-loans/{loanId}/place-lock/{lockOwner} returns   InternalWorkingCapitalAccountLockApiResource.java:90
in  db     WorkingCapitalLoanRepository.findById                 WorkingCapitalLoanWritePlatformServiceImpl.java:716
out db     WorkingCapitalLoanRepository.save                     WorkingCapitalLoanWritePlatformServiceImpl.java:752
in  json   json amount                                           WorkingCapitalLoanChargeWritePlatformServiceImpl.java:249
out throw  throws NoSuchElementException in getDefaultAllocationRule   WorkingCapitalLoanAllocationRequestFactory.java:65
```

831 rows in this module, of six kinds:

| kind | count |
|---|---|
| `out throw` | 280 |
| `in db` | 205 |
| `in net` | 161 |
| `out db` | 94 |
| `out net` | 86 |
| `in json` | 5 |

Every rule fires; none is dead weight. (The paths are shown shortened here - the tool prints them
in full, relative to the source root.)

---

## `forward` - what the values on this line reach

This is the whole of `542da12e6` in twelve lines. The commit deleted the `List.of(DUE_PRINCIPAL)`
fallback in `getAllocationRule` and changed `findFirst().orElse(null)` to `findFirst().get()`; ask
what the allocation rule reaches afterwards and all three new failures are on the page, each under
the carrier that gets it there.

```
$ node spike/src/cli.ts forward spike/out/542/after.json \
    org/apache/fineract/portfolio/workingcapitalloan/service/WorkingCapitalLoanAllocationRequestFactory.java:52

▼ …/service/WorkingCapitalLoanAllocationRequestFactory.java:52  getAllocationRule(loan, transactionType).getAllocationTypes()
  ▼ …/service/WorkingCapitalLoanAllocationRequestFactory.java:55  this   [via arg 0 this]
    ▼ …/service/WorkingCapitalLoanAllocationRequestFactory.java:60  WorkingCapitalLoanAllocationRequestFactory.<lambda>1()   [via this]
      ▼ …/service/WorkingCapitalLoanAllocationRequestFactory.java:64  WorkingCapitalLoanAllocationRequestFactory.getDefaultAllocationRule(loan)   [via arg 1 loan]
        ▶ …/service/WorkingCapitalLoanAllocationRequestFactory.java:65  return loan.getPaymentAllocationRules().stream().filter(rule -> rule.getTransactionType().isDefault()).findFirst().get();   [via loan]   -> ⇤ throws NoSuchElementException in getDefaultAllocationRule
  ▼ …/data/WorkingCapitalLoanAllocationRequest.java:34  WorkingCapitalLoanAllocationRequest.allocationOrder()   [via arg 5 charges]
    ▼ …/service/WorkingCapitalLoanPaymentAllocationProcessor.java:72  BigDecimal applied = allocateToCharges(request.charges(), runningChargeOutstanding, false, dueType, request.transactionDate(), remaining, chargeAllocations)   [via return of charges()]
      ▼ …/service/WorkingCapitalLoanPaymentAllocationProcessor.java:92  WorkingCapitalLoanPaymentAllocationProcessor.allocateToCharges(charges, runningChargeOutstanding, penalty, dueType, transactionDate, remaining, chargeAllocat...   [via arg 1 charges]
        ▼ ⟳ cycle (45 stmts)  …/service/WorkingCapitalLoanPaymentAllocationProcessor.java:97   [via charges]
          ▶ …/service/WorkingCapitalLoanPaymentAllocationProcessor.java:101  ChargeBalance charge = charges.get(i)   [via guard !MathUtil.isGreaterThanZero(available)]   -> ⇤ throws IndexOutOfBoundsException in allocateToCharges
    ▼ …/service/WorkingCapitalLoanPaymentAllocationProcessor.java:59  for (final WorkingCapitalPaymentAllocationType allocationType : request.allocationOrder()) { if (!MathUtil.isGreaterThanZero(remaining)) { break; } final Due...   [via return of allocationOrder()]
      ▼ ⟳ cycle (53 stmts)  …/service/WorkingCapitalLoanPaymentAllocationProcessor.java:59   [via $iterLocal0]   -> ⇤ throws NoSuchElementException in plan
```

`▶` is a row you could expand; `▼` is expanded. `⇤` is a value leaving the process, `⇥` one
entering it. `⟳` is a cycle, collapsed to one row that says how many statements it holds - the
53-statement one here is the allocation loop, and rendering it as a row is what keeps the tree
finite.

---

## `backward` - where the values reaching this boundary came from

The same failure, read the other way: who can make `getDefaultAllocationRule` throw? Rooted at the
throw, each level moving outwards towards the inputs. Truncated here at twelve of thirty-one rows.

```
$ node spike/src/cli.ts backward spike/out/542/after.json getDefaultAllocationRule

▼ …/service/WorkingCapitalLoanAllocationRequestFactory.java:65  return loan.getPaymentAllocationRules().stream().filter(rule -> rule.getTransactionType().isDefault()).findFirst().get();
  ▼ …/service/WorkingCapitalLoanAllocationRequestFactory.java:64  WorkingCapitalLoanAllocationRequestFactory.getDefaultAllocationRule(loan)   [via loan]
    ▼ …/service/WorkingCapitalLoanAllocationRequestFactory.java:60  WorkingCapitalLoanAllocationRequestFactory.<lambda>1()   [via arg 1 loan]
      ▼ …/service/WorkingCapitalLoanAllocationRequestFactory.java:56  WorkingCapitalLoanAllocationRequestFactory.getAllocationRule(loan, transactionType)   [via loan]
        ▼ …/service/WorkingCapitalLoanAllocationRequestFactory.java:52  getAllocationRule(loan, transactionType).getAllocationTypes()   [via arg 1 loan]
          ▼ …/service/WorkingCapitalLoanAllocationRequestFactory.java:44  WorkingCapitalLoanAllocationRequestFactory.build(loan, balance, charges, transactionDate, amount, transactionType)   [via loan]
            ▼ …/service/WorkingCapitalLoanWritePlatformServiceImpl.java:746  WorkingCapitalLoanAllocationRequest allocationRequest = allocationRequestFactory.build(loan, balance, charges, transactionDate, transactionAmount, transactio...   [via arg 1 loan]
              ▼ …/service/WorkingCapitalLoanWritePlatformServiceImpl.java:740  WorkingCapitalLoanBalance balance = this.balanceRepository.findByWcLoan_Id(loan.getId()).orElseGet(() -> WorkingCapitalLoanBalance.createFor(loan))   [via loan]
                ▼ …/service/WorkingCapitalLoanWritePlatformServiceImpl.java:736  WorkingCapitalLoanTransaction transaction = resolveNewTransaction(transactionType, loan, transactionAmount, paymentDetail, transactionDate, classification, t...   [via loan]
                  ▼ …/service/WorkingCapitalLoanWritePlatformServiceImpl.java:718  this.validator.validateRepayment(command.json(), loan, transactionType)   [via loan]
                    ▶ …/service/WorkingCapitalLoanWritePlatformServiceImpl.java:716  WorkingCapitalLoan loan = this.loanRepository.findById(loanId).orElseThrow(() -> new WorkingCapitalLoanNotFoundException(loanId))   [via loan]   -> ⇥ db:in  WorkingCapitalLoanRepository.findById
      ▼ …/service/WorkingCapitalLoanAllocationRequestFactory.java:55  this   [via this]
   ... 19 more
```

Four database reads reach it, on four different call chains, and one of them arrives through a
field of a service two classes away. That is the answer the design document wanted and the one a
call hierarchy cannot give: `getDefaultAllocationRule` is called from exactly one place.

---

## `dot` - the same slice, drawn

`forward` and `backward` answer their question as a tree, which is a lie of convenience: the slice
is a graph, and a tree has to repeat a node that two routes reach. `dot` prints the graph itself,
in Graphviz, on stdout - `| dot -Tsvg > slice.svg` is the whole workflow, and which format to
render is not this tool's business.

```bash
node spike/src/cli.ts dot forward spike/out/542/after.json \
  org/apache/fineract/portfolio/workingcapitalloan/service/WorkingCapitalLoanAllocationRequestFactory.java:52 \
  | dot -Tsvg > forward.svg
```

27 boxes and 36 edges for the slice whose tree is above. One box per **source line**, not per graph
node: `getAllocationRule(loan, transactionType).getAllocationTypes()` is four nodes and one line,
and drawing it four times fills a column with identical rectangles. A cycle is one box saying how
many statements it stands for. Boundaries are filled by kind - blue `net`, green `db`, amber
`json`, pink `throw` - and the line you asked about is outlined in blue.

```
  "…/WorkingCapitalLoanAllocationRequestFactory.java:52" [label="WorkingCapitalLoanAllocationRequestFactory.java:52\ngetAllocationRule(loan, transactionType).getAllocationTypes()", color="#1d4ed8", penwidth=2.4];
  "…/WorkingCapitalLoanAllocationRequestFactory.java:65" [label="WorkingCapitalLoanAllocationRequestFactory.java:65\nreturn loan.getPaymentAllocationRules().stream().filter(rule -> rule.ge…\nthrows NoSuchElementException in getDefaultAllocationRule", fillcolor="#fee2e2", color="#334155", penwidth=1.6];
  "30064782061" [label="⟳ cycle, 45 stmts\nfor (int i = 0; i < charges.size(); i++)", peripheries=2];
```

Edges carry the carrier, as in the tree; a field write-then-read is dashed and a guard is dotted.
Unlike the tree, every edge between two kept boxes is drawn, not just the ones on a witness route -
the picture is the reason to prefer it.

`backward` works the same way and takes the boundary label:

```bash
node spike/src/cli.ts dot backward spike/out/542/after.json getDefaultAllocationRule | dot -Tsvg > backward.svg
```

29 boxes, 36 edges: five green repository reads converging on one pink throw.

### Why not `joern-export`

Joern ships an exporter, and it does not work on this corpus. On `spike/out/542/after.cpg` every
`--repr` (`ddg`, `cdg`, `pdg`, `cpg14`, `all`) wrote zero files and died:

```
CPG does not have dataflow overlay. Calculating.
ERROR ReachingDefPass Pass io.joern.dataflowengineoss.passes.reachingdef.ReachingDefPass failed
flatgraph.SchemaViolationException: OUT edge with label REF to an adjacent METHOD is mandatory,
  but not defined for this METHOD_REF node with id=120259084455
```

It recomputes the dataflow overlay rather than reading the 80,960 `REACHING_DEF` edges already in
the file, and the recomputation trips over an unresolved `Foo::bar` method reference, of which
Fineract has many. On the six-file `tiny.cpg` it does run, and the DDG comes out empty: 3 edges for
`addCharge` where our own dump reads 34.

Even working, it would be the wrong picture. `joern-export` draws one method at a time; the flows
this tool exists to show cross methods, classes and fields.

## `boundary-diff` - what a commit did to the flows

Three commits, each run before and after. The full reports are regenerated into `spike/out/*.diff`;
these are complete except where marked.

### `542da12e6` - "Simplify allocation rule logic" (1 file, +16 -30)

```
$ node spike/src/cli.ts boundary-diff spike/out/542/before.json spike/out/542/after.json

NEW  throws IndexOutOfBoundsException in allocateToCharges
       from WorkingCapitalLoanRepository.findById
NEW  throws NoSuchElementException in apply
       from WorkingCapitalLoanRepository.findById
NEW  throws NoSuchElementException in getDefaultAllocationRule
       from WorkingCapitalLoanBalanceRepository.findByWcLoan_Id
       from WorkingCapitalLoanChargeRepository.findByLoanIdAndActiveTrueOrderByDueDateAscIdAsc
       from WorkingCapitalLoanRepository.findById
       from WorkingCapitalLoanTransactionRepository.findByWcLoan_IdOrderByTransactionDateAscIdAsc
NEW  throws NoSuchElementException in plan
       from WorkingCapitalLoanRepository.findById
PATH throws IndexOutOfBoundsException in allocateToCharges
       from WorkingCapitalLoanBalanceRepository.findByWcLoan_Id
       from WorkingCapitalLoanChargeRepository.findByLoanIdAndActiveTrueOrderByDueDateAscIdAsc
       from WorkingCapitalLoanTransactionRepository.findByWcLoan_IdOrderByTransactionDateAscIdAsc
PATH throws NoSuchElementException in apply
       from WorkingCapitalLoanBalanceRepository.findByWcLoan_Id
       from WorkingCapitalLoanChargeRepository.findByLoanIdAndActiveTrueOrderByDueDateAscIdAsc
       from WorkingCapitalLoanTransactionRepository.findByWcLoan_IdOrderByTransactionDateAscIdAsc
PATH throws NoSuchElementException in plan
       from WorkingCapitalLoanBalanceRepository.findByWcLoan_Id
       from WorkingCapitalLoanChargeRepository.findByLoanIdAndActiveTrueOrderByDueDateAscIdAsc
       from WorkingCapitalLoanTransactionRepository.findByWcLoan_IdOrderByTransactionDateAscIdAsc
PATH WorkingCapitalLoanTransactionAllocationRepository.saveAndFlush
       from WorkingCapitalLoanBalanceRepository.findByWcLoan_Id
       from WorkingCapitalLoanChargeRepository.findByLoanIdAndActiveTrueOrderByDueDateAscIdAsc

754 pairs: 7 new, 0 gone, 11 same endpoints by different code, 736 unchanged
```

The four `NEW` rows are the commit: `findFirst().get()` is a new way for that method to fail, and
deleting the `DUE_PRINCIPAL` fallback means loan data now reaches the allocation code that used to
be short-circuited, so three more failures became reachable from a loan read. The four `PATH` rows
are the same outputs still reached from the other three reads, now through different code.

### `d602928ae` - "Fix payment allocation rule processing" (14 files)

Two methods move to a new class, and two others are deleted. Truncated to the endpoint verdicts and
the first `PATH`; the full report is 115 lines.

```
GONE throws NoSuchElementException in reapplyChargeAdjustments
       from WorkingCapitalLoanBalanceRepository.findByWcLoan_Id
       from WorkingCapitalLoanChargeRepository.findByLoanIdAndActiveTrueOrderByDueDateAscIdAsc
       from WorkingCapitalLoanTransactionRepository.findByWcLoan_IdOrderByTransactionDateAscIdAsc
GONE WorkingCapitalLoanBalanceRepository.save
       from WorkingCapitalLoanRepository.findById
GONE WorkingCapitalLoanChargeRepository.save
       from WorkingCapitalLoanChargeRepository.findById
NEW  WorkingCapitalLoanChargeRepository.saveAll
       from WorkingCapitalLoanRepository.findById
PATH rethrows in handleDataIntegrityIssues
       from WorkingCapitalLoanChargeRepository.findByLoanIdAndActiveTrueOrderByDueDateAscIdAsc
       from WorkingCapitalLoanRepository.findById
       from WorkingCapitalLoanTransactionRepository.findByWcLoan_IdOrderByTransactionDateAscIdAsc
...
735 pairs: 1 new, 5 gone, 79 same endpoints by different code, 650 unchanged
```

Nothing about the *move* is reported, which is the point of the case: all twelve statements of the
two moved methods fingerprint identically on both sides, so every flow through them is `SAME`. Every
row names one of the two deletions.

### `745ae67de` - "Enforce Charge Rounding using Money inMultiplesOf settings" (8 files, 4 modules)

Corpus: `fineract-loan`, `fineract-savings` and three packages of `fineract-provider` staged into
one tree - 1190 files, 254k graph nodes. Truncated to the `NEW` rows; the full report is 8567 lines.

```
NEW  throws LoanChargeCannotBeAddedException in validateChargeAmountNotZero
       from LoanChargeRepository.findById
       from LoanRepository.findByExternalId
       from LoanRepository.findById
       from LoanRepository.findLoanByClientId
       from LoanTermVariationsRepository.findByIdAndLoanIdAndTermType
       from LoanTransactionRepository.findLastTransactionDateForReprocessing
NEW  throws PlatformApiDataValidationException in validateChargeAmountNotZero
       from AccountAssociationsRepository.findBySavingsIdAndType
       from CalendarInstanceRepository.findByEntityIdAndEntityTypeIdAndCalendarTypeId
       from ClientRepository.findById
       from DepositAccountOnHoldTransactionRepository.findBySavingsAccountAndReversedFalseOrderByCreatedDateAsc
       from FixedDepositProductRepository.findById
       from GET /v1/internal/client/{clientId}/audit
       from GroupRepository.findById
       from InterestRateChartRepository.findById
       from json accountingRule
       from json applicationId
       from json assignmentDate
       from json fromSavingsOfficerId
       from json groupId
       from json isGSIM
       from json isParentAccount
       from json isPostInterestAsOn
       from json lastApplication
       from json note
       from json toSavingsOfficerId
       from json transactionAmount
       from json transactionDate
       from json unassignedDate
...
9498 pairs: 42 new, 0 gone, 8138 same endpoints by different code, 1318 unchanged
```

Two new failures, out of 9498 pairs over 1190 files, and both are the validation the commit added.
The savings site is absent and correctly so: that one adds an `ApiParameterError` to a list rather
than throwing, so no value leaves the process there.

The 8138 `PATH` rows are the honest cost of a change to code that most requests run through. See
below.

---

## What `PATH` compares, and what changed

`PATH` means "the same input still reaches the same output, but through different code". It used to
be decided by comparing one shortest witness path per pair, and a witness is a fact about the walk,
not about the pair: two builds of the same program pick different equally-short routes, and the diff
reports the difference as a finding. Measured against the commits, it was noise - all 43 rows on the
single-file commit ran through no file the commit had touched.

It is now decided by the pair's **interior**: every statement on *any* route from the source to the
sink. A node lies on some route exactly when it is reachable forwards from the source and backwards
from the sink, so the interior is one intersection of two breadth-first walks - no path enumeration,
of which there are usually infinitely many. Each component contributes the fingerprints of all its
members, so a 45-statement cycle contributes 45 statements, and the set is folded to an
order-independent name (`setName` in `fingerprint.ts`) rather than kept.

Graded by asking a question the tool never sees the answer to - does this pair's interior run
through a file the commit actually edited? (`spike/precision.ts`):

| commit | | `PATH` | crosses a changed file | differs in one |
|---|---|---|---|---|
| `542da12e6` (1 file) | witness | 43 | 0 | - |
| | **interior** | **11** | **11** | **11** |
| `d602928ae` (14 files) | witness | 66 | 43 | - |
| | **interior** | **79** | **79** | **79** |
| `745ae67de` (8 files) | witness | 3029 | 2021 | - |
| | **interior** | **8138** | **8138** | **8138** |

The last column is the strong bar: the two interiors differ *in a statement of a file the commit
edited*, which is the row's own claim restricted to code the author wrote. Every row clears it. The
`NEW` and `GONE` verdicts are unchanged by this work and remain 55 for 55.

Two things the numbers say that the table's precision does not:

- **`PATH` is precise but not selective.** On `745ae67de` it fires on 8138 of 9498 pairs, and each
  one is true: the commit put a new validation inside charge handling, and most requests to these
  four modules run through charge handling. A report of 8138 rows is a report nobody reads. What is
  missing is not precision but *what changed inside* - the tool keeps a hash of the interior, not
  the statements, so it can say the code between two boundaries moved but not which code. Keeping
  the differing fingerprints for the rows that differ is the obvious next step.
- **It costs.** The diff on the 254k-node corpus went from 10 s to 2 min 4 s, because it now walks
  backwards from every sink component as well as forwards from every source. On the 46k-node module
  it is 1.2 s either way. Still under the CPG build, and both sides of a PR can be built in parallel.
