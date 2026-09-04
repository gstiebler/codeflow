# spike/ - the boundary-diff dataflow view

A throwaway tree that answers one question: does comparing two builds of a program by the
**boundary pairs** they allow - "this input reaches that output" - say anything useful about a real
pull request? The answer is in [FINDINGS.md](FINDINGS.md). The design it tests is
`docs/superpowers/specs/2026-09-01-boundary-diff-view-design.md`; the plan it follows is
`docs/superpowers/plans/2026-09-02-boundary-diff-joern-spike.md`.

The view is a call hierarchy whose edges are values instead of calls. One row per source line, an
expandable tree, and a column no call hierarchy has: the **carrier**, the variable or field or
parameter that carries the value from one row to the next. It stops at boundaries - where a value
enters or leaves the process - because a dataflow tree with no boundaries runs until the reader
gives up.

## Running it

```bash
# 1. Build a CPG from a source tree. ~15 s for 300 files, ~30 s for 1200.
./spike/build-cpg.sh /path/to/module/src/main/java spike/out/before.cpg

# 2. Dump it to JSON. ~10 s. The script does no analysis at all.
./spike/dump.sh spike/out/before.cpg spike/out/before.json <commit-sha>

# 3. Ask it things.
node spike/src/cli.ts boundaries    spike/out/before.json
node spike/src/cli.ts forward       spike/out/before.json path/To/File.java:123
node spike/src/cli.ts backward      spike/out/before.json 'LoanRepository.save'
node spike/src/cli.ts boundary-diff spike/out/before.json spike/out/after.json

# Or draw the same slice instead of printing it. Graphviz, on stdout.
node spike/src/cli.ts dot forward spike/out/before.json path/To/File.java:123 | dot -Tsvg > slice.svg
```

A large corpus needs `node --max-old-space-size=24576`: the graph JSON is 186 MB for 254k nodes.

`$JOERN_HOME` defaults to `/opt/homebrew/Cellar/joern/4.0.570/libexec`.

## Reading a diff

```
NEW  throws NoSuchElementException in getDefaultAllocationRule
       from WorkingCapitalLoanBalanceRepository.findByWcLoan_Id
       from WorkingCapitalLoanRepository.findById
PATH throws IndexOutOfBoundsException in allocateToCharges
       from WorkingCapitalLoanBalanceRepository.findByWcLoan_Id

754 pairs: 7 new, 0 gone, 11 same endpoints by different code, 736 unchanged
```

`NEW` is a flow the change created, `GONE` one it removed, `PATH` one whose two ends are unchanged
but whose middle is not. All three are trustworthy: across the three commits in FINDINGS.md every
`NEW` and `GONE` row, and every `PATH` row, ran through a file the commit actually changed. `PATH`'s
weakness is volume rather than truth - a change to code that most requests run through reports most
pairs - and FINDINGS.md says what would fix that. EXAMPLES.md has the full output of every verb.

## Tests

```bash
npm run test:spike           # 66 tests, ~200 ms, never invokes Joern
./spike/test/extract.sh      # builds a CPG from spike/fixtures/tiny and checks it against the fixture
```

The unit tests run against `spike/test/corpus.ts`, a `Graph` value written by hand. That is fast
enough to iterate on a boundary rule in a second rather than in a two-minute Joern run, and it is
also a claim about what Joern produces - a claim that has been wrong six times so far. `extract.sh`
is what catches it: it builds a CPG from the six small Java files in `spike/fixtures/tiny` and
asserts the same shapes the fixture asserts. If one of those fails while its unit-test twin passes,
the fixture is lying about Joern.

## The pieces

| file | what it decides |
|---|---|
| `joern/export.sc` | nothing. Dumps methods, nodes, REACHING_DEF and CDG edges as JSON. |
| `src/types.ts` | the contract between Joern and everything below. |
| `src/load.ts` | the value graph: def-use, composition, fields, guards. Nodes, not lines. |
| `src/boundaries.ts` | the seven rules for where a value enters or leaves the process. |
| `src/scc.ts` | Tarjan, so a loop is one row rather than an infinite tree. |
| `src/slice.ts` | walking the graph, forwards and backwards, to boundaries. |
| `src/fingerprint.ts` | naming a statement so it survives being moved. |
| `src/diff.ts` | the four verdicts. |
| `precision.ts` | grading the verdicts against what each commit actually edited. |
| `src/render.ts` | the tree and the report. |
| `src/dot.ts` | the same slice as a picture: one box per source line, boundaries filled. |
| `src/cli.ts` | five verbs, no options. |

Two of those carry a lesson that cost real time and is written at the top of the file:
`load.ts` on why the graph is over Joern node ids and not over source lines, and `boundaries.ts` on
why every rule is string matching - Joern sees an annotation, not a servlet, and that is the honest
ceiling of a source-only tool.
