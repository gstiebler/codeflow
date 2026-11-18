# Condensed: a click pulls its path into a lane — design (short)

Status: approved (user request 2026-11-17, with a sketch: "when selecting a node or edge, have the
path from it to the entry point and to boundaries somewhat separated from the other nodes"; scope
answer "condensed only"). Builds on `docs/decisions/viewer.md`.

## Intent
A click is the reader saying what they want to follow. Today it only reveals neighbours; the path the
clicked value sits on is mixed into everything else, and following it by eye across a wide graph is
where a reader loses the thread.

## Approaches
- **Lay the path out in its own ELK compound node ("lane"), dim the rest** (chosen). The path is one
  block on the page, its lines routed inside it; the rest stays drawn for context.
- Dim only: nothing moves, so a path spread across the page stays spread.
- Show only the path: strongest focus, but the context the reader came from is gone.

## Changes
1. `condensed.ts`: `focusOf(payload, revealed, target)` → the seeds of a click (a leaf: itself up and
   down; a drawn edge: its payload edges' sources up, targets down; a group: none - it stands for
   unrelated values). `focusPath(payload, revealed, focus)` → display nodes and edges on the path.
   The walk is over **payload** edges, so it follows real dependencies through closed groups rather
   than treating a group as a hub; a hidden leaf on the path puts its group on the path. ORDER edges
   are not walked: they say which call ran first, and following them pulls in every later call.
2. `layout.ts`: `flatElkGraph` takes the path and wraps it in a `focus` compound node
   (`INCLUDE_CHILDREN`); `routes` puts an edge ELK placed inside the lane back into the root frame.
3. `transition.ts`: a node changing parent (into or out of the lane) starts from where it was on the
   page, not from its old relative position.
4. `reactflow.tsx`: the lane drawn as a shaded box; nodes and lines off the path dimmed. Cleared by a
   click on the canvas or the lane, `Esc`, Reset, or a click on a group. Focus is the page's state,
   beside `revealed`.
5. `theme.ts`: the lane's fill and outline, and a legend row for it.

## Not changed
Boxes, Kotlin, payload, Mermaid export, what a click reveals.

## Test
- Unit: a diamond `top→a→bottom`, `top→b→bottom`: a click on `a` puts `top, a, bottom` on the path,
  not `b` (pair: `b` gives `top, b, bottom`, not `a`); an edge click; ORDER not walked; a hidden leaf
  puts its group on the path; a group click has no focus; a lane edge's route comes back in the root
  frame; a kept node changing parent starts at its old page position.
- Browser: a condensed click draws the lane and dims something, the clicked node not dimmed; `Esc`
  removes both.
