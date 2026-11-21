# Method boxes only when needed

Approved 2026-11-20. Small change on branch `no-boxes`, on top of `2026-11-20-method-boxes-design.md`.

## Why
One box per call site put 85-315 boxes on the Fineract opening. Most of them say nothing yet. Put
everything back into `program` and draw a method box only once it matters to what is on screen.

## What
Where a hidden leaf is drawn, a pure function of (payload, revealed):
- its call-site box when the box is **boundary** - always drawn, per call site, boundary look;
- its call-site box when the box is **open**: some member of the box is revealed, or some hidden
  member has an edge **of any kind but EVENT** (either direction) to a revealed leaf;
- else `program`.

A revealed leaf is drawn on its own, as today.

- "A revealed member keeps the box open" makes it monotone: once open, a box stays drawn until empty,
  never falls back into `program`.
- EVENT edges do not open a box: the entry event has an EVENT edge to almost every node it starts
  (up to 579 on Fineract), and counting them opened 48-248 boxes on the opening. Without them the
  opening is `program` plus the boundary boxes (14-30 on Fineract).
- Still flat: no box inside a box. A nested call's box opens on its own rule, not its caller's.
- Clicks unchanged: a group click reveals its frontier (members with an edge to something drawn
  outside it), or all its members when it has none. That holds for `program` too.
- Legend "Method boxes": a `program` row, then method, boundary.

## Approaches
- **A. Touches the screen** (picked): a box appears when its hidden nodes connect to what is open.
- B. Opened in it: a box appears once one of its nodes is revealed. Fewer boxes, but a box's edges to
  the screen stay hidden inside `program` until then.
- C. A fixed size/depth rule: blind to what the reader is looking at.

## Builder's calls
- `program` is the group id and label; it has no `boundary` and no `called` line in its hover.
- Group order: `program` first, then boxes in payload order; a group with no hidden member is not drawn.
- `groupOf(payload, revealed)` (hidden leaf -> group id) replaces the static `groupOf(payload)`, so
  every function and the sweep test share the one rule.
