# Calmer condensed view — design (short)

Status: approved (user request 2026-11-15: "it looks too crowded" on opening Condensed; calls marked
*call* are the controller's). Builds on `2026-11-15-condensed-view-design.md`, `2026-11-15-entry-event-design.md`.

## Intent
Opening Condensed on `boundary` shows 5 nodes but a tangle of lines: each pair of display nodes gets
one edge **per kind** (flow, true, false, order, event), each with its own caption, packed into 40 px
between layers. The opening should read at a glance; the detail stays one hover away.

## Changes (Condensed view only)
1. **One edge per ordered pair** (*call*). All payload edges that land on the same
   `(shown(source), shown(target))` are one drawn edge, whatever their kinds. `A→B` and `B→A` stay two.
   - Colour/dash: the kind's own style when every underlying edge has one kind; otherwise neutral
     (the FLOW style), solid (*call*).
   - Caption: `×N` when it stands for N > 1 edges, else none. No kind words on the line (*call*):
     colour says the kind when there is one.
   - Hover on the edge: the kinds with counts first (`order ×2, flow ×3`, fixed kind order), then
     today's lines.
   - Click on the edge: reveals the endpoints of every underlying edge, of every kind (today's rule,
     with the kind filter dropped).
2. **More room** (*call*): Condensed `nodeNodeBetweenLayers` 40 → 70, `nodeNode` 25 → 40. The boxed
   view's options are untouched.
3. **Captions don't sit on lines**: edge captions get a page-coloured background (`labelBgStyle`
   from `theme.ts`, both themes), if not already.

## Not changed
Boxes view, payload, exporters, goldens, what a node click reveals, `condensedOpening`.

## Test
- Unit (boundary `graph.json` opening and a hand-written payload): no two drawn edges share an
  ordered pair; a pair with one kind keeps that kind's style; a mixed pair is neutral and solid;
  caption `×N` iff N > 1; hover starts with the kind counts; clicking a merged edge reveals the same
  set as the union of today's per-kind clicks. Each fails against the per-kind version — say which.
- Corpus sweep stays green (each leaf in exactly one display node at every step).
- Browser: Condensed on `boundary` opens on 5 nodes; count drawn edges before/after (report both);
  a merged edge's hover shows the kind counts. Screenshot of the opening in the report.
- Docs: `docs/decisions/viewer.md` condensed section: one edge per pair, why.
