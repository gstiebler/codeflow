# Export the condensed view to Mermaid — design (short)

Status: approved (user request 2026-11-15, "export the current view in condensed mode, to mermaid";
calls marked *call* are the controller's). Builds on `2026-11-15-calmer-condensed-design.md`,
`2026-11-15-routed-lines-design.md`.

## Intent
The reader opens the condensed view until it shows what they want to explain, then wants that
picture outside the page: in a note, a doc, a PR. Mermaid is the format codeflow already speaks.
The export is **what is on screen now**: the same display nodes and edges, after the reader's clicks.

## Changes (Condensed view only)
1. **Pure exporter** `condensedMermaid(view: CondensedView): string` in a new
   `app/src/main/resources/viewer/mermaid.ts`, same contract as `condensed.ts` (no DOM, imports types
   and `theme.ts` only). Input is the very `condensed(payload, revealed)` the renderer draws, so the
   file cannot differ from the screen.
   - `flowchart TD`. Node ids `n0, n1, …` in view order (*call*: never derived from attributes).
   - Label = `condensedCaption(node)`, in `"…"`, with `"` `<` `>` `#` written as Mermaid entities
     (`#quot;` `#lt;` `#gt;` `#35;`). Plain strings, not markdown strings (*call*: no `_` escaping).
   - Colours = what the viewer draws: one `classDef` per fill actually used, values from `theme.ts`
     (leaf fills from `PALETTE`/`NODE_DEFAULT`, group outline from `GROUP_OUTLINE`/`BOUNDARY_OUTLINE`).
   - Edges: one line per drawn edge, caption `×N` when N > 1; colour/dash from `condensedEdgeStyle`
     via `linkStyle <index> stroke:…` (+ `stroke-dasharray` when dashed), indexes in write order.
   - Hover text and routes are not exported (Mermaid lays out itself).
2. **Button** `Mermaid` in the toolbar, shown only in Condensed (*call*). It downloads
   `codeflow-condensed.md` (*call*: a ```` ```mermaid ```` fence, so it renders when opened in
   Obsidian or GitHub). No clipboard (works from `file://` everywhere, no permission prompt).

## Not changed
Model, layout, Boxes view, Kotlin `MermaidExporter`, payload, goldens.

## Test
- Unit on `boundary` `graph.json`:
  - the opening exports 5 node lines and one edge line per `view.edges`, captions `×N` iff N > 1;
  - after a `tapCondensed` the export changes to match the new view (fails against exporting the
    opening);
  - a label with `"`, `<`, `>`, `#` comes out escaped (fails against raw labels);
  - every `linkStyle` index points at an edge of matching colour (fails against off-by-one).
- If a Mermaid parser is available as a devDependency, the export parses; otherwise say so (do not add
  a dependency just for this without noting it).
- Browser: Condensed on `boundary`, click a node, wait on `window.drawn`, click `Mermaid`: the
  download is `codeflow-condensed.md`, fenced, and its node count equals the drawn node count.
  The button is absent in Boxes (paired with present in Condensed).
- `npm test`, `check:bundle`, `test:browser`, `./gradlew test` green; bundle rebuilt.
- Docs: `docs/decisions/viewer.md` condensed section, one paragraph.
