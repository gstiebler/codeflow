# Method summaries

Phase 1 of Plan B (`docs/superpowers/specs/2026-11-15-method-summaries-design.md`). The builder
answers "what reaches what" again at every call site; `ir.Summarizer` answers it once per method,
and `--summaries` writes every method's answer as one JSON document. Nothing else reads a summary
yet: the graph, `--json`, `--html` and the goldens are unchanged.

## A summary is the builder's answer, said once

`Summarizer` walks the same `MethodBody.instructions` the builder draws, borrowing its lowering
cache (`bodyOf`, `initializersOf`), and its rules are the builder's: whatever edge the builder draws
is a flow here. A summary says which **inputs** reach which **outputs**:

- `ret`: what the result is made of, and which objects it can be.
- `effects`: the final value of every field written on an object the caller can reach.
- `sinks`: what flowed into each opaque call — the `EXTERNAL` of the builder.
- `gaps`: where it is incomplete, and why.

Why once per method: inlining costs grow with call sites, not with source size. Fineract
progressive-loan's 658 roots are 2.24M nodes and 742 MB of JSON. A summary makes every method a
root at no extra cost, and recursion a fixpoint rather than an `EXTERNAL` cut.

A call to a source method **instantiates** the callee's summary at the call site, never its body:
each `P<i>` becomes the argument's value, `this` the receiver's objects, and an object the callee
allocated is renamed by the call site. So `f(a)` and `f(b)` stay apart, as inlining keeps them.
Dispatch is the builder's own rule, shared in `ir.Dispatch` so the two interpreters cannot drift.

The builder's known imprecisions are kept, not fixed here (`joins.md`): no back edge for a field
in a loop, `?:`/`&&`/`||` not forked, and a strong update on each of several holders — the last
also said, with an `alias` gap, since it drops the other objects' old values. A field written on
only some paths joins with its entry value, because the caller may already hold one.
A summary that quietly did better than the builder would be a second truth; the parity test is
where the two are held together.

**Fixpoint.** Summaries are solved by a worklist: a run reads its callees' summaries as they
stand, and runs again when one changes. A recursive call reads its own approximation, which starts
empty. The budget is 20 runs per key and 20 passes per loop. Over budget keeps the last
approximation and records a `fixpoint` gap — at the method declaration, or at the loop's first
back-edge phi.

## Atoms

Spelled from `Element`s, never from text. `<qfield>` is the declaring class's qualified name, a
`.`, then the field name: `pkg.Loan.rate`.

| Inputs | Outputs |
|---|---|
| `P<i>` | `RET` |
| `this.<qfield>[.<qfield>]`, `P<i>.<qfield>…`, `static.<qfield>` | the same field spellings, and `RET.<qfield>[.<qfield>]` |
| `ext:<label>@<file:line:col>` — an opaque call's result | `ext-arg:<label>@<file:line:col>` — its arguments and receiver |

- A field path stops at **2** fields. Deeper is a `depth` gap, not a longer name.
- An array's length is a field of the array: `P0.Array.length`.
- A field of an object the method made is spelled through the caller's field that holds it:
  `this.A.b.B.v` for `this.b = x; x.v = a` — or through the result, `RET.B.v` for
  `x.v = a; return x`. Without `RET` a factory's document listed `ext:B>RET` alone and sent its
  parameter nowhere, though instantiation, which reads the objects, had it right.
- An allocated object is named by its chain of call sites. Past 2 it keeps the **outermost call
  site and the allocation site**, and drops the ones between. Keeping the 2 newest lost the
  allocation site and merged distinct objects; the outermost keeps two calls in this method apart.
- A call site in a chain is its position **and its instruction's index**. A call's position is
  where its tree starts, so `m.with(a).with(w)` is two calls at one position; named by that alone,
  the two `new M()` were one object, the second write of `v` overwrote the first, and the summary
  said `P2>RET` for the builder's `P1>RET`, with no gap.

## Gaps are named, not guessed

A summary that drops a flow without a sign is the silently wrong graph again. So each place a
summary cannot follow becomes a gap with a kind, a `file:line:col` and a detail.

- **`dispatch`**: a virtual call the sources override, on a receiver whose class the summary does
  not know — a parameter's object, or one with no tracked origin. The caller knows the class; the
  summary would have to guess. Several implementations on the known classes are the same gap. A
  call on `this` is specialised instead: the summary is keyed by `(method, receiver class)`.
- **`fixpoint`**: the budget ran out. The last approximation is kept and may be short.
- **`depth`**: a field path was cut at 2, or two allocation chains were merged by the call-site
  limit, or a call made an object under a name the method already holds. Two objects became one,
  so their fields join.
- **`unmodelled`**: an `Unmodelled` instruction, or a field read or written through an object with
  no tracked origin (a foreach element, a catch parameter, the stored result of an opaque call), or a callee's effect on an object this
  method has no name for. An `Unmodelled` instruction is also recorded where the builder records
  it, so `--summaries` prints the same "constructs not modelled" lines and exits non-zero.
- **`body`**: lowering threw. Callers see an opaque call plus this gap.
- **`alias`**: summaries are context-insensitive. A callee is read as if `this` and each parameter
  were distinct objects. When two of its paths (`P0` and `P1`, `this.a` and `this.b`) are one
  object at the call site, and it reads a field through one that it writes through the other, or
  writes one field through both, the instantiated flow would be wrong. The check ignores order, so
  it may over-report. Too many gaps is a summary that says it is unsure; too few is silent. A write
  through a receiver that is one of several objects is the same kind: one name, several objects, a
  strong update on each (the builder's, `joins.md`), so the others' old values are lost.
- **`escape`**: a field written on an object made here (or by a callee) that no output reaches —
  not a `RET`, `this`, `P<i>` or `static` path. The object may have left with its fields: handed to
  an opaque call (`repo.add(e)`), or thrown, and `throw` is not lowered at all. Nothing tells that
  from a dead store, so both are gaps, as is a local object the method set and read back
  (`make(a).v`): noise, where the other way was silence. The gap is at the write.

A caller **copies** its callees' gaps, each keeping the callee's position, so a row lists every gap
that touched it with no reference to follow. That does not make a gap-free row complete: the cases
in [What is still silent](#what-is-still-silent) are not gaps yet. `counts.gaps` and the stderr
total count emitted rows, so an inherited gap counts once for every caller that lists it. Large
totals are mostly that.

### What is still silent

Known flows a summary drops or adds with no gap, all shared with the builder, so parity cannot see
them; each is a follow-up, not fixed on this branch:

- **`ROOT_THIS`, and root parameters.** A root runs on no object, so a callee's write to `this` is
  filed on nothing; `p.f(a, b, c); return p.x` with `p` a parameter is drawn `EXTERNAL`.
- **A guarded return, then a straight-line rewrite.** `if (c) { this.x = a; return; } this.x = b;`
  drops `a`.
- **`new Sub()` through an inserted default constructor** skips the superclass constructor and its
  initializers.

## Parity with the builder

`SummaryParityTest` holds the summarizer to the builder on every fixture. Each method with a
result is built alone as an inlined root (`build(listOf(m))`, which forgets statics first). Its
`P<i> → RET` reachability is read off the drawn nodes, and compared with the summary's
`P<i>>RET` pairs. `knownDifferences` names each disagreement, one entry per pair and direction,
with its reason. The test fails on a difference it does not name, and on an entry that no longer
happens.

A pair the builder draws and the summary lacks, on a method with no gap, is a summarizer bug by
default. It may be listed only with a sentence saying why the builder is wrong.

Today: 47 methods compared, 3 known differences, all `extra`, all `ROOT_THIS`. That one is a
**builder bug**, not fixed on this branch. A root runs on no object (`owner = emptySet()`), so a
callee's write to `this` is filed on nothing and the root's later read never finds it. `--from`
draws a silently wrong graph there today. Fixing it makes the entries stale, which fails the test
by design.

**Only `P<i> → RET` is compared**; field outputs, `RET.<qfield>` and `ext-arg` sinks are not. That
is why two bugs got past it. A factory's `RET.B.v` was never written (the parity reads the result's
parameters, not its fields), and `m.with(a).with(w)` is not in any fixture, so the merged objects
were never compared. The 100% on Fineract below says nothing about either.
Behaviour tests cover both now; widening the comparison is Phase 2.

`CODEFLOW_PARITY_SRC=<java dir>` runs the same comparison on a real corpus, by hand. It is skipped
otherwise. It fails only on a silently missing pair; the rest is reported.

## Fineract numbers

progressive-loan at `e048ee8b2b`, 2026-11-15.

| | Graph (`--json`, every method a root) | `--summaries` |
|---|---|---|
| Time | 70 s, 658 of 658 roots | 4–5 s, 687 methods |
| Document | 742 MB | 242 MB |
| Pairs | | 859,155, of which 262 to a `RET.<qfield>` |
| Gap rows | | 35,935: 19,194 `unmodelled`, 9,135 `depth`, 5,440 `escape`, 1,302 `alias`, 864 `dispatch` |
| Distinct gaps | | 2,378: 1,233 `unmodelled`, 518 `depth`, 460 `escape`, 87 `dispatch`, 80 `alias` |

- No `fixpoint` or `body` gap. 469 of the 687 methods have no gap at all.
- Against the first measurement (before the fix wave): the 262 `RET.<qfield>` pairs are new and
  every other pair is unchanged; `escape` is new, and 750 of the 1,302 `alias` rows (20 distinct)
  are writes on several holders. Chained calls named apart changed no Fineract pair and added no
  `depth` gap.
- 658 against 687: the smoke passes `Class#method` names to `--from`, and overloads share one.
- The 22 `TYPE_CAST` the graph run reports unmodelled are 22 of the `unmodelled` gaps (measured before casts were modelled; see `constructs.md`).
  `--summaries` reports them on stderr too, and exits 1, as the graph run does.
- **After casts were modelled** (`d449caa`, CI smoke of 2026-11-22): 860,619 pairs (+1,464) and 35,483
  gap rows, of which 18,742 `unmodelled` (−452). Every other kind is unchanged, and the graph run
  reports no unmodelled construct. The table above is the earlier measurement.
- **After a receiver on an unresolved type was kept** (`d66e869`, run locally on the same commit):
  2,799,736 pairs (3.3 times) and 38,957 gap rows: 20,802 `unmodelled`, 10,331 `depth`, 5,596 `escape`,
  1,350 `alias`, 878 `dispatch`. The document is 767 MB and takes 10 s. 658 of 658 roots are still graphed
  and the graph run still reports no unmodelled construct. Nothing was added: `widget.frame().width()` used
  to drop everything before `width` with no gap (see `attribution.md`), so the pairs and the gaps on those
  receivers were missing, not absent. The lines below are the measurement before it.
- **So `unmodelled` counts no construct here.** Its 18,742 rows, 53% of all gap rows, are field
  reads and writes through an object with no tracked origin, and callee effects on an object the
  caller cannot name. The kind's name misleads when the numbers are read. A kind of its own is
  proposed, not built: `docs/research/2026-11-22-codeflow-usefulness/review.md`.
- **Those rows still carry a fact.** `SP.forceAuthentication read on an object with no tracked origin`
  says the field is read, and where. In Keycloak's SAML `DeploymentBuilder#build`, `sp` is
  `adapter.getSps().get(0)`, an opaque result stored in a local. The value of each `sp.isX()` is
  dropped, so no `SP → deployment` pair exists, with or without casts. But the 15 rows name 13 of
  `SP`'s 14 fields as read, and the 14th is the setter that fix #49604 added. Read a gap row as
  "something is read here that I cannot follow", not as "nothing here".
- 92% of the pairs are `ext:` → `ext-arg:`: an opaque call's result passed on to another one.
  That is most of the document's size.
- **Parity, by hand** (`CODEFLOW_PARITY_SRC`): 421 methods with a result, 506 `P<i> → RET`
  pairs drawn and 506 summarised. Recall 100%, precision 100%, no difference of any kind.
