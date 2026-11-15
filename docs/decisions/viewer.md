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
| `layout.ts` | the layout posed as a value: `elkGraph(view)` and `positions(laidOut)` |
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

