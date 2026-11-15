# Boundary call order — design (short)

Status: approved (user request 2026-11-15; calls marked *call* are the controller's).

## Intent
Boundary functions (`--boundary <Class>`, `docs/superpowers/specs/2026-11-15-boundary-functions-design.md`)
usually have side effects, so the order they run in matters, and today the graph does not show it:
two boundary calls with unrelated arguments are two unconnected boxes. The user wants a special
variable `boundaries` threaded through every boundary call:
`boundaries → call<name> → boundaries' → next call<name> → boundaries'' → …`.

## Semantics
- `boundaries` behaves **exactly like a static field declared in the sources** that every boundary
  call reads and then writes. So it gets, for free and with no new rule:
  - its first read with nothing written is a `boundaries` node with nothing flowing in (the start);
  - inlining: one chain across caller and callee, since the builder's field state is per build,
    not per frame;
  - `if`/`switch`: a gated phi `boundaries` at the join when the arms leave it different, the same
    as a static field written in an arm;
  - loops: the same as a static field today (no back edge; a known limit, not new);
  - several roots: the same as a static field (forgotten between roots, `SHARED` edges between
    them — entry points run in any order) (*call*).
- Each boundary call, in the assumed implementation `Lowering.assumeBoundary` builds:
  1. read `boundaries`;
  2. `call<name>` takes it as one more input, after the parameters (edge `boundaries → call<name>`);
  3. write `boundaries` = the call's value (edge `call<name> → boundaries`, a new `VARIABLE` node
     labelled `boundaries`);
  4. then `result` / `RETURN` as today. void boundaries thread the chain too.
- Edges are ordinary `FLOW` edges, as asked (*call*; see "Known cost").

## Mechanism (builder's choice, within these constraints)
- Identify the token by an object, not by matching the string `"boundaries"` (a user field named
  `boundaries` must stay an ordinary field). E.g. a dedicated holder in `Heap` listed among the
  static holders, read/written by a small new instruction pair or a flag on `ReadField`/`WriteField`.
- Writes go through the same journaled path static-field writes use, so forks/joins see them.
- A new `Insn` needs its `Frame.draw` branch, a `LoweringTest` assertion and a Summarizer branch.

## Not changed
- Without `--boundary`, nothing: **every existing golden except `boundary/` stays byte-identical.**
- `--summaries`: unchanged (*call*): the summarizer ignores the token; boundary calls stay ext
  atoms/sinks. Parity stays 100%.
- Node types, edge kinds, colours, viewer: none.

## Known cost (*call*, tell the user)
Because the chain uses plain flow edges, every argument of a boundary call now *reaches* every later
boundary call through `boundaries`. Reachability questions across boundary calls over-approximate.
A distinct edge kind (e.g. `ORDER`) would let readers and `reaches()` tell order from data; not in
scope unless asked.

## Test
- `boundary/` fixture (already has an `if/else` with calls in both arms in different orders):
  - start `boundaries` → `callboundaryfunc1` → `boundaries` → both arms' first call;
  - then-arm: `callboundaryfunc2` reaches `callboundaryfunc3`, and **not the reverse**;
    else-arm: `callboundaryfunc3` reaches `callboundaryfunc2`, and not the reverse;
  - both arms' last `boundaries` reach one phi `boundaries` (gated by `a > b`);
  - `callboundaryfunc1` has exactly one more incoming edge than its parameter count.
  Check each assertion fails against a wrong implementation (e.g. chain built per frame, or the
  write before the read).
- Inlining: a source method that calls two boundary methods, called from the root between two other
  boundary calls → one chain in source order across the callee.
- A void boundary method threads the chain.
- A user field named `boundaries` in a fixture class is not the token (no edge between it and the chain).
- Golden diff: only `boundary/` moves; structural diff (sorted `label:TYPE` multisets, ids
  stripped, old from `git show HEAD:<path>`), every leftover explained.
- `docs/decisions/calls-and-dispatch.md` "Boundaries" section: one paragraph on the chain and the
  known cost.
