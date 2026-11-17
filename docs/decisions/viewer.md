# The interactive viewer

Moved verbatim from CLAUDE.md, which now keeps only the rules. This is the reasoning behind them.

`HtmlExporter` substitutes one **bundle** and the JSON payload into `template.html`. It is
substitution only — all of the behaviour lives in the bundle. `HtmlExporter(bundle)` has no default
argument on purpose: a default would name a file, and a name that is not in the jar fails at the one
moment nobody is watching, so every call site says which page it wants.

**One renderer, and a view model that does not know about it.** `--html` emits the React Flow page.
There were two for a while — a Cytoscape page kept as a second opinion, since it *derived* an edge's
visibility from its endpoints and a box's from its descendants where React Flow is told both, so a
page the two drew differently was a fact about `model.ts` neither could have produced alone. It was
removed once every property it could disagree about had a unit test on `screen` standing behind it,
which is where the argument goes if a second renderer is ever wanted again: the value was the
independent derivation, and a test that states the rule is a cheaper way to hold it.

The viewer is TypeScript, under `app/src/main/resources/viewer/`, and the boundary between deciding
and drawing is the point:

| File | What it is |
|---|---|
| `model.ts` | every decision about what is on screen — `opening`, `tap`, `screen` — as pure functions of `(payload, revealed)`, no DOM and no library |
| `condensed.ts` | the same for the condensed view — `condensed`, `tapCondensed`, `hoverText` — see [The condensed view](#the-condensed-view) |
| `mermaid.ts` | `condensedMermaid(view)`: the condensed view on screen as a Mermaid flowchart — see [The condensed view](#the-condensed-view) |
| `layout.ts` | the layout posed as a value: `elkGraph(view)`, `flatElkGraph(view)` and `positions(laidOut)`, seeded from the last drawing by `stabilise`; the condensed lines' `routes(laidOut)`, `captionAt`, `roundedPath` |
| `transition.ts` | how one drawing becomes the next — `diffViews`, `frame` — by node id, pure; see [Expanding without losing your place](#expanding-without-losing-your-place) |
| `types.ts`, `theme.ts` | the payload and view types, and the colours the page paints with |
| `reactflow.tsx` | the renderer, and what `--html` emits |

The renderer decides nothing: it calls `tap` on a click and writes what `screen` returns. Before the
split, three decisions lived only inside a browser — the opening set, read out of a graph query for
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

`screen(payload, revealed)` returns the whole view as data: which leaves are showing, which of those
are names standing in for closed methods, the hidden-neighbour counts, and every node and edge of the
payload with a `visible`. Every node, not only the visible ones — the view is the payload with a
verdict on each node rather than the subset that survived, so a renderer holding its own graph
between frames has a `false` to write onto a node that just left rather than having to notice it is
missing.

`visible` on a `METHOD` node means *some descendant leaf is showing*, transitively, because a box
holds boxes. Stating it in the model rather than leaving it to the renderer is what makes it
readable by a unit test: as an `if` in the middle of a render loop it was a rule nothing could reach,
and the failure it guards — a box whose only visible node is a grandchild being hidden, leaving that
grandchild nowhere to live — is reachable by clicking. *a box whose only showing node is a grandchild
is visible* in `screen.test.ts` is what holds it now.

**Method boundaries are nesting**: a React Flow node's `parentId`, which is what `subgraph` was doing
in Mermaid and what `parent` is in the JSON payload. Layout is ELK `layered` with
`elk.hierarchyHandling: INCLUDE_CHILDREN` — without that it lays each container out independently
and the boxes overlap — plus `elk.padding` with a taller `top`, since ELK pads evenly and the first
row of nodes would otherwise sit on the box's caption. `ELK_OPTIONS` is applied to the root and to
every box, so a box cannot be laid out by different rules than its parent. Those options are
**strings**, which is not a style choice: the ELK API drops a number silently, so it reads as ELK
having ignored the request rather than as a type error. An adapter that coerced numbers on the way
through is what hid this for as long as there was one.

The React Flow page runs ELK itself and draws nothing until it answers, so a click changes the view
synchronously and the page catches up a layout later. Two consequences worth knowing: a parent must
appear before its children in the node array or React Flow silently drops the child — hence the sort
by depth in `nodesOf` — and ELK returns a child's coordinates relative to its parent, which is
already React Flow's `parentId` convention, so re-basing them would be the bug.

The page opens with the entry method's own leaf children revealed, plus the *name* of each method it
calls and nothing else. This is not cosmetic: a callee's body is inlined at *every* call site, so
node count grows with call sites rather than source size, and showing everything at once is the wall
the viewer exists to avoid. What the name of a closed method is drawn as, and why it has to be drawn
at all, is "A closed method is drawn as its own result" below.

A `Set` of revealed *leaf* ids is the only state. Everything else is derived from it by `screen` on
every call, so nothing has to be kept in step and nothing can go stale — `showing` in particular is
never stored, so folding a box cannot strand a stub that was added when it opened.

**A hidden neighbour is announced, or the reveal lies by omission.** An edge goes when either
endpoint does, so a node with six hidden neighbours renders *identically* to a genuine source
or sink — a value arriving from somewhere invisible drawn as a value arriving from nowhere, which is
the silently-incomplete diagram arriving through the one door progressive reveal opens. So
`hiddenDegree(edges, revealed)` counts, per revealed node, the edges whose other end is off screen,
split by direction, and `badgeLabel` renders them into the node's caption: `total ↑2 ↓3`, `amount
↓3`, and nothing at all when the node is fully surrounded. An edge counts at a node only when
exactly one of its endpoints is revealed — both on screen is nothing missing, both off belongs to
neither — so the two cases are one test.

Counting *reachable* nodes instead is the tempting alternative and does not work: the graph is close
to connected and the walk is undirected, so every node would report approximately the payload's
size, adjacent nodes would count the same hundreds twice, and it is a walk per node per `apply()`.
`docs/superpowers/specs/2026-08-08-hidden-neighbour-counts-design.md` has that argument and the one
against ranking by the click's own harvest.

The badge lives in `badge` and **`label` stays the plain name**, because the annotation is a
property of the current view rather than of the value — every existing browser assertion looks a
node up by label, and so will the next one. The browser probe reads what is *drawn* for the matching
reason: the badge being right in the data while the page still paints the plain name is a page that
draws none of this, and reading the data would call that green. So it goes through the DOM and not
through the `view` object the page publishes.

`neighbourhood(edges, startId, depth)` is the click behaviour: an undirected ball of radius
`REVEAL_DEPTH`. It is breadth-first on purpose — the walk is bounded, so a node first reached by a
long path would be recorded at the wrong distance and pruned early. Undirected on purpose too: for
`c = a + b`, clicking `a` shows `b`, because an operator drawn with one operand missing is worse
than one more node.

Clicks union into the revealed set and never subtract. Only a box click on an *open* box (which
removes its leaf *descendants* — a box holds boxes) or `R` takes anything away.

### A closed method is drawn as its own result

**Dataflow is a forest, so following edges is not enough to navigate by.** Every fixture in the
suite is disconnected, and a call that passes no value connects nothing at all: `member`'s `main`
does `app.func1()` — no argument, no result — so not one edge crosses from `main` into `func1`. That
page opened on four nodes, clicking every one of them changed nothing, and 19 of the fixture's 23
leaves could not be put on screen by any sequence of clicks. Swept over all 63 fixtures, **none**
could be fully reached from any single starting node; the median best case was 71%. The opening view
worked only because it is built from *containment*, and containment was the relation nothing else
used. Choosing a different root would not have helped: the missing relation was the call structure,
not the starting point.

`withStubs(nodes, revealed)` makes it navigable. A box whose parent is open is offered as its
**RETURN node**, which every box has exactly one of — the method's name, or `<init>` for a
constructor. That node is the method's result, so a method you have not opened is drawn as the one
value it produces. Clicking that name reveals the method's own leaves and the names of the methods
it calls, and still walks the name's own edges like any other leaf. Folding puts the stub back
rather than removing the box, so there is a way in again.

It has to be a real leaf, and that was once forced by the library: Cytoscape would not draw a
compound node with no visible children whatever display the parent was given — a spike confirmed
`visible: false` and `0×0` on a box forced to `element` — so an empty box could not be a click target
at all. React Flow draws one happily, so the reason is now ours: an empty box is a rectangle with a
caption and no value in it, and it is the stub, not the box, that gives a closed method a node in the
**dataflow** graph. A click walks the stub's own edges like any other leaf; a box with nothing inside
it could not be reached by following a value.

Two rules hold the recursion down, and each is one line with a test behind it. `openBoxes` decides
which boxes are open, and a box whose only revealed leaf is its own name **does not count as open**:
if it did, offering one callee's name would open that callee, which would offer its callees' names,
and one pass would unfold the whole call tree — which is also why one pass suffices, since no name
can produce another. And only a **closed** box is offered a name: an open one already has its RETURN
among the leaves the click revealed, and a box opened by following dataflow instead should show what
the walk reached and nothing more. That second rule is what keeps the off-by-one guard in *clicking
a node reveals its neighbourhood three hops out* meaningful — `methodA`'s RETURN is exactly four hops
from `x`, and offering names for open boxes would have put that label on screen for an unrelated
reason and made the assertion untestable.

**That first rule governs the derivation, and a click is not a derivation.** The two read alike and
were one sentence, and merging them is what left `member` unopenable. Its callee's name is a
`RETURN` node for a method that passes and returns nothing, so it has no edges at all, and the
neighbourhood walk returned it to itself: the page opened on 5 of 23 leaves with all five inert, and
the only way in was the thin box border around a node that looked like the obvious thing to press.
Clicking a name now opens its method, and cannot cascade, because a click happens once — the
newly-open method offers only the names directly inside it, each needing a press of its own.

The callees' names arrive with the click rather than being left to the next derivation, and that is
load-bearing rather than an optimisation: a box is drawn by its contents, so a method whose own
leaves are just its own name — one that only calls other methods — would otherwise open onto
nothing it could be drawn from. `deepField` is that shape. The sweep is what found it.

`openBoxes` is one exported function and not four spellings, because `withStubs` deciding what to
offer, `screen` deciding which of those are names rather than bodies, `tap` deciding whether a box
click opens or folds, and the invariants sweep all ask the same question. Four answers to "is this
box open" is four chances for the viewer to disagree with itself about what the reader is looking at
— and `screen` in particular cannot use "showing but not revealed", since a click on a name puts it
in `revealed` while leaving the box it stands for shut, and it is still a name.

`tap`'s box branch is the one that escaped, and the failure it produced is exactly the one this
change exists to remove. Once a name click writes callee names into `revealed`, a shut box has a
revealed leaf inside it, so the branch's own test — *any revealed leaf under here* — read it as open
and **folded** it, deleting the name that `withStubs` then offered straight back. `screen`'s output
was byte-identical before and after: five fixtures had a box the reader could press forever.
`clicking a box whose only revealed leaf is its own name opens it` is what pins it, and nothing else
in the suite can — the corpus sweep passes either way, because containment reaches those leaves down
some other path.

A box and the name standing in for it are two ways to press the same door, so `oneLevel` is what
both reveal: the box's own leaves, plus one name per method it calls. Two definitions would make how
much of a fixture a reader can reach depend on which of the two they happened to hit. What is left
inert is a method whose whole body *is* its own RETURN — `member`'s `getMemberX`, `generic`'s `get`
— where the name on screen is already the entire method and there is nothing behind the door.

`showing` is derived inside `screen()` on every call and never stored, so folding a box cannot
strand a stub that was added when it opened.

## The condensed view

A second view on the same page and payload, behind a `Boxes` / `Condensed` switch (Boxes is the
default and draws what it drew before). Spec: `docs/superpowers/specs/2026-11-15-condensed-view-design.md`.
`condensed.ts` holds the rules under the same contract as `model.ts` — pure functions of
`(payload, revealed)`, types the only import — and the page keeps one `revealed` per view, so
switching away and back returns to what was open. `R` and the `Reset` button reset the view on screen.

**It opens on what happens and what is outside.** `condensedOpening(payload)` is the ids of the
payload's `EVENT` leaves - one per root, see `calls-and-dispatch.md` - so the first page is one
group per boundary method, each root's event on its own, and `program` (`boundary`: five nodes).
Reset asks for the opening again, so it returns to the same set. The event is a leaf like any other:
its edges into a group are one drawn edge with a count, and clicking it reveals what it
starts. In Boxes it is a leaf of the root's box, and Boxes edges take no pointer events
(`.cf-boxes`): they answer no click, and the event's edge into a closed callee's stub crosses the
box's title. **The event is not a hub.** `tap` follows an `EVENT` edge only from the clicked node:
clicking the event reveals what it starts, and no other walk steps onto it - walked through it, a
three-hop click next to any literal reached every unfed node of the root, callees and all
(`funcCall`: clicking `x` opened both `methodC` boxes). Condensed needs no such rule: a leaf click
is one hop, and a group's frontier gains the event's targets, which the event, drawn outside, makes
members with an outside edge - one hop, as clicking the event would.

**Groups are read from the payload, never guessed.** A leaf belongs to a boundary method's group iff
its *nearest* enclosing box carries `boundary: true`; the group is keyed by that box's `method`
(`pkg.Class#name(ParamTypes)`, JsonExporter's `Method.key()`), which is what makes every block of
one method — one per call site, across roots — a single group. Keyed by box id instead, `boundary`
would open on seven nodes, not five. Everything else, including a leaf with no box, is `program`; no
leaf is ever dropped, and the corpus sweep holds that at every step of a click-everything walk.

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
order (`flow ×1, false ×1`). Condensed spacing is its own (70 px between layers, 40 between nodes,
against the boxed view's 40 / 25). A group whose last member came out is not drawn. The layout is
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
seeds it, and holds that every line has one route, at right angles, through no node box. Boxes still
draws handle-to-handle curves: its coordinates nest per box, and routing it is a separate step.

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
The button, shown only in Condensed, downloads `codeflow-condensed.md`: a ```` ```mermaid ```` fence,
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
browser test waiting on it after switching views waits for ELK, not for the empty first render.

## Expanding without losing your place

A click used to re-run ELK from scratch and refit, and the new drawing could look nothing like the
old one. Spec: `docs/superpowers/specs/2026-11-15-expand-animation-design.md`. Three things, in both
views; switching views is a plain redraw (each view remounts, lays out from scratch, does not animate).

**Stable layout: ELK's interactive options, seeded from the last drawing** (`stabilise` in
`layout.ts`). Each node drawn before gets its old position (`x`, `y` and `elk.position`); a new one
sits half a pixel past its first already-drawn neighbour *in the same graph* (a position means
nothing across a box boundary); every graph gets `elk.layered.layering.strategy: INTERACTIVE` and
`crossingMinimization.semiInteractive: true`. Child and edge order are left alone. Measured by
`node app/src/test/js/stability.ts` — a click-everything walk over every fixture `graph.json`
(163 Boxes clicks, 103 Condensed clicks); per click, mean displacement of nodes drawn before and
after (net of the clicked node's own move) and order inversions among them:

| | Boxes px / inversions | Condensed px / inversions |
|---|---|---|
| from scratch (before) | 207.3 / 88.40 | 80.4 / 22.77 |
| model order (`considerModelOrder: NODES_AND_EDGES`, input sorted by old position) | crashes elkjs 0.9.3 | 74.6 / 26.91 |
| **interactive (chosen)** | **126.9 / 29.91** | **81.3 / 16.86** |

Model order combined with `hierarchyHandling: INCLUDE_CHILDREN` throws from inside an elkjs timer
(uncatchable), so it cannot be used for Boxes at all, and in Condensed it moves nodes less but flips
more of them. Interactive cuts both in Boxes; in Condensed it cuts inversions by a quarter and costs
0.9 px of displacement (about 1 %) — kept, because an order that flips is what loses the reader, and
a node that drifts a pixel is not. Things tried and dropped: feeding the input in the old drawing's
order (row- or column-major) *with or without* interactive — it changes ELK's layering and cycle
breaking and made both numbers worse in every variant; seating new nodes by BFS instead of at one
neighbour — no gain. `layout-stability.test.ts` pins a one-neighbour reveal keeping every old pair's
order on a payload where ELK from scratch does flip one.

**The move** (`useMotion` in `reactflow.tsx`, positions from `transition.ts`). The old drawing stays
on screen until the new layout lands; then kept nodes glide (and boxes resize) over 350 ms, ease-out;
new nodes grow out of the clicked node's old place *on the page* (made relative to their own box's
start) and fade in; gone nodes fade out where they were and are dropped when the move ends. The
viewport moves against the clicked node every frame, so it holds still on screen; afterwards the page
refits only if something new is off screen, or if no click asked for the drawing (Reset: nothing
anchors the reader, who may have panned anywhere). An edge click has no anchor either but keeps the
off-screen rule. A second click mid-move starts from the positions on screen, minus the nodes that
were fading out - they are gone already, and starting from them would draw them whole and fade them
again. Positions are renderer state: `model.ts` and `condensed.ts` decide what is on screen and know
nothing of motion, and `transition.ts` compares two drawings by node id (payload ids, `program`,
`boundary:<method>`, all stable across clicks) with no DOM, so `node --test` covers the arithmetic.

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
