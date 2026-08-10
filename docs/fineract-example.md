# The Fineract example

codeflow's standing test case on real input: **`ProgressiveEMICalculator` in Apache Fineract**, rooted
at `calculateRateFactorPerPeriodForInterest`. It is the corpus every measurement in
[`externals.md`](externals.md) was taken on, and the reason `switch` expressions, block arms and
`yield` are modelled at all.

It was chosen because it is a case where **reading the source is not enough and grep actively
misleads**, which is the only kind of case a dataflow diagram can justify itself on. Everything below
was re-measured on 2026-08-09; the commands that produce each number are in
[Running it](#running-it).

---

## The code

Apache Fineract, `origin https://github.com/apache/fineract.git`, at `de17f9632` (2026-07-15).

```
fineract-progressive-loan/src/main/java/org/apache/fineract/portfolio/loanproduct/calc/ProgressiveEMICalculator.java
```

Two methods in that file are near-identical text, and both are live:

| | line | called from | sets |
|---|---|---|---|
| `calculateRateFactorPerPeriod` | 1486 | 639 | `interestPeriod.rateFactor` |
| `calculateRateFactorPerPeriodForInterest` | 1355 | 641 | `interestPeriod.rateFactorTillPeriodDueDate` |

They are invoked **two lines apart**, inside the same lambda:

```java
// ProgressiveEMICalculator.java:636
public void calculateRateFactorForRepaymentPeriod(final RepaymentPeriod repaymentPeriod,
        final ProgressiveLoanInterestScheduleModel scheduleModel) {
    repaymentPeriod.getInterestPeriods().forEach(interestPeriod -> {
        interestPeriod.setRateFactor(calculateRateFactorPerPeriod(scheduleModel, repaymentPeriod,
                interestPeriod.getFromDate(), interestPeriod.getDueDate()));
        interestPeriod.setRateFactorTillPeriodDueDate(calculateRateFactorPerPeriodForInterest(scheduleModel,
                repaymentPeriod, interestPeriod.getFromDate(), repaymentPeriod.getDueDate()));
    });
}
```

Both declare the same locals, run the same five-way guard cascade, and end in the same helper chain.
What differs between them, in full: one local is renamed (`calculatedDaysInPeriod` /
`calculatedDaysInRepaymentPeriod`), two declarations are reordered, the sibling has an extra
`daysInMonth` local (1508), the final dispatch is written as `if`/`else if` in one and as a `switch`
expression in the other — and **two arguments at one call**. Everything on that list is cosmetic
except the last item, and the last item is the entire fix.

### Where it came from

Commit [`0b9687d2a`](https://github.com/apache/fineract/commit/0b9687d2a), *"FINERACT-2081: Fix end of
month and partial interest calculation for 360/30"*, Adam Saghy, 2025-01-27 — 152 lines changed in
the calculator, 500 in its test.

```shell
git -C <fineract> show 0b9687d2a -- '*ProgressiveEMICalculator.java'
```

The commit **forked** `calculateRateFactorPerPeriod` into `calculateRateFactorPerPeriodForInterest`
and added two new helpers, `calculatePeriodRatio` (1419) and `calculateSeedDate` (1452). It did not
delete the original — which is what makes this a live comparison rather than a historical one.

JIRA: <https://issues.apache.org/jira/browse/FINERACT-2081>. Be warned that in this repository the
key is used as an umbrella prefix on **140 commits** across unrelated areas, so the ticket is not a
description of this change; the commit is.

### What the difference actually is

Both methods end at the same call. The fixed one, at line 1412:

```java
BigDecimal periodRatio = switch (repaymentFrequency) {         // 1404
    case YEARS  -> calculatePeriodRatio(scheduleModel, repaymentPeriod, ChronoUnit.YEARS, mc);
    case MONTHS -> calculatePeriodRatio(scheduleModel, repaymentPeriod, ChronoUnit.MONTHS, mc);
    case WEEKS  -> calculatePeriodRatio(scheduleModel, repaymentPeriod, ChronoUnit.WEEKS, mc);
    case DAYS   -> calculatePeriodRatio(scheduleModel, repaymentPeriod, ChronoUnit.DAYS, mc);
    default     -> throw new UnsupportedOperationException(...);
};
return calculateRateFactorPerPeriodBasedOnRepaymentFrequency(interestRate, repaymentFrequency, periodRatio,
        BigDecimal.valueOf(30), daysInYear, actualDaysInPeriod, calculatedDaysInPeriod, mc);
```

The sibling, at line 1536:

```java
case DAYS_30 -> calculateRateFactorPerPeriodBasedOnRepaymentFrequency(interestRate, repaymentFrequency, repaymentEvery,
        daysInMonth, daysInYear, actualDaysInPeriod, calculatedDaysInRepaymentPeriod, mc);
```

One slot moves:

| slot | parameter name (1598) | `…ForInterest` passes | `…PerPeriod` passes |
|---|---|---|---|
| 3 | `repaymentEvery` | **`periodRatio`** (1404, computed) | **`repaymentEvery`** (1495, `getRepayEvery()`) |
| 4 | `daysInMonth` | `BigDecimal.valueOf(30)` | `daysInMonth` (1508 — also 30 on this path) |

Slot 4 is the same value written two ways. Slot 3 is the fix: a *computed fractional period count*
that accounts for end-of-month and partial periods, in place of the product's configured repayment
interval.

### Why reading does not find it

The value then passes through three more methods, and **the parameter it lands in is named
`repaymentEvery` in every one of them**:

```
calculateRateFactorPerPeriodBasedOnRepaymentFrequency(…, BigDecimal repaymentEvery, …)   1598
  └─ MONTHS → rateFactorByRepaymentEveryMonth(…, repaymentEvery, daysInMonth, …)         1607/1922
       └─ rateFactorByRepaymentPeriod(interestRate, daysInMonth, repaymentEvery, …)      1925/1950
            └─ interestFractionPerPeriod = repaymentPeriodMultiplierInDays               1956
                                             .multiply(repaymentEvery, mc)
                                             .divide(daysInYear, mc);
```

So the arithmetic reads `repaymentPeriodMultiplierInDays × repaymentEvery ÷ daysInYear` and all three
names are wrong about what is flowing. What actually runs on the fixed path is
`30 × periodRatio ÷ daysInYear`. Note also the swap at 1925: `daysInMonth` is passed into a parameter
called `repaymentPeriodMultiplierInDays`.

Grepping `periodRatio` finds two lines. Grepping `repaymentEvery` finds it in both methods and in
every helper, and cannot tell you which occurrences carry the ratio.

---

## Running it

Requires JDK **25** at runtime — see *Two JDK numbers* in `CLAUDE.md`. `./gradlew run` pins it
already; the installed CLI takes whatever `JAVA_HOME` names.

```shell
FINERACT=$HOME/Documents/Projetos/fineract
MODULE=$FINERACT/fineract-progressive-loan/src/main/java
ROOT='ProgressiveEMICalculator#calculateRateFactorPerPeriodForInterest'

# the interactive page — this is the one to actually look at
./gradlew -q run --args="$MODULE --html --from $ROOT" > /tmp/emi.html

# the payload the tests and any scripted analysis read
./gradlew -q run --args="$MODULE --json --from $ROOT" > /tmp/emi.json

# the sibling, for comparison
./gradlew -q run --args="$MODULE --json --from ProgressiveEMICalculator#calculateRateFactorPerPeriod" > /tmp/emi-old.json
```

Two things to expect:

- **stderr carries the summary; stdout is the document.** `codeflow: 23311 of 40457 references
  unresolved (57.6%)` is normal for one module of a multi-module build — see Finding 3 in
  `externals.md`.
- **The `ForInterest` root exits non-zero.** `codeflow: 1 construct not modelled — TYPE_CAST at
  ProgressiveEMICalculator.java:1428:42`. That is the `(LocalDate)` in
  `((LocalDate) TemporalAdjusters.lastDayOfMonth().adjustInto(...)).getDayOfMonth()` — inside
  `calculatePeriodRatio`, i.e. inside the fix itself. The document is written in full regardless.
  The sibling root exits 0. The count is deduplicated by position while the graph is not, so *one*
  construct on stderr is *four* `UNMODELLED` nodes below — `calculatePeriodRatio` is inlined at four
  call sites.

`--html` writes ~2 MB (each page inlines its own copy of the vendored libraries). Opening it over
`file://` is fine; a headless browser will need `python3 -m http.server`.

### Lombok

Fineract is Lombok-annotated and codeflow parses with `-proc:none`, so accessors do not exist in the
attributed tree. `RepaymentPeriod` declares `getFromDate`/`getDueDate` as `@Getter`, and those two are
**54 opaque nodes** in this graph; `getFromDate` alone is 31, the most common label in it after `==`.
For a run where they resolve:

```shell
scripts/delombok.sh "$MODULE" /tmp/delomboked
./gradlew -q run --args="/tmp/delomboked --html --from $ROOT" > /tmp/emi-delombok.html
```

The trade is that every `file:line` afterwards names the delomboked copy — `1428:42` becomes
`1075:42`. Finding 4 in `externals.md` measures both halves.

---

## What the graph says

Rooted at `…ForInterest`, one module:

```
916 nodes (883 leaves + 33 method boxes), 1159 edges

EXTERNAL 365 (41%)   BIN_OP 164   FUNC_PARAM 124   OBJ_VARIABLE 79
VARIABLE 57          LITERAL 57   RETURN 33        UNMODELLED 4

edges: FLOW 1059   CONDITION 40   TRUE 30   FALSE 30
```

The sibling root is **408 nodes / 508 edges** — the fix more than doubled the graph, which is itself
the first honest signal that the two methods are not as alike as they read.

**Methods inlined more than once**, which is the per-call-site inlining doing its job:

| times | method |
|---|---|
| 6 | `rateFactorByRepaymentPeriod` |
| 4 | `calculatePeriodRatio`, `calculateSeedDate`, `getStartDate` |
| 2 | `isPeriodContainsFeb29` |

Six copies of `rateFactorByRepaymentPeriod`, each with different arguments in the same seven slots, is
precisely the thing a reader cannot hold in their head — and precisely what the source cannot show.

**The trace**, forward from `periodRatio` (this is `/tmp/emi.json` walked by out-edges):

```
periodRatio :OBJ_VARIABLE                                   [1404:13]
  repaymentEvery :FUNC_PARAM                                [1599:59]  ← named repaymentEvery from here on
    repaymentEvery :FUNC_PARAM                              [1870:85]  (DAYS arm)
      repaymentEvery :FUNC_PARAM                            [1951:13]
        multiply :EXTERNAL                                  [1956:54]
          divide :EXTERNAL                                  [1956:54]
            interestFractionPerPeriod :OBJ_VARIABLE         [1956:9]
              multiply :EXTERNAL → multiply → divide        [1959:16]
    repaymentEvery :FUNC_PARAM                              [1895:86]  (WEEKS arm) → … same tail
    repaymentEvery :FUNC_PARAM                              [1922:79]  (MONTHS arm) → … same tail
```

That is the answer the source will not give you, and it took no reading of the file to get.

---

## What the page does today

Measured on the React Flow page at the opening view, viewport 1600×1000:

| | opening | after one click on `periodRatio` |
|---|---|---|
| nodes drawn | 125 — 110 leaves showing (14 of them stubs) + 15 method boxes | 150 |
| edges drawn | 101 | — |
| ELK layout extent | 4110 × 1241 px | 4792 × 1590 px |
| zoom to fit | **0.28** | **0.30** |

Clicking everything on screen to a fixpoint reaches **883 of 883 leaves in 4 rounds** — the whole
graph is reachable, and the whole graph is also 883 nodes on one canvas.

Badges work and are carrying real weight: the opening view shows `scheduleModel ↓5`,
`mc ↓10`, `interestRate ↓5`, `ONE ↓9` — a reader is told what is missing rather than shown a false
sink.

**And then the one measurement that matters.** The question this example exists to answer is *where
does `periodRatio` end up*. The arithmetic is **4 undirected hops** away. `REVEAL_DEPTH` is **3**. So:

> Clicking `periodRatio` puts **8 boxes labelled `repaymentEvery`** on the screen and **zero
> `multiply`**.

One click reveals exactly the misleading half of the trace and stops one hop short of the point. That
is not a tuning problem — raising the depth to 4 would reveal ~200 more nodes and bury it differently.
It is the wrong *shape* of reveal for this question.

---

## What a good visualization would look like

Ranked by how much of the gap each closes on this example. Nothing here is implemented.

### 1. A directed trace, not an undirected ball

`neighbourhood(edges, startId, depth)` is an undirected ball of radius 3, and that is right for
*exploring* — for `c = a + b`, clicking `a` should show `b`. It is wrong for *tracing*, which is
directed and unbounded.

**What the reader should get:** shift-click a node (or `--trace Class#method:name`) → the forward
transitive cone to its sinks, everything else dimmed rather than hidden. Dimming rather than hiding is
the point: the cone read against the rest of the method is what says "and nothing else touches this".

```
periodRatio ──▶ repaymentEvery ──▶ repaymentEvery ──▶ repaymentEvery ──▶ × ──▶ ÷ ──▶ interestFraction… ──▶ × ──▶ ÷
                (1599)             (1870/1895/1922)   (1951)
```

**The two directions are not the same size, and this is the design decision.** Measured on this
payload:

| cone from `periodRatio` | nodes |
|---|---|
| forward (where does it go) | **45** — 9 `multiply`, 7 `repaymentEvery`, 6 `divide`, 3 `interestFractionPerPeriod`, 3 `setScale`, across 3 arms |
| backward (what fed it) | **324** |
| union | 368 of 883 (42%) |

45 is a screenful at legible zoom and is the whole answer, three arms and all. 324 is a third of the
graph — every date computation that ever touched a `RepaymentPeriod` — and dimming everything *else*
would dim almost nothing. So a trace should default to **forward only**, with the backward cone on a
second gesture. Symmetric two-colour highlighting reads well in the abstract and would be useless
here.

**What is missing:** `model.ts` has no directed walk and `screen()` has no third state between visible
and hidden. Both are additive — a `cone(edges, startId, direction)` beside `neighbourhood`, and a
`dimmed` flag beside `visible`. The renderers already read `visible` per node and per edge.

### 2. Draw the arithmetic as arithmetic

**77 of the 365 `EXTERNAL` nodes are `BigDecimal` operations** — `valueOf` 25, `multiply` 21, `divide`
19, `setScale` 7, `add` 5. The formula the whole bug is about renders as five featureless orange
boxes, while the 164 `BIN_OP` nodes codeflow *can* draw are `+` and `>` on loop counters. The tool's
best rendering is spent on the least interesting arithmetic and its "I cannot see inside" rendering on
the most interesting.

**What the reader should get:** `30 × periodRatio ÷ daysInYear`, readable as a formula.

**What is missing:** the operator table from `externals.md` §1 — keyed on the resolved
`ExecutableElement`'s owner, never on the receiver's spelling — plus its own shade, because "codeflow
was *told* what this operator means" is a different claim from "codeflow read this operator".

### 3. Six call sites, side by side

`rateFactorByRepaymentPeriod` is drawn six times, in six boxes, scattered by the layout. The reader's
question is not "what is in the box" but "what is in **slot 2** across all six".

**What the reader should get:** select a method drawn N times → a table, one row per call site, one
column per parameter, cells naming the value that arrives. Clicking a cell traces it.

**What is missing:** all of it, but the data is already there — the payload's `source` positions
distinguish the call sites, and `PosStack` distinguishes the frames.

### 4. Diff two roots

The finding in this document came from running codeflow twice and comparing by hand (916 nodes vs
408). A forked-and-fixed method beside its sibling is one of the commonest shapes a real bug takes.

**What the reader should get:** two roots, one page, shared nodes grey and the divergence coloured —
which for this example is one slot at one call.

**What is missing:** the normalisation already exists in the test discipline (sorted multisets of
`label:TYPE` nodes and `label:TYPE -> label:TYPE` edges, ids stripped). What is absent is joining two
payloads on it and rendering the result.

### 5. Drop the rounding policy

`mc` is **20 nodes and 74 edges — 6.4% of every edge in the graph** — carrying `MathContext` into
every arithmetic call. A rounding policy is not an operand.

**What the reader should get:** nothing. That is the point.

**What is missing:** this is the same table as §2 — an operator's trailing `MathContext` is dropped
where the operator is declared.

### 6. Aspect ratio

`elk.direction` left-to-right over a call chain this deep produces 4110 × 1241, so fitting the graph
means 0.28 zoom and no legible label. The reader either reads labels or sees the shape, never both.

**What is missing:** less clear than the others, and worth a measurement before a change.
`elk.aspectRatio`, or `elk.direction: DOWN` for the outer method boxes with layers inside them, are
the two cheap things to try. `ELK_OPTIONS` is applied to the root and to every box, so a change there
moves the whole page at once.

---

## What already works, and should not be rebuilt

Worth stating, because on this corpus these are the parts that carry it:

- **Per-call-site inlining.** `rateFactorByRepaymentPeriod` ×6 with different arguments is the whole
  finding. A method summary would have collapsed it.
- **Gated joins.** 100 non-`FLOW` edges — 40 `CONDITION`, 30 `TRUE`, 30 `FALSE`. The five-way guard
  cascade in each method is on the page with the deciding value attached.
- **`switch` expressions with block arms.** `periodRatio` *is* a `switch` expression, and
  `calculatePeriodRatio` contains another one with a `yield` in a block arm. None of this was modelled
  until this file asked for it; before that the arms were drawn `UNMODELLED`.
- **Hidden-neighbour badges.** At 0.28 zoom over 883 nodes, `↓10` on `mc` is the difference between a
  partial view and a partial view that admits it.
- **`UNMODELLED` and the non-zero exit.** One `(int)`-shaped cast, named with a file and a line, and
  the document still written. The alternative — the old behaviour — was zero bytes for the corpus.

---

## Provenance

Every number here was produced on 2026-08-09 by the commands in [Running it](#running-it), against
Fineract `de17f9632` and codeflow at `6ddcb5e`, on javac 25. Node/edge counts and label breakdowns
come from `/tmp/emi.json`; the page measurements come from the React Flow page under Playwright at
1600×1000, reading `.react-flow__node` out of the DOM rather than the `view` object it publishes. The
hop distance is a breadth-first walk of the payload's edges taken as undirected, which is what
`neighbourhood` walks.

The graph totals match `externals.md` exactly (883 leaves, 365 `EXTERNAL`), which is what says the two
documents are measuring the same run.
