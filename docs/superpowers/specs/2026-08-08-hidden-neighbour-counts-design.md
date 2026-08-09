# Hidden-neighbour counts in the viewer

## The problem

`apply()` sets `display: none` on every unrevealed leaf, and Cytoscape hides an edge when either
endpoint goes. So a node with six hidden neighbours renders **identically** to a genuine source or
sink. A value that arrives from somewhere invisible reads as a value that arrives from nowhere, and
nothing on the page distinguishes the two.

This is the same failure the rest of codeflow is built to avoid — a readable, plausible diagram
that is quietly incomplete — arriving through the one door progressive reveal opens. The viewer
exists because a callee's body is inlined at every call site, so showing everything at once is
unusable; the cost of not showing everything is that the reader cannot tell what is missing.

## What it does

A visible node whose neighbours are not all on screen carries its missing-edge counts in its label:

```
┌───────────────┐   ┌────────────┐   ┌────────┐
│ total ↑2 ↓3   │   │ amount ↓3  │   │  base  │
└───────────────┘   └────────────┘   └────────┘
 two hidden in,      only outgoing    fully surrounded,
 three hidden out    hidden           no annotation
```

`↑n` is "n values reach this that you cannot see". `↓n` is "it goes on to n places you cannot see".
A direction with nothing hidden is omitted; a node with neither gets its bare name back.

Viewer-only. No exporter changes, no payload changes, no new graph elements — `cy.nodes().length`
stays the payload's node count, and the Mermaid, GraphML and JSON renderings are untouched.

## Why hidden degree and not reachability

"How many nodes are reachable from here" was the obvious alternative and it does not work. The graph
is close to connected and `neighbourhood` is undirected, so reachability from almost any node is
approximately the whole payload: every node would show a number within a few percent of every other,
adjacent nodes would count the same hundreds of nodes twice, and the totals would sum to many times
the graph. A number that is the same everywhere is not information. It is also an O(V+E) walk per
node per `apply()`.

Hidden degree localises. It is exact, it is one pass over the edges for the whole graph, and it
drops to zero precisely when a node is fully surrounded — which makes the annotation's disappearance
meaningful rather than merely quieter.

A third candidate was rejected later and is worth recording: **the click's own harvest**,
`|neighbourhood(id, REVEAL_DEPTH) \ revealed|`, the number of new nodes a click would actually
open. It ranks clicks correctly by construction, which hidden degree only approximates — a node
showing `↓3` may open three nodes or forty, since the reveal is a ball of radius 3. It was not
chosen because it loses direction, and direction is half the meaning: "two values arrive here that
you cannot see" is a statement about dataflow, where "12 nodes are near here" is a statement about
the picture.

## The mechanism

Two pure functions in `viewer.mjs`, exported so `node --test` reaches them directly, matching how
`neighbourhood` is already tested:

- `hiddenDegree(edges, revealed)` → `Map<id, {in, out}>`. One pass over the payload's edges. For
  each edge, if exactly one endpoint is revealed, that endpoint's count goes up — `out` if it is the
  source, `in` if it is the target. An edge with both endpoints hidden contributes to neither. O(E)
  for the whole graph rather than per node, which is the same order `neighbourhood` already pays on
  every click.
  The map carries an entry only for a node with at least one hidden neighbour, so a hidden node
  never appears in it (neither of its edges has exactly one revealed endpoint counting *towards*
  it), and neither does a fully surrounded one.
- `badgeLabel(name, hidden)` → the rendered string. Omits each direction that is zero, and returns
  the bare name when both are — including when `hidden` is absent, which is the case for every node
  the map left out.

`apply()` sets `node.data('badge', …)` alongside the `display` it already sets, and the stylesheet's
`label` changes from `data(label)` to `data(badge)`.

**`data('label')` deliberately keeps the plain name.** The badge is a separate field, so the name
stays queryable: the existing browser assertions and anything that later looks a node up by what it
is called go on working, and no reader of the graph programmatically can mistake the annotation for
part of the variable's name.

No edge in the payload names a `METHOD` node — `JsonExporter.collect` takes edges from
`node.edgesIterator()` on graph nodes only, and adds each block as a node with no edges of its own.
So the counts are over leaves, `revealed` only ever holds leaf ids, and a box's badge is just its
name.

### Why the counts are in the label

A Cytoscape node has exactly one label. There is no second text layer and no badge slot, so the
counts either live inside the label string or become real elements. Adding elements would break the
"nothing is ever removed from the graph, so `cy.nodes().length` is always the payload's node count"
invariant and the browser test resting on it, which is too much to spend on an annotation.

Within the label, a stacked multi-line form was considered — `↑2` above the name and `↓3` below,
which under `elk.direction: DOWN` puts each number where its missing arrows would actually enter or
leave, and so needs no legend. The single-line suffix was chosen instead because it never changes a
node's height, so adding the annotation perturbs the existing layout in one dimension rather than
two.

## Tests

Unit, a new file beside `neighbourhood.test.mjs`:

- an edge to a revealed node counts nothing
- direction lands on the right side
- an edge with both ends hidden contributes to neither
- a fully surrounded node produces the bare name
- each direction is omitted from the string when it is zero

Browser, in `viewer.spec.mjs`, on the `funcCall` fixture: at open, a node feeding a call carries
`↓`; clicking it and re-reading the same node shows the `↓` **gone**. The pairing is the assertion —
the first half fails if badges are never drawn, the second fails if they are drawn once and never
recomputed, and neither passes against the reveal-everything mutation. This follows the rule in
CLAUDE.md that a negative assertion is paired with a positive one that fails when the feature does
nothing.

## Consequences

At open, the entry method shows `↓` on every value that flows into a call. That is the page's first
honest statement that there is more, and it appears before anyone clicks.

The numbers change on every click, which is intended: they are a property of the current view, not
of the program.
