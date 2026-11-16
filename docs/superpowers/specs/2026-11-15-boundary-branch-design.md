# The `if` decides which boundary call runs next — design (short)

Status: approved (user request 2026-11-15, on `boundary/`: "the decision of where the boundary node
should go after boundaryfunc1 (either to boundaryfunc2 or boundaryfunc3) should come from the if";
calls marked *call* are the controller's). Builds on `2026-11-15-boundary-look-design.md`,
`docs/decisions/joins.md`, `docs/decisions/calls-and-dispatch.md` (order token).

## Intent
Today the token after `call boundaryfunc1` fans out with two plain `ORDER` edges, one into each arm
(`boundaries → call boundaryfunc2` and `boundaries → call boundaryfunc3`). Nothing says only one arm
runs, or what picks it; the `if` appears only at the join below. The fork should say what the join
already says: the condition chose.

## The graph
- **A split at the fork** (*call*: the mirror of the gated join). Where an `if` statement's arms
  read the order token, one `BIN_OP` box captioned `if` takes
  - the token from above (`ORDER`), and
  - the condition (`CONDITION`, drawn `-->|if|` as the join's gate is),
  and hands the token on with **`TRUE`** into the then-arm and **`FALSE`** into the else-arm
  (*call*: the kinds and colours the join's inputs already use). It is BIN_OP like the join; the
  token boxes stay BOUNDARIES.
- Each arm's first token read is the split's output for that arm: `if -->|true| call boundaryfunc2`,
  `if -->|false| call boundaryfunc3` on `boundary/`. No `ORDER` edge from the pre-`if` token into an
  arm remains.
- **An arm that never touches the token** (no `else`, or no boundary call in it): the split's edge for
  that arm goes straight to the join (`if -->|false| if`). So a split is drawn exactly when the
  token's gated join is (*call*: no split for an `if` whose arms leave the token alone).
- Nested `if`s: each its own split, gated by its own condition.
- **Only the token** (*call*): ordinary values reaching into arms stay ungated, as today.
- **Scope** (*call*): `if` statements only. A `switch` statement and a `?:` with boundary calls in
  their arms fan out the same way today; they are the same change with more arms, a later step if
  wanted. Loops are not touched.
- Where it is made is the builder's choice (Lowering, which already forks `definitions` and emits the
  token's `FieldJoin` at `if`, is the likely place: one new instruction or a field on the fork, and a
  branch in `Frame.draw`). Decided structurally (the token's holder), never by label.
- `Summarizer` ignores the token today; it keeps ignoring it, `SummaryParityTest` unchanged.

## Not changed
Without `--boundary` nothing changes (no token, no split): every such golden byte-identical.
Viewer code, edge kinds, colours, the join.

## Goldens
`boundary/` moves (one split box, its four edges, the two arm `ORDER` edges gone, linkStyles
renumbered). `boundaryRoots/`, `boundaryOrder/` move only if they have an `if` with boundary calls —
say. Structural diff against `HEAD` (sorted `label:TYPE` multisets, ids stripped); explain every leftover.

## Test
- Behaviour (AppTest, `boundary/`): the token written by `call boundaryfunc1` has exactly one edge
  out, `ORDER`, into a BIN_OP `if`; that `if` has a `CONDITION` in from `>`, a `TRUE` out reaching
  `call boundaryfunc2` and a `FALSE` out reaching `call boundaryfunc3` (the else arm's first call);
  no `ORDER` edge from that token into either call. Fails against today's fan-out and against
  swapped `TRUE`/`FALSE`.
- New small fixture `boundaryBranch/` (*call*): an `if` with no `else`; an `if` with a boundary call
  only in the `else`; a nested `if`; an `if` with no boundary call in either arm (no split). Behaviour
  tests for each: split→join edge for the empty arm; one split per nested `if`, each gated by its own
  condition; no split where the token is untouched. Each fails against a named wrong version.
- Invariant (InvariantsTest, every fixture): every split has exactly one `ORDER` in, one `CONDITION`
  in, one `TRUE` and one `FALSE` out.
- `./gradlew test`, `npm test` (corpus sweeps read the new `graph.json`), `test:browser` green.
  If a viewer test pinned `boundary` counts that move, update it and say which.
- Docs: `docs/decisions/calls-and-dispatch.md` order-token section and `joins.md` (the split as the
  join's mirror), one paragraph each.
