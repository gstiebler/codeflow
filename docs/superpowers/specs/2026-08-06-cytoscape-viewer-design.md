# Interactive viewer — design

**Date:** 2026-08-06
**Status:** approved, not yet implemented

## Problem

`MermaidExporter` renders the whole graph at once. Because `AstBlockProcessor` inlines a callee's
body at *every* call site rather than summarising a method once, node count grows with call sites,
not with source size. On anything past a fixture the diagram becomes a wall: readable in principle,
unusable in practice.

The method boundary is already in the model — each `GraphBuilderBlock` is one — and Mermaid draws it
as a `subgraph`. What is missing is the ability to *fold* it, so a reader can open the one call they
care about and leave the rest shut.

A `GraphmlExporter` (added 2026-08-06) covers the desktop-editor route: yEd does turn the nested
graphs into foldable group nodes. It stays. This design covers the case yEd cannot serve — a viewer
that opens with the right thing already on screen and answers "where does this value go".

## Approach

Emit a self-contained HTML page: one file, double-clickable, no server, no network at open time.
Cytoscape.js does the rendering, because its compound nodes are a direct match for the block tree —
`parent` on a node *is* the method boundary, with no translation step.

Rejected: a CDN-loaded page (breaks offline, pins us to a third party staying up) and a separate
JSON + committed viewer page (two files and a drag step on every run).

## Components

### `JsonExporter` (Kotlin, new)

Walks the `GraphBuilderBlock` tree and emits the graph as data. This is where all the logic worth
testing lives.

```json
{"nodes": [{"id": "b7", "label": "methodA", "type": "METHOD", "parent": "b0"},
           {"id": "n8", "label": "+",       "type": "BIN_OP", "parent": "b0"}],
 "edges": [{"source": "n5", "target": "n8"}]}
```

- Ids keep the `n`/`b` serial prefixes the other two exporters use, so one node is findable by the
  same string in all three documents.
- `parent` is Cytoscape's native compound field. A block is a node *and* a container, which is
  exactly what `GraphBuilderBlock` is.
- Edges are a flat list. Unlike GraphML there are no placement rules, so a cross-boundary edge
  needs no special handling.
- Labels are JSON strings and must be escaped: `<init>`, `"test"` and the operators are all real
  labels, and a raw `"` truncates the payload.

### `HtmlExporter` (Kotlin, new)

Wraps a `JsonExporter` payload in `template.html` with every vendored asset inlined. String
concatenation around data — deliberately no logic, so nothing here needs a test of its own.

If a vendored asset is missing at export time it throws. A page that renders nothing while
reporting success is the same failure mode the whole project exists to avoid.

### Vendored assets — `app/src/main/resources/viewer/`

| File | Size | Why |
|---|---|---|
| `cytoscape.min.js` | 428 KB | renderer; compound nodes |
| `elk.bundled.js` | 1.5 MB | layout (see below) |
| `cytoscape-elk.js` | 12 KB | adapter |
| `cytoscape-expand-collapse.js` | 32 KB | folding, and the meta-edges that replace a collapsed block's edges |
| `viewer.mjs` | — | ours: init, styling, trace-flow |
| `template.html` | — | the shell |

Committed to the repo, not fetched at build time, so a clone builds offline. `package.json` exists
only to pin the versions these were copied from and to run the JS tests.

### Layout: ELK, and why not dagre

`cytoscape-dagre` is 80 KB against ELK's 1.5 MB and is the obvious pick for a DAG. It is
unusable here: dagre does not respect compound boundaries, so expanding a method scatters its
children outside their own box. ELK's `layered` algorithm handles hierarchy properly.

Consequence: ~2.0 MB of vendored JS, and an exported page a little over that. Local file, so this
costs load time and repo size, not bandwidth. Accepted.

## Viewer behaviour

- **On open:** every block collapsed except the root. `main`'s dataflow is visible with each call
  as one closed box. First paint stays small no matter how deep the inlining goes.
- **Expand/collapse:** click a block. ELK re-runs on each toggle.
- **Colour:** by node type, matching the existing `classDef` palette.
- **Trace flow:** click a node to highlight everything transitively upstream and downstream of it
  and dim the rest. Click the background to clear. This is the question the tool exists to answer.

### Meta-edges must not read as dataflow

When a block is collapsed, the extension replaces its edges with *meta-edges* terminating on the
closed box. A meta-edge means "something inside here connects to `x`" — it is a summary, and the
specific flow is not recoverable from it.

Summarising is fine. Drawing the summary identically to a real edge is not: it would assert a flow
between two nodes that never touched, which is the silently-wrong graph in a new costume. Meta-edges
get a distinct treatment (dashed, muted) so the abstraction is visible as an abstraction.

## Testing

Four layers, each covering something the others cannot.

1. **`JsonExporter` behaviour tests** (`AppTest.kt`, alongside the GraphML ones):
   - a called method's node carries its caller's id as `parent`, and nesting survives two levels
   - edge label pairs match `MermaidExporter`'s exactly — the two renderings are one graph
   - the payload parses as JSON with labels that are JSON syntax (`"test"`, `<init>`)
2. **`node --test`** on `viewer.mjs`: the trace-flow closure, given nodes, edges and a start id.
   Pure, no DOM. The one piece of viewer code that can be *wrong* rather than merely ugly.
3. **Playwright smoke test** on a real exported page: Cytoscape initialised at all, expected box
   count, root open with calls closed, and clicking a closed method revealing its children. This is
   the layer that catches a blank page — not hypothetical, it is what yEd did on first import.
4. **Existing suite-wide invariants** are unaffected; no golden files move.

No golden file for the HTML: it is ~2 MB of vendored JS around a payload already covered by (1).

`viewer.mjs` exports its pure functions and calls `init()` only when it detects a browser, so
`node --test` imports it directly. At export time it is inlined as `<script type="module">`, where
the `export` statements are legal and simply unused.

### Build boundary

`npm` is **not** wired into `./gradlew build`. The Kotlin build stays dependency-free and fast, and
a clone does not silently require a 150 MB Chromium download. The JS tests run on demand:

```shell
npm test              # node --test, no browser needed
npm run test:browser  # playwright
```

## CLI

```shell
app <dir> --html > graph.html   # the viewer
app <dir> --json > graph.json   # the payload alone
app <dir> --graphml > g.graphml # existing
app <dir>                       # existing, Mermaid
```

## Out of scope

Dropped deliberately during design, not forgotten:

- **Click-to-source.** The most valuable feature for reading unfamiliar code, and the most
  expensive: `GraphNode` carries no source position, so it means threading positions through the
  model and every construction site in `AstBlockProcessor`. Worth its own spec.
- **Search by label**, **saved expansion state**. Polish; cheap to add once the core is proven.
