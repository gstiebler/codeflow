# ORDER edge kind — design (short)

Status: approved (user request 2026-11-15, "Yes, add ORDER"; calls marked *call* are the controller's).
Builds on `docs/superpowers/specs/2026-11-15-boundary-order-design.md` ("Known cost").

## Intent
The `boundaries` chain uses plain `FLOW` edges, so every argument of a boundary call appears to
*reach* every later boundary call: order reads as data. A distinct `EdgeKind.ORDER` lets a reader,
the viewer and `reaches()` tell "ran before" from "value flowed into".

## What is ORDER
- Exactly the two chain edges of each boundary call: `boundaries → call<name>` (the token in) and
  `call<name> → boundaries` (the token out). Nothing else.
- Unchanged kinds (*call*): the gated join of the token keeps `TRUE`/`FALSE`/`CONDITION` and its
  `if → boundaries` edge; `SHARED` between roots stays `SHARED`. They are the static-field rules
  the chain reuses; between two ORDER edges they cannot carry data anywhere.
- The edge kind is decided where the edge is made, from the instruction (the token's read feeding a
  call, the token's write taking the call), never from a label match.
- `call<name> → result` stays `FLOW`: the call's value is still data.

## Drawing
- Mermaid: `-. order .->` (dashed with a label, like `shared`, since neither is a value within one
  run), and a stroke colour in `MermaidExporter.strokes` (*call*: `#7b1fa2`, purple).
- Viewer: `ORDER` in `types.ts` `EdgeKind` and the same colour in `theme.ts` (they must match);
  dashed like the Mermaid edge if the renderer already supports a dashed style, else colour only.
  Rebuild the committed bundle (`scripts/build-viewer.mjs`); `npm run check:bundle` green.
- GraphML/JSON: the kind travels as today's kinds do.

## Reachability
- Both test helpers (`TestFixtures.reaches`, `AppTest.reaches` over Mermaid lines) follow **data
  only**: they do not cross `ORDER` edges (*call*). A separate helper follows the chain for order
  assertions (e.g. `ordered(from, to)` over ORDER + the join's edges).
- Restore the negative `c ↛ callboundaryfunc2` in `argumentsReachTheBoundaryCallsTheyArePassedTo`
  (the boundary-order change weakened it to `c ↛ callboundaryfunc1`). Keep the new one too.
- The order tests from the boundary-order change move to the order helper and keep their negatives.
- `SummaryParityTest`: its graph side must not cross ORDER edges; add the `boundary` and
  `boundaryOrder` fixtures (with their `--boundary` flags) to the fixture sweep so parity is
  checked with the chain present. Parity stays 100%.

## Not changed
- Node types, the chain's nodes, `--summaries`, Lowering. Goldens without `--boundary` byte-identical.

## Test
- Behaviour: in `boundary/` every `boundaries → call…` and `call… → boundaries` edge is ORDER, no
  other edge is; an argument of `callboundaryfunc1` does not data-reach `callboundaryfunc2`, while
  the order helper says func1 runs before func2. Check each fails against a wrong version (chain
  left FLOW; ORDER on `call → result`).
- Viewer: a unit test that an ORDER edge gets the ORDER colour; a model sweep still green.
- Only `boundary/` and `boundaryOrder/` goldens move (arrow and linkStyle lines); structural diff,
  every leftover explained.
- `docs/decisions/calls-and-dispatch.md`: replace the "Known cost" sentence with the ORDER rule;
  `docs/decisions/joins.md` edge-kind list gains ORDER; CLAUDE.md's `EdgeKind` list gains `ORDER`.
