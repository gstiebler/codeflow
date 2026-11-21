# Method boxes in the condensed view

Approved 2026-11-20. Small change; builds on branch `no-boxes` (Boxes view removed, condensed is the only view).

## Why
Condensed today has one group per boundary method plus one `program` bucket for everything else, so every
non-boundary method disappears into `program`. Method boxes put the program's methods on the map.

## What
- A condensed group is the **call-site box** a leaf sits directly in (`leaf.parent`, a METHOD node): one group
  per call site, not per method. Every path drawn is real; call order stays visible.
- **Flat: no box inside a box.** A nested call's box is a sibling of its caller's box; the edges between them show the call.
- A box never draws anything inside it. Caption: the method's simple name (`box.label`) with the member count,
  as today. Hover: full `method` and the call site's `source` (file:line).
- A revealed leaf comes out of its box and is drawn on its own, as today. A box is drawn while it still holds a
  hidden leaf.
- Boundary blocks are per call site too, still drawn in the boundary style (`boundary: true` on the group).
- The `program` bucket is gone: every leaf of every test payload has a parent box. A leaf with no parent is an
  error (throw), not a silent bucket.
- Opening unchanged: entry-point events revealed, every box drawn.
- Legend: heading "Boxes" becomes "Method boxes"; a plain method box gets a legend row next to the boundary one.

## Approaches
- **A. One box per method** (all call sites merged, as boundary groups are today): fewer boxes, but a merged
  box draws paths that don't exist (call 1's argument appears to reach call 2's result). Rejected by the user.
- **B. One box per call site** - picked by the user.
- Nested boxes - rejected by the user ("no boxes inside boxes").

## Builder's calls
- Group id = the box's payload id (unique, cannot collide with a leaf id).
- Group order: payload order of the boxes.
- Tests and docs that assume `program` / `boundary:<method>` ids are rewritten to the new ids.
- Risk to check after building: the opening's box count on the Fineract example (`examples/fineract/run.sh`).
