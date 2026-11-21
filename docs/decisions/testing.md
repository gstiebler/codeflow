# Testing

Moved verbatim from CLAUDE.md, which now keeps only the rules. This is the reasoning behind them.

# Testing

`AppTest.kt` has three kinds of assertion, and the mix is deliberate:

- **Golden files** (`app/src/test/resources/<fixture>/snapshot.md`). They certify
  *unchanged*, not *correct*. `ternary/snapshot.md` was once written from a buggy run and passed
  happily while encoding a graph with a branch missing. Treat a green golden file as evidence of
  nothing.
- **Behaviour tests** using `edgeLabels`, which reduces the graph to `(sourceLabel, targetLabel)`
  pairs and ignores ids. These are the assertions that can fail on a graph that has never been
  correct. **New behaviour needs one of these**, not just a regenerated snapshot.
  `edgeLabels`, `edgesOf` and `reaches()` skip the root's `EVENT` edges (and `reaches()` `ORDER`
  too): an event starts every node nothing else feeds, and an assertion on which labels are
  adjacent, or what reaches what, is about the code, not that. The exporter-parity tests and `allEdgesOf` keep them.
- **Suite-wide invariants** run on every fixture: `assertNoSelfEdges`, `assertNoUnknownOperators`,
  `assertNoDuplicateNodeIds`, `assertNoNodeIsMentionedBelowItsOwnBlock`,
  `assertLinkStylesAddressTheEdgesTheyMean`. These read the rendered Mermaid — the last two check
  what Mermaid will *make* of it, which is not the same as what the graph says; the three in
  `InvariantsTest.kt` below read the graph against the IR.

The golden test also writes each fixture's graph as the interactive page, to
`app/src/test/resources/<fixture>/graph.html`, gitignored and rewritten on every run — `snapshot.md` is
the diagram as *text*, which is what a snapshot can compare, and the viewer is what the tool is
actually pointed at. Nothing asserts on it (`AppTest.writePage`), same as `ir.txt` below: it is there
to be opened. Each page inlines its own copy of the vendored libraries, so it is about two megabytes,
which is why they are not committed.

Snapshots are only written when missing or under `UPDATE_SNAPSHOTS=1`, so a regression cannot
overwrite its own expectation. When a change does move snapshots, verify them *structurally* rather
than reading diffs: normalise old (`git show HEAD:<path>`) and new to sorted multisets of
`label:TYPE` nodes and `label:TYPE -> label:TYPE` edges with ids stripped, and diff those. Anything
left over is a real change and needs explaining.

`app/build.gradle` sets `maxHeapSize = '2g'` on the test task because two suites sweep every fixture
directory, so a javac task per fixture is live at once and Gradle's 512m default ran out. It failed as
the *worker* dying after every test had already reported passing, which reads as infrastructure rather
than as anything about the suite — worth knowing before chasing it again after adding fixtures.

Three suites sit alongside `AppTest` and assert on something a rendered document cannot show:

- `LoweringTest.kt` — the instruction list itself, as text, for one method or swept over every
  fixture. What a method *means*, before anything has decided how to draw it. The sweep also writes
  each fixture's whole IR to `app/src/test/resources/<fixture>/ir.txt`, gitignored and rewritten on
  every run: nothing asserts on it, it is there so that `App.java`, `ir.txt` and `snapshot.md` can be
  read side by side as source, meaning and diagram for one fixture. A stale one would be worse than
  none, which is why it is not a snapshot.
- `IrGraphBuilderTest.kt` — the graph as `label:TYPE` nodes and edges, for the few cases where the
  node *type* is the claim. While both builders existed this was the port's differential harness:
  every fixture built both ways and compared as multisets, with each disagreement asserted by name.
  The comparison went with the tree walker; what is left is the behaviour it found.
- `InvariantsTest.kt` — §9's three properties, swept over every fixture directory: every literal is
  drawn once per drawing of its method and reaches something; every box drawn for an instruction that
  consumes a value shows it arriving; every instruction draws a box (or, for a call, opens a block)
  and every box sits at a position some instruction this run read occupies. These need no expected
  output and no fixture author to have anticipated the failure, which is why they hold on a corpus
  nobody has read. They compare the graph against **the IR it was drawn from**, joined on the
  position both sides carry, so the harness builds `IrGraphBuilder` directly and keeps it: `bodyOf`
  is memoised, so the instruction list it hands back afterwards is the one that was drawn.

  Two things about that join. `ProcessorContext.location` records the *start* offset only, so both
  `+` of `a + b + c` carry one position — which is why the checks ask whether a box exists for an
  instruction and never how many sit at a position. The exception is a literal, the one instruction
  whose mapping onto boxes is exactly one, where the count is the assertion and is what catches a
  body drawn twice. `drawnLabel` is a `when` over the sealed `Insn` for the same reason `Frame.draw`
  is one, and the four kinds that draw no box of their own each say why on the spot.

  Each of the three was confirmed against a *wrong* implementation rather than an absent one:
  drawing a literal twice, dropping the assignment edge into a written variable, dropping a unary
  operator's operand edge, letting a unary operator vanish into its operand, and giving a box the
  enclosing method's position instead of its own. The middle three fail one test each, which is what
  says the three are not restating each other.

`app/src/test/resources/codemap` and `ls` are named by no test of their own — `InvariantsTest`'s
sweep is the only thing that reaches them; `codemap/snapshot.md` is stale and still in the pre-serial id
format.

## The viewer's tests

Split by what they can actually catch:

- `app/src/test/js/unit/` (`npm test`) — the pure functions, imported straight from `condensed.ts`.

  The corpus sweeps live here too, over the `graph.json` the golden suite writes beside each
  fixture (gitignored, rewritten every run, like `ir.txt`).

  `corpus.mjs` fails when it finds fewer than 60 payloads rather than sweeping an empty list — a
  checkout that has not run `./gradlew test` has none, and a sweep over nothing passes every
  property it is given. Same trap as a negative browser assertion, one layer up.
- `app/src/test/js/browser/` (`npm run test:browser`) — Playwright against pages built from real
  fixtures. `global-setup.mjs` runs `gradlew run --args="<fixture> --html"` per fixture, with an
  **absolute** path: the `run` task's working directory is `app/`, so a repo-relative one resolves
  to `app/app/...` and `Files.walk` throws. The fixtures built are listed in `global-setup.mjs`.

  `page.ts` is how the suite talks to the page, and everything in it goes through the **DOM** rather
  than through the `view` the page publishes: a page whose data is right and whose rendering is not
  is the whole of what a browser test adds over a unit test on `screen`.

Three traps, each of which produced a green — or hanging — run on a broken page:

- **The page draws a layout behind the view.** A click changes the view synchronously and the screen
  once ELK answers, so an assertion run in that gap reads the screen from *before* the gesture. The
  first run of this suite lost that race exactly twice out of fifteen, which is the worst way for a
  test to be wrong. Every gesture in `page.ts` therefore waits on `window.drawn`, a count of layouts
  the page has drawn.
- **That signal must not be "the DOM matches the page's own `view`".** That asks the renderer whether
  the renderer is right, and a page drawing nodes it should have hidden hangs instead of failing.
  That is not hypothetical — it is what happened when the mutation was tried.
- A negative assertion (`not.toContain`, `count === 0`) passes trivially when the feature does
  nothing at all. Each one is paired with a positive assertion that fails in that case. Confirm a
  new test fails against a *wrong* implementation, not just an absent one — revealing every node is
  the mutation to try. Removing **both** `visible` filters (`elkGraph` and `nodesOf`; either alone is
  redundant) fails 11 of the 14, which is the check that this suite bites.
