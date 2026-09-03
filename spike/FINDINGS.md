# Boundary diff: what the spike found

Three Fineract commits, run before and after, scored against what the plan predicted each would
produce. The bar the plan set was **two of three clean**.

**Verdict: go.**

All three commits produced the finding they were chosen for, and across the three, all 55 `NEW` and
`GONE` rows crossed a file the commit actually changed - no false positives at the level a reviewer
reads. `PATH`, the verdict the design document is proudest of, scored zero as first built and was
then rebuilt; it is now exact too, on a stricter bar than the endpoint verdicts are held to, and its
remaining problem is volume rather than truth. Both halves of that were settled by measurement
rather than by taste. Worked examples of every command are in EXAMPLES.md.

---

## The three commits

The blocks below are the `NEW` and `GONE` rows of each report. The `PATH` rows are left out here to
keep the cases readable and because they are the subject of their own section further down; the full
reports, and everything else the tool prints, are in EXAMPLES.md.

### 1. `542da12e6` - "Simplify allocation rule logic"

One file, 16 lines added and 30 removed. `getDefaultAllocationRule` ends `...findFirst().orElse(null)`
before and `...findFirst().get()` after, and the `List.of(DUE_PRINCIPAL)` fallback that used to
stand in when no rule matched is deleted.

Corpus: `fineract-working-capital-loan/src/main/java`, 316 files.

```
NEW  throws NoSuchElementException in getDefaultAllocationRule
       from WorkingCapitalLoanBalanceRepository.findByWcLoan_Id
       from WorkingCapitalLoanChargeRepository.findByLoanIdAndActiveTrueOrderByDueDateAscIdAsc
       from WorkingCapitalLoanRepository.findById
       from WorkingCapitalLoanTransactionRepository.findByWcLoan_IdOrderByTransactionDateAscIdAsc
NEW  throws NoSuchElementException in apply
NEW  throws NoSuchElementException in plan
NEW  throws IndexOutOfBoundsException in allocateToCharges

754 pairs: 7 new, 0 gone, 11 same endpoints by different code, 736 unchanged
```

The predicted finding is the first row, and it is there, named at the method the commit changed and
reached from four separate reads. The prediction also said the `DUE_PRINCIPAL` fallback would show
as `GONE`, and it does not - correctly. A `List.of(DUE_PRINCIPAL)` is a constant, not a boundary,
and nothing enters or leaves the process there. What its removal did show up as is the other three
rows: with the constant gone, loan data now reaches the allocation order, so a loan read now reaches
three failures in the allocation code that it could not reach before. That is a better answer than
the one predicted, and not one that reading the diff gives you.

**Clean.**

### 2. `d602928ae` - "Fix payment allocation rule processing"

Fourteen files. Two methods move out of `WorkingCapitalLoanWritePlatformServiceImpl` into a new
`WorkingCapitalLoanTransactionProcessor` without a line of either body changing; separately,
`reapplyChargeAdjustments` and `WorkingCapitalLoanBalanceUpdater.applyChargePayment` are deleted.

Same corpus.

```
GONE throws NoSuchElementException in reapplyChargeAdjustments
       from WorkingCapitalLoanBalanceRepository.findByWcLoan_Id
       from WorkingCapitalLoanChargeRepository.findByLoanIdAndActiveTrueOrderByDueDateAscIdAsc
       from WorkingCapitalLoanTransactionRepository.findByWcLoan_IdOrderByTransactionDateAscIdAsc
GONE WorkingCapitalLoanBalanceRepository.save
GONE WorkingCapitalLoanChargeRepository.save
NEW  WorkingCapitalLoanChargeRepository.saveAll

735 pairs: 1 new, 5 gone, 79 same endpoints by different code, 650 unchanged
```

The plan predicted an identical pair set on both sides, "because the change is a method move". The
plan was wrong about the commit: it is a move *and* two deletions, and every row above names one of
the deletions. The property the case was really testing - that a method moving to another class
produces no rows - holds, and was checked directly: of `triggerInlineAmortizationIfLoanClosed`'s nine
statements and `isBackdatedTransaction`'s three, every one fingerprints identically on both sides,
and neither method appears in any row.

That check initially failed on exactly one statement in each method, the signature row, because
`load.ts` synthesizes its text as `Class.method(a, b)` so the tree reads like a call hierarchy - and
the class is the one thing a fingerprint must not carry. Fixed; see below.

**Clean.**

### 3. `745ae67de` - "Enforce Charge Rounding using Money inMultiplesOf settings"

Eight source files across four modules, each gaining a `validateChargeAmountNotZero`.

Corpus: `fineract-loan`, `fineract-savings`, and the `client`, `shareaccounts` and `savings`
packages of `fineract-provider`, staged into one source tree. 1190 files.

```
NEW  throws LoanChargeCannotBeAddedException in validateChargeAmountNotZero
       from LoanChargeRepository.findById
       from LoanRepository.findByExternalId
       ... 4 more
NEW  throws PlatformApiDataValidationException in validateChargeAmountNotZero
       from AccountAssociationsRepository.findBySavingsIdAndType
       ... 35 more

9498 pairs: 42 new, 0 gone, 8138 same endpoints by different code, 1318 unchanged
```

Every one of the 42 new pairs is the new validation, and nothing else is reported, out of 9498 pairs
over 1190 files. The rows group by module in the sense the plan meant: the loan module's new failure
is a `LoanChargeCannotBeAddedException` and the rest are `PlatformApiDataValidationException`s, and
the inputs beneath each say which module's repositories reach it.

The savings site is missing from the list, and correctly: that one adds an `ApiParameterError` to a
list rather than throwing, so no value leaves the process there.

**Clean.**

---

## What was hand-edited to get there

Nothing in the outputs above was edited. Five changes were made to the tool while running the cases,
each committed separately with its reason. In spike terms these are the findings: every one is
something the hand-written fixture could not have told us.

1. **`orElseThrow` and `get` on a receiver Joern could not type.** The CPG is built no-delombok
   (Joern's delombok modes deadlock on Fineract, and the one that works reports line numbers from the
   delomboked copy). A Lombok `@Getter` therefore has no source, arrives as `<unresolvedSignature>`,
   and everything chained off it is typed `<unresolvedNamespace>`. A boundary rule keyed on the
   receiver's type had no type to read, so the headline case's `findFirst().get()` was not a boundary
   at all - and neither were all 44 `findById(..).orElseThrow(..)` sites in the module, which is the
   commonest way Fineract fails. `orElseThrow` needs no type: no other common class has that method.
   For `get`, which `Map`, `List` and `JsonObject` also have, the call that produced the receiver
   stands in for the type.

2. **A throw is named by where it escapes.** With the boundary finally found, the diff still reported
   nothing: a throw was labelled by exception type alone, `NoSuchElementException` names a hundred
   places in this corpus, and a brand new way for a method to fail matched a pair that already
   existed. Throw labels now carry the enclosing method's simple name - simple, so a method that
   moves file or class still matches itself.

3. **The signature row's class prefix.** See case 2 above.

4. **Deterministic witness choice, and then the interior.** Each pair keeps one witness path, and it
   was whichever equally short one the walk happened to reach first. Two sides then disagreed about
   which of a validator's six calls to show, and the pair read as `PATH`. Ties now break on the
   witness text, which removed nine of 75 `PATH` rows on case 2 - and made it obvious that the rest
   were the same problem in a form no tiebreak can fix. The verdict no longer depends on the witness
   at all; see below. The tiebreak stays, because the line a reviewer reads still does.

5. **The report groups by what changed.** Case 3 flat is 42 rows: two new failures, once per input
   that reaches them, and a reader has to read all 42 to learn there are two.

---

## What did not work first time: `PATH`

The design document's argument for `PATH` is sound - "the same input still reaches the same output,
but through different code" is a real thing to know, and no which-flows-exist comparison can say it.
The first implementation could not support it. It compared one shortest witness per pair, and one
path through a program this size is a fact about the walk, not about the pair: two builds of the
same program pick different equally-short routes and the diff reports the difference as a finding.

`PATH` is now decided by the pair's **interior** - every statement on *any* route from the source to
the sink. A node lies on some route exactly when it is reachable forwards from the source and
backwards from the sink, so the interior is the intersection of two breadth-first walks, computed
without enumerating paths, of which there are usually infinitely many. Every member of a condensed
component counts, so a 45-statement cycle contributes 45 statements, and the set is folded to an
order-independent name rather than kept: 9498 pairs times a few thousand fingerprints does not fit.

Measured by asking whether each row's flow runs through a file the commit touched (`precision.ts`):

| commit | | `PATH` | crossing a changed file | differing in one |
|---|---|---|---|---|
| `542da12e6` (1 file changed) | witness | 43 | 0 | - |
| | **interior** | **11** | **11** | **11** |
| `d602928ae` (14 files) | witness | 66 | 43 | - |
| | **interior** | **79** | **79** | **79** |
| `745ae67de` (8 files) | witness | 3029 | 2021 | - |
| | **interior** | **8138** | **8138** | **8138** |

The last column is the strong bar - the two interiors differ in a statement of a file the author
actually edited - and every row clears it. The 55 `NEW` and `GONE` rows are unaffected by the change
and remain 55 for 55.

What is left is not precision but selectivity. On `745ae67de` `PATH` fires on 8138 of 9498 pairs,
every one of them true: a new validation inside charge handling is genuinely on the way from most of
these modules' inputs to most of their outputs. A report of 8138 rows is a report nobody reads. The
missing half is *what* changed inside - the pair keeps a hash of its interior, not the statements,
so it can say the code between two boundaries moved but not which code. Keeping the differing
fingerprints for the rows that differ, and grouping the report by those instead of by sink, is the
next thing to do.

## What else is now known

- **Cost is not a problem.** `javasrc2cpg` takes 15 s on 316 files and 27 s on 1190; the Joern dump
  takes 8 s and 13 s; the whole TypeScript pipeline, parse to report, is 1.2 s on 46k nodes and
  2 min 4 s on 254k. The large one is all interior: a backward walk per sink component on top of a
  forward walk per input, where the witness version needed only the forward walks and took 10 s. A
  PR check on a module is still under a minute, most of it CPG construction, and both sides can be
  built in parallel.
- **The graph JSON is large** - 186 MB for 254k nodes - and needs `--max-old-space-size`. It is a
  debugging convenience, not a design commitment; the dumper could emit only what `load.ts` reads.
- **Multi-module corpora need staging.** `javasrc2cpg` takes one source root, so case 3 copies each
  module's `src/main/java` into one tree. This is what the compiler's classpath does anyway, and it
  resolved fine.
- **Boundary counts, per module** (`542da12e6` after): 280 throws, 205 repository reads, 94 writes,
  161 endpoint inputs, 86 endpoint outputs, 5 JSON fields. Every rule fires; none is dead weight.
- **Cycles are real and common.** The largest condensed component in the working-capital module holds
  45 statements. Rendering it as one row is what keeps the tree finite.
