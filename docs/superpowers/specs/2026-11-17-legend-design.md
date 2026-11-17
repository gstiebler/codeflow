# A legend: what each colour, outline and line means — design (short)

Status: approved (user request 2026-11-17, "add a way to show a reference: what's a red box, ...,
what's a dashed line, etc"). Builds on `docs/decisions/viewer.md`.

## Intent
The page paints a dozen fills, two group outlines, six line styles and two click marks, and says what
none of them mean. A reader new to codeflow has to guess that red is a return and pink is "codeflow
stopped here" — and a guessed meaning is how a right graph gets read wrong.

## Approaches
- **A `Legend` toolbar toggle opening a panel, built from `theme.ts`** (chosen). The meanings sit
  beside the colours they explain, so a new colour without a meaning is visible in one file, and the
  legend cannot drift from what is painted.
- A static legend always on screen: covers the graph, and most of it is noise after the first read.
- A legend in the docs only: not where the reader is looking.

## Changes
1. `theme.ts`: `NODE_MEANINGS` (every leaf type JsonExporter emits) and `EDGE_MEANINGS` (every
   kind), and `legend(payload, mode)` → the rows to draw: leaf fills and line styles **only for the
   types and kinds this payload has**; the method box (Boxes) or the two group outlines (Condensed);
   the two click marks (blue ring = just appeared, black outline = last clicked). Each row takes its
   colour from the same constant the renderer paints with.
2. `reactflow.tsx`: a `Legend` button (`aria-pressed`) in the toolbar, both modes; the panel draws
   `legend(...)`: a swatch or a line sample, the name, the meaning. Closed by default.

## Not changed
Kotlin, payload, Mermaid, model, layout.

## Test
- Unit (`legend.test.ts`): every NodeType but METHOD/BASE and every EdgeKind has a meaning; a payload
  with only FLOW edges and LITERAL/RETURN leaves lists exactly those (pair: a payload with ORDER
  lists ORDER, dashed, in the ORDER colour); Condensed lists the boundary outline, Boxes does not.
- Browser: the panel is absent, opens on `Legend`, lists `RETURN` on a fixture that has one, and
  closes again.
