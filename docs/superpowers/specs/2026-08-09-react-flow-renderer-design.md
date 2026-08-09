# A second renderer: React Flow, in TypeScript, over one view model

## The problem

The viewer is Cytoscape and nothing else, and the choice is load-bearing in a way nothing states.
Cytoscape *derives* three things the payload never says: an edge disappears when either endpoint
does, a compound node disappears when every descendant does — transitively — and a box's extent is
whatever its children need. `2026-08-09-viewer-view-model-design.md` calls that out as the reason
not to swap renderers, and it is right about the cost. But the cost is only visible once something
else has to be told all of it explicitly, and until then "what is on screen" is a question only a
Playwright click can ask.

A second renderer is the forcing function. Anything the model cannot state, the second renderer
draws wrong, and it draws it wrong *differently* from the first — which is a disagreement a test can
read. That is worth more here than a nicer-looking page.

React Flow is the second renderer, and it becomes the default because it is the one worth investing
in: React components for node bodies, a live ecosystem, and an obvious path to the things
`docs/if-written-again.md` wants next. Cytoscape stays for historical reasons — reachable, tested,
and drawing from the same model, so it cannot drift.

## What this rests on

The view-model separation specified in `2026-08-09-viewer-view-model-design.md` and planned in
`docs/superpowers/plans/2026-08-09-viewer-view-model.md`: `opening`, `tap` and `screen` as pure
functions in `model.mjs`, the stub-click fix, `graph.json` per fixture, and the corpus sweeps. That
work lands first, in JavaScript. **This spec starts from it finished** and does three things it did
not: converts the viewer to TypeScript behind a bundler, widens `screen()` to say what Cytoscape was
deriving, and adds the React Flow renderer.

## What changes

### 1. `screen()` states box visibility, because React Flow derives nothing

The prior spec has `screen()` return `display: null` for every `METHOD` node — a Cytoscape rule
("never set `display` on a box") encoded as an absence. React Flow has no compound visibility at
all: a box it is not told to hide stays on screen as an empty rectangle, and a box it *is* told to
hide takes its visible grandchildren with it. So the model states the truth for both, and each
renderer takes what it needs:

```ts
type ViewNode = {
  id: Id; label: string; type: NodeType; parent?: Id;
  badge: string;      // label plus what is missing around it
  visible: boolean;   // on screen. For a box: some descendant leaf is showing.
};
```

- **Cytoscape adapter** ignores `visible` on a box — one `continue`, carrying the comment it has
  today, because Cytoscape works boxes out itself and a `display` of ours would hide a box whose
  only visible node is a grandchild.
- **React Flow adapter** writes `hidden: !visible` on every node, boxes included.

This turns the prior spec's P3 into a stronger property, and one that only exists because there are
two renderers:

> **A box is `visible` exactly when some descendant leaf is showing.**

That is the rule Cytoscape applies internally. Asserting it in Node is what keeps the two pages
drawing the same picture, and it fails loudly in the case that has always been the trap — a box
whose only visible node is a grandchild.

`edges[].visible` (both endpoints showing) is already in the prior spec and is what React Flow needs
in place of the edge-dies-with-its-endpoint rule.

### 2. Layout becomes something the model can be asked about

Cytoscape runs ELK through `cytoscape-elk` and never tells us the result. React Flow positions
nothing on its own, so ELK has to be driven directly — which means the *input* to layout becomes a
value, and a value can be tested:

```ts
elkGraph(view): ElkNode      // pure: the visible subgraph, nested, with estimated sizes
```

Hierarchy comes from `parent`; only visible nodes and visible edges are included; sizes are
estimated from label length the way Cytoscape's `width: 'label'` measures them. The options are the
ones the page uses today — `layered`, `elk.direction: DOWN`, `hierarchyHandling: INCLUDE_CHILDREN`,
the same two spacings. ELK returns child coordinates relative to their parent, which is already
React Flow's `parentId` convention, so nothing is re-based.

Running ELK and writing positions into React Flow state is the renderer's half and stays untested by
Node. Building its input is not.

### 3. TypeScript, behind esbuild

```
app/src/main/resources/viewer/
  model.ts              pure view model. node --test reads this directly.
  theme.ts              PALETTE + EDGE_COLOURS, shared by both renderers
  layout.ts             View -> ELK graph. Pure.
  reactflow.tsx         the React Flow renderer
  cytoscape.ts          the Cytoscape renderer (was viewer.mjs)
  reactflow.bundle.js   GENERATED, committed
  cytoscape.bundle.js   GENERATED, committed
  template.html         one /*__BUNDLE__*/ slot and /*__PAYLOAD__*/
```

`npm run build:viewer` runs esbuild twice (`--bundle --format=iife --minify`), resolving `react`,
`react-dom`, `@xyflow/react`, `elkjs`, `cytoscape` and `cytoscape-elk` from `node_modules`. React
Flow's stylesheet is imported with `--loader:.css=text` and injected as a `<style>` at runtime, so
the page stays one file.

The hand-copied `cytoscape.min.js`, `elk.bundled.js` and `cytoscape-elk.js` are deleted. Once a
bundler resolves them from `node_modules`, a committed copy is a second and staler answer to "which
version is this".

**Gradle still never runs npm.** It substitutes a committed bundle, which is exactly the contract the
vendored files had. `HtmlExporter` stays substitution only; it gains a constructor parameter naming
which bundle to inline.

Node 26 strips types, so `model.ts` and the unit tests need no build at all — `node --test` reads
the sources. Only the two bundles are generated, and only the renderers need generating.

### 4. `--html` is React Flow

`--html` emits the React Flow page. `--html-cytoscape` emits the Cytoscape one. Both are real
products of the tool, both browser-tested; the second exists so that a React Flow drawing that looks
wrong has something to be checked against, which is the only reason to keep two renderers at all.

`AppTest.writePage` writes `graph.html` (React Flow), `graph-cytoscape.html` and `graph.json` beside
each fixture — all three gitignored and rewritten every run, like `ir.txt`.

## Parity, stated as a list

The React Flow page must do what the Cytoscape page does. Anything below that is missing is a
regression, not a difference in style:

| | |
|---|---|
| Boxes | one per method, captioned with its name, nested to any depth |
| Node captions | `badge` — the name plus `↑2 ↓3` for hidden neighbours |
| Node colour | `PALETTE[type]`, from `theme.ts` |
| Edge colour | `TRUE` `#2e7d32`, `FALSE` `#c62828`, `CONDITION` `#6a6a6a`, everything else grey |
| Edge labels | the kind, lowercased; `CONDITION` reads `if` and is dashed |
| Click a leaf | reveal its neighbourhood, `REVEAL_DEPTH` hops, undirected |
| Click a closed box or a stub | open it, one level |
| Click an open box | fold every leaf descendant |
| `R` | back to the opening view |
| Layout | ELK layered, top-down, children laid out with their parents |

## Testing

**Unit (`npm test`, `node --test` over `*.test.ts`).** The existing files convert to TypeScript and
otherwise stand. New: `layout.test.ts`, over `elkGraph` — that the hierarchy nests, that hidden
nodes and hidden edges are absent, that a box's size is left to ELK rather than guessed. The corpus
sweeps gain the box-visibility property above.

**The generated-bundle guard.** `npm test` rebuilds both bundles into a temp directory and compares
them byte for byte with the committed ones, failing with "run `npm run build:viewer`". A stale
bundle is the one new way this design can be silently wrong — the page rendering yesterday's model
while every test passes on today's source — so it gets a check rather than a convention.

**Browser (`npm run test:browser`, Playwright, TypeScript specs).** `global-setup` builds both pages
for both fixtures. A `probes.ts` gives each renderer the same five operations — `ready`,
`leafLabels`, `boxLabels`, `tapLeaf`, `tapBox` — and the existing specs run parameterized over both,
so `member` and `funcCall` assert identical behaviour on both pages.

The React Flow probe reads the **DOM**: captions out of `.react-flow__node`, never out of a view
object we published. A test reading our own `screen()` output would pass on a page that draws
nothing, which is the trap this repo already names for `n.style('label')`.

**Mutations each new test must be confirmed against**, since an absent implementation is not enough:

| Mutation | Should fail |
|---|---|
| React Flow renders every node regardless of `visible` | the leaf/box label assertions on both fixtures |
| a box is visible iff its *own* leaves show (not descendants) | the box-visibility sweep; the grandchildren browser test |
| `elkGraph` flattens the hierarchy | `layout.test.ts`; boxes render at the origin |
| edit `model.ts` and do not rebuild | the bundle guard |

## Risks

**React Flow's nested nodes, offline.** Everything above rests on React Flow drawing parent nodes
with children positioned inside them, in a page opened from `file://` with all assets inlined. This
is proven by a throwaway page — three nodes, one nested in a box, ELK-positioned — *before* the real
renderer is written. If nested nodes need something React Flow cannot give offline, that is a finding
to report, not something to work around quietly.

**Node sizing.** Cytoscape measures a label and sizes the node to it; ELK has to be told a size
before anything is rendered. The estimate (characters × width + padding) will be wrong by a few
pixels, which shows up as looser or tighter spacing, never as a wrong graph. Measuring properly means
render → measure → re-layout, which is a second layout pass on every click. Not now.

**Two renderers, one model, and one of them barely used.** The Cytoscape page's protection against
rotting is that it consumes the same `model.ts` and runs the same browser spec. If that ever becomes
inconvenient enough to skip, the honest move is to delete it, not to freeze it.

## Not in scope

No change to the payload format, to any exporter's graph, or to `truth.md` — nothing here alters the
graph, only how it is drawn. No second layout pass to measure real node sizes. No React Flow feature
beyond parity: no minimap, no controls panel, no editing. No CI wiring for the bundle guard; it runs
in `npm test`, which is where this repo's other guards run.
