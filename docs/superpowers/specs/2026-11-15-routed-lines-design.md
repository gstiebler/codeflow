# Routed lines in the condensed view — design (short)

Status: approved (user request 2026-11-15, "Yes, route lines"; calls marked *call* are the controller's).
Builds on `2026-11-15-calmer-condensed-design.md`, `2026-11-15-expand-animation-design.md`.

## Intent
ELK already lays out the condensed view, but only node positions are read (`layout.ts` `positions`);
React Flow then draws each edge as its own bezier from a fixed bottom handle to a fixed top handle.
So lines cut through nodes, edges going back up loop around, and a line can leave the screen. Draw
each line along the route ELK computed, which keeps clear of nodes by construction.

## Changes (Condensed view only, *call*: the user's complaint; Boxes view nests coordinates per box
and is a separate step if wanted)
1. **Ask ELK for routes:** condensed options gain `'elk.edgeRouting': 'ORTHOGONAL'` (*call*: clearest
   to follow at a glance; strings only). Read each edge's `sections` (start, bend points, end) from
   the layout result — a pure function in `layout.ts` beside `positions`, unit-testable without a browser.
2. **Draw the route:** a custom React Flow edge type for the condensed view draws the polyline through
   ELK's points with small rounded corners (*call*: ~6 px), ignoring the node handles; arrow at the end
   as today; colour/dash from `condensedEdgeStyle` as today.
3. **Caption** `×N` sits on the route (midpoint of its longest segment, *call*), page-coloured
   background as today.
4. **Motion:** while nodes glide (expand animation), routes from the new layout would not match the
   moving nodes. During the move draw the edge as today (follows its endpoints); when the move ends,
   switch to the route (*call*). With reduced motion or above the size cutoff: route straight away.
   `window.drawn` semantics unchanged.
5. **Fit:** the opening fits the whole drawing, routes included (no line leaves the screen on
   `boundary`).

## Not changed
Boxes view, model (`condensed.ts`), payload, exporters, goldens, what clicks reveal, hover text.

## Test
- Unit: the route reader on a small laid-out ELK result — start, bends, end in order; an edge with
  no section is an error (bubble up, never a silent straight line). Fails against "ignore sections".
- Unit: caption position = midpoint of the longest segment.
- Browser on `boundary` Condensed opening: every drawn edge path's points stay outside every node's
  box except at its own endpoints (fails against today's beziers — show it); every path lies inside
  the viewport after the opening fit; after a click, once `window.drawn` advances, edges are routed
  again. Pair negatives with positives. Screenshot of the opening.
- `npm test`, `check:bundle`, `test:browser`, `./gradlew test` green; bundle rebuilt.
- Docs: `docs/decisions/viewer.md` condensed section: why routes come from ELK, why straight during motion.
