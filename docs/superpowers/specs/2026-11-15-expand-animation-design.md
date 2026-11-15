# Expand animation — design (short)

Status: approved (user request 2026-11-15; calls marked *call* are the controller's).
Plan: `docs/superpowers/plans/2026-11-15-expand-animation.md`. Runs after the condensed view
(`2026-11-15-condensed-view-design.md`), since both change the renderer.

## Intent
A click that opens up the graph re-runs ELK from scratch, and the new layout can look nothing like
the old one: the user loses track of which node is which. Make before/after easy to match.

## Approach (*call*: all three, cheapest first; each helps on its own)
1. **Keep the layout stable.** Lay out with the previous positions as the starting point so nodes
   that were on screen move as little as possible: ELK layered with model order kept
   (`'elk.layered.considerModelOrder.strategy': 'NODES_AND_EDGES'`) and the node/edge order fed from
   the previous drawing (old nodes first, in their old left-to-right/top-to-bottom order, new ones
   after their neighbour). If ELK's interactive options (`'elk.layered.crossingMinimization.semiInteractive'`,
   `'elk.layered.layering.strategy': 'INTERACTIVE'` with prior positions) measurably move fewer old
   nodes on the fixtures, use them instead; the plan's Task 1 measures this and picks. Strings only.
2. **Animate the change.** Old nodes glide from their old position to the new one; new nodes grow
   out of the clicked node's old position and fade in; removed display nodes (an emptied condensed
   group) fade out where they were; edges follow their endpoints. ~350 ms, ease-out (*call*).
   The viewport keeps the clicked node where it was on screen during the move, then eases to
   `fitView` only if something new is off screen (*call*: today's refit-on-every-layout is what
   makes the jump feel total).
3. **Mark what is new.** Nodes and edges that just appeared get a short highlight ring (~1.5 s,
   fading) in a theme colour; the clicked node gets a steady "last clicked" outline until the next click.

## Rules
- Both views (Boxes and Condensed) (*call*). Boxes nest: a box animates as a node, its children
  relative to it.
- The model stays pure and knows nothing of animation: the renderer diffs the previous drawn view
  against the new one by node id (`serial`-based ids are stable across clicks). Positions are
  renderer state, not model state.
- `prefers-reduced-motion: reduce` → no motion: jump to the new layout, keep the highlight (*call*).
- `window.drawn` fires when the animation has ended (browser tests wait on it); a test hook can
  set the duration to 0.
- Colours from `theme.ts` only.

## Not changed
- What a click reveals, the payload, exporters, goldens.

## Test
- Layout stability, measured on every fixture `graph.json` with a click-everything walk: per click,
  mean displacement of nodes present before and after, and order inversions among them (pairs whose
  left/right or above/below order flipped). Report before/after this change; the plan's chosen
  options must reduce both on the sweep, and a unit test pins that a single-neighbour reveal keeps
  every old node's relative order on a small hand-written payload.
- Unit (pure diff helper): `diffViews(prev, next)` → `{kept, added, removed}` by id; added nodes'
  start position = clicked node's old position.
- Browser: after a click, the clicked node's screen position moves less than a threshold
  (*call*: 40 px) while the animation runs; new nodes carry the highlight class, then lose it; with
  reduced motion emulated, no transition style is applied; `window.drawn` fires after the end.
