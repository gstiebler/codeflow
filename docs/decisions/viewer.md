# The interactive viewer

Moved verbatim from CLAUDE.md, which now keeps only the rules. This is the reasoning behind them.

`HtmlExporter` substitutes one **bundle** and the JSON payload into `template.html`. It is
substitution only — all of the behaviour lives in the bundle. `HtmlExporter(bundle)` has no default
argument on purpose: a default would name a file, and a name that is not in the jar fails at the one
moment nobody is watching, so every call site says which page it wants.

**One renderer, and a view model that does not know about it.** `--html` emits the React Flow page.
There were two for a while — a Cytoscape page kept as a second opinion, since it *derived* an edge's
visibility from its endpoints and a box's from its descendants where React Flow is told both, so a
page the two drew differently was a fact about the view model neither could have produced alone. It was
removed once every property it could disagree about had a unit test on the view model standing behind it,
which is where the argument goes if a second renderer is ever wanted again: the value was the
independent derivation, and a test that states the rule is a cheaper way to hold it.

The viewer is TypeScript, under `app/src/main/resources/viewer/`, and the boundary between deciding
and drawing is the point:

| File | What it is |
|---|---|
| `condensed.ts` | every decision about what is on screen — `condensed`, `tapCondensed`, `hoverText`, `captionParts` — as pure functions of `(payload, revealed)`, no DOM and no library; see [The condensed view](#the-condensed-view) |
| `mermaid.ts` | `condensedMermaid(view)`: the condensed view on screen as a Mermaid flowchart — see [The condensed view](#the-condensed-view) |
| `layout.ts` | the layout posed as a value: `flatElkGraph(view)` and `positions(laidOut)`, seeded from the last drawing by `stabilise`; the condensed lines' `routes(laidOut)`, `captionAt`, `roundedPath` |
| `transition.ts` | how one drawing becomes the next — `diffViews`, `frame` — by node id, pure; see [Expanding without losing your place](#expanding-without-losing-your-place) |
| `types.ts`, `theme.ts` | the payload and view types, and the colours the page paints with |
| `reactflow.tsx` | the renderer, and what `--html` emits |

Before the split, three decisions lived only inside a browser — the opening set, read out of a graph query for
orphan `METHOD` nodes; the click, decided by the library's own `descendants()`; and the render loop
— so the half of the viewer that chooses what a reader sees was covered only by whatever a Playwright
test happened to click.

`scripts/build-viewer.mjs` runs esbuild over `reactflow.entry.tsx` and writes `reactflow.bundle.js`
beside it, **committed**, so Gradle never runs npm and a checkout with no `node_modules` still builds
a page. `npm run check:bundle` rebuilds into a temp directory and byte-compares, which is the only
thing standing between a source change and a page still carrying the previous one. `npm run
typecheck` is what makes the annotations mean anything: `node --test` reads `.ts` by stripping the
types, so nothing else would ever look at them.

`package.json` deliberately has **no `"type"` key**, and the `"//type"` entry beside it says why:
setting `"module"` silences node's reparse warning and also flips esbuild's CJS interop, turning
every `__toESM(cjs())` into `__toESM(…, 1)` — which overwrites a module's own `default` with the
whole `module.exports`. That is what broke `cytoscape.use(elk)`, and setting it today still changes
the bundle: `elkjs` and `react-dom/client` both take the flag and neither is harmed by it, one
because its `default` *is* itself and the other because it declares none. The page is safe by luck of
two dependencies rather than by anything under our control, and what it would buy is a warning.

The renderer decides nothing: `condensed.ts` returns what is on screen as data and `reactflow.tsx` draws it.
The React Flow page runs ELK itself and draws nothing until it answers, so a click changes the view
synchronously and the page catches up a layout later. ELK options are **strings**: the ELK API drops
a number silently, so it reads as ELK having ignored the request rather than as a type error.

## The legend

The `Legend` toolbar toggle (closed by default) lists what each fill, outline and line
means. The meanings live in `theme.ts` beside the colours (`NODE_MEANINGS`, `EDGE_MEANINGS`) and
`legend(payload)` reads its rows off the same constants the renderer paints with, so the legend
cannot drift from the page. It lists only the leaf types and edge kinds the payload has: a reference
of colours that are not on screen is noise. Spec: `docs/superpowers/specs/2026-11-17-legend-design.md`.

## The focus lane

A condensed click on a leaf or a line also focuses it: `focusPath` (condensed.ts) is everything that
flows into it, back to the entry point, and everything it flows into, on to the boundaries. The page
lays that path out in one ELK compound node, `lane:focus`, and fades the rest to `DIM_OPACITY`. Esc,
a click on the canvas or the lane, Reset, or a click on a group clears it.

- **Walked over payload edges, not drawn ones.** A group is a hub on the page; walking through it
  put most of the graph on the path. A leaf still in its group puts the group on the path.
- **ORDER is not walked.** It says which call ran first; following it pulls in every later call.
- **A group click focuses nothing**: it stands for values that need not be related.
- **ELK gives a line inside the lane in the lane's frame** (`container` on the edge). `routes` adds
  the lane's place back; without it every lane line starts and ends short of its nodes.
- **A node changing box starts where it was on the page** (`diffViews`), not at its old relative
  place, or it jumps into the lane at the start of the move.

Spec: `docs/superpowers/specs/2026-11-17-focus-path-design.md`.

## The condensed view

The only view (a boxed view, one box per method, was removed). Spec: `docs/superpowers/specs/2026-11-15-condensed-view-design.md`.
`condensed.ts` holds the rules as pure functions of
`(payload, revealed)`, types the only import. `R` and the `Reset` button reset to the opening.

**It opens on what happens and what is outside.** `condensedOpening(payload)` is the ids of the
payload's `EVENT` leaves - one per root, see `calls-and-dispatch.md` - so the first page is one
box per call site (a method's box, flat), each root's event on its own (`boundary`: seven nodes).
Reset asks for the opening again, so it returns to the same set. The event is a leaf like any other:
its edges into a group are one drawn edge with a count, and clicking it reveals what it
starts. **The event is not a hub.** `tap` follows an `EVENT` edge only from the clicked node:
clicking the event reveals what it starts, and no other walk steps onto it - walked through it, a
three-hop click next to any literal reached every unfed node of the root, callees and all
(`funcCall`: clicking `x` opened both `methodC` boxes). A leaf click is one hop, and a group's frontier gains the event's targets, which the event, drawn outside, makes
members with an outside edge - one hop, as clicking the event would.

**Groups are read from the payload, never guessed.** A leaf belongs to the group of the METHOD box it sits
directly in (`leaf.parent`): one group per call-site box, id = the box's payload id, caption the method's
simple name with the member count, in payload order. Flat: a nested call's box is a sibling of its caller's
box, never inside it; the edges between them show the call. A box that is a boundary call keeps `boundary:
true` (the purple outline). Merging a method's call sites into one group was rejected: it draws paths that
do not exist (call 1's argument appears to reach call 2's result). There is no `program` bucket: a leaf with
no parent box is an error, thrown. No leaf is ever dropped, and the corpus sweep holds that at every step of
a click-everything walk. Hover names the method and `called at File.java:N`.

**Click rules** (`tapCondensed`; each only adds to `revealed`):
- a leaf → its neighbours in both directions;
- a group → its *frontier*, the members with an edge to something drawn outside the group; with no
  frontier, every member, so a group click always does something — without that fallback the walk
  stalls on a group with no outside edge;
- a drawn edge → the endpoints of every payload edge it stands for, of every kind.

**Edges: one per ordered pair.** Each payload edge is drawn between `shown(u)` and `shown(v)` (the
leaf if revealed, else its group) and dropped when both land on one display node. Every edge onto
one ordered pair is **one** drawn edge, whatever its kinds (`A→B` and `B→A` stay two), carrying a
count per kind (`CondensedEdge.kinds`). Spec: `docs/superpowers/specs/2026-11-15-calmer-condensed-design.md`.
Why: one line per kind, each captioned with its kind, made the five-node opening of `boundary` a
tangle - 13 lines, 10 captions; now 12 lines, 4 captions, all counts. The kinds are not lost, they
moved one hover away. `condensedEdgeStyle` (`theme.ts`): a single-kind edge keeps that kind's colour
and dash, so colour still says the kind when there is one; a mixed edge is drawn as FLOW (grey,
solid), since no one kind's look would be true of it. The caption is `×N` when N > 1, never a kind
word, on the page's white (`LABEL_BG`). The hover starts with the kinds and counts in `EdgeKind`
order (`flow ×1, false ×1`). Spacing is 70 px between layers, 40 between nodes. A group whose last member came out is not drawn. The layout is
flat (`flatElkGraph`, no compound nodes); an edge's id is its pair.

**Lines follow ELK's routes** (spec: `docs/superpowers/specs/2026-11-15-routed-lines-design.md`).
React Flow draws an edge as a curve from a fixed handle on one node to a fixed handle on the other,
and knows nothing of what lies between: on `boundary`'s opening, 7 of the 12 lines ran through a
node box, 12 crossings in all, and a line going back up looped through its own ends. ELK has
already placed every node and, asked for `elk.edgeRouting: ORTHOGONAL`, routes every edge around
them; so the condensed view draws that route (`routes(laidOut)` in `layout.ts`, a `routed` edge type in the renderer: the polyline
with ~6 px rounded corners, the arrow at its end, `×N` at the middle of its longest segment).
Orthogonal over splines because right angles are the easiest to follow from one end to the other.
A flat graph's routes come back in the frame of its nodes, so nothing is re-based. An edge ELK gave
no section, or more than one, **throws** - a straight line drawn instead would be a guess through
whatever lies between its ends, with nothing on the page to say so. The fit includes the routes.
`routes-sweep.test.ts` lays out every condensed view of the corpus click-walk, seeded as the page
seeds it, and holds that every line has one route, at right angles, through no node box.

**During a move the lines are curves again.** A route is laid out for where the nodes will be, so
drawn while they glide it would not meet them. While `moving`, the condensed view strips the routes
and each line follows its two ends as before; when the move ends the routes come back on the same
frame as the settled nodes. A jump (first drawing, reduced motion, `MOTION_CUTOFF`, zero duration)
draws routes straight away. `window.drawn` is unchanged: it counts after the move, so a test waiting
on it reads routed lines. Side effect: a line can now run where a clicked node was, so the pointer
left there hovers it (`condensed.spec.ts` moves the pointer away before asserting no tooltip).

**Hover is an approximation, on purpose.** A leaf shows its call stack, innermost first, as a Java
trace prints it: its box's method and its own line, then each enclosing box's method and the line its
child box was `called` from (spec: `docs/superpowers/specs/2026-11-16-hover-call-stack-design.md`).
The stack is what tells apart the copies a per-call-site inlining draws of one line; it is read off
the box nesting plus one payload field, `called` (`GraphBuilderBlock.openedAt`, absent on a root),
never a per-node stack. A group, its count and each method's line range, capped at ten then `+N more`; an
edge, the method and line of its *targets* — where the value arrives, not the expression that moved
it, which for a phi or a call is not the same line. The text comes from the payload's `lines`
(only the lines some node names), read from disk by JsonExporter rather than from the block's own
file, because a field initialiser can be drawn in a block of another file.

**`Mermaid` exports what is on screen** (spec: `docs/superpowers/specs/2026-11-15-condensed-mermaid-export-design.md`).
The button downloads `codeflow-condensed.md`: a ```` ```mermaid ```` fence,
so it renders where it is opened (Obsidian, GitHub), saved through a Blob and a temporary `<a download>`
rather than the clipboard, which `file://` pages do not always get. `condensedMermaid(view)`
(`mermaid.ts`, pure, no DOM) takes the very `CondensedView` the renderer draws - `Page` computes it
once and hands it both to `CondensedGraph` and to the button - so the file cannot differ from the
screen. Nodes are `n0, n1, …` in view order, never derived from attributes; the label is
`condensedCaption`, with `"` `<` `>` `#` as Mermaid entities. Colours are the theme's, not copies:
one `classDef` per look in use (a leaf's type fill and `NODE_OUTLINE`, a group's `GROUP_FILL` and
its owner's outline - the renderer's CSS reads the same two constants), `rgba()` written as
`#rrggbbaa` by `hex`, since a classDef is split on commas; each edge a `linkStyle` from
`condensedEdgeStyle`, indexed in the order the edges are written, `×N` when N > 1. Hover text and
routes are left out: Mermaid lays out itself. No Mermaid parser is a dependency, so the unit tests
read the export back with a line grammar of their own rather than through Mermaid.

The page's `window.drawn` counts only real condensed layouts (the target drawing starts `null`), so a
browser test waiting on it waits for ELK, not for the empty first render.

## Expanding without losing your place

A click used to re-run ELK from scratch and refit, and the new drawing could look nothing like the
old one. Spec: `docs/superpowers/specs/2026-11-15-expand-animation-design.md`. Three things.

**Stable layout: ELK's interactive options, seeded from the last drawing** (`stabilise` in
`layout.ts`). Each node drawn before gets its old position (`x`, `y` and `elk.position`); a new one
sits half a pixel past its first already-drawn neighbour *in the same graph* (a position means
nothing across a group boundary); every graph gets `elk.layered.layering.strategy: INTERACTIVE` and
`crossingMinimization.semiInteractive: true`. Child and edge order are left alone. Measured by
`node app/src/test/js/stability.ts` — a click-everything walk over every fixture `graph.json`
(103 clicks); per click, mean displacement of nodes drawn before and
after (net of the clicked node's own move) and order inversions among them:

| | px / inversions |
|---|---|
| from scratch (before) | 80.4 / 22.77 |
| model order (`considerModelOrder: NODES_AND_EDGES`, input sorted by old position) | 74.6 / 26.91 |
| **interactive (chosen)** | **81.3 / 16.86** |

Model order moves nodes less but flips more of them. Interactive cuts inversions by a quarter and costs
0.9 px of displacement (about 1 %) — kept, because an order that flips is what loses the reader, and
a node that drifts a pixel is not. Things tried and dropped: feeding the input in the old drawing's
order (row- or column-major) *with or without* interactive — it changes ELK's layering and cycle
breaking and made both numbers worse in every variant; seating new nodes by BFS instead of at one
neighbour — no gain. `layout-stability.test.ts` pins a one-neighbour reveal keeping every old pair's
order on a payload where ELK from scratch does flip one.

**The move** (`useMotion` in `reactflow.tsx`, positions from `transition.ts`). The old drawing stays
on screen until the new layout lands; then kept nodes glide  over 350 ms, ease-out;
new nodes grow out of the clicked node's old place *on the page* (made relative to their own box's
start) and fade in; gone nodes fade out where they were and are dropped when the move ends. The
viewport moves against the clicked node every frame, so it holds still on screen; afterwards the page
refits only if something new is off screen, or if no click asked for the drawing (Reset: nothing
anchors the reader, who may have panned anywhere). An edge click has no anchor either but keeps the
off-screen rule. A second click mid-move starts from the positions on screen, minus the nodes that
were fading out - they are gone already, and starting from them would draw them whole and fade them
again. Positions are renderer state: `condensed.ts` decides what is on screen and know
nothing of motion, and `transition.ts` compares two drawings by node id (payload ids, a group's being
its box's, all stable across clicks) with no DOM, so `node --test` covers the arithmetic.

What appeared gets a fading ring (`cf-new`, `NEW_RING`, 1.5 s), nodes and edges alike; the node last
clicked keeps a steady outline (`cf-clicked`) until the next click (an edge click clears it).

**When it jumps instead.** `prefers-reduced-motion: reduce`, more than `MOTION_CUTOFF` (2,000) drawn
nodes in the next view — every frame re-renders every node, and at that size it is a slideshow — and
`window.cfAnimationMs = 0` (tests). A jump keeps the ring (static under reduced motion) and the anchor.

**`window.drawn` counts after the move and any refit have finished**, and a move cut short by the
next layout is never counted, so a test waiting on it reads the settled drawing. The ReactFlow root
carries `cf-moving` while a move runs, for the browser tests (`animation.spec.ts`).

**No `extent: 'parent'`.** React Flow clamps a child to its parent's *measured* size, and a box resized
this frame has not been measured yet: every frame of a move drew its children in its top-left corner.
Nothing is draggable, so the extent bought nothing. The 40 px anchor test is what caught it.
