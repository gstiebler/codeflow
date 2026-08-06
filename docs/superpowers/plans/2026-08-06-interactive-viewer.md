# Interactive Viewer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Export the dataflow graph as a self-contained HTML page where each method is a foldable box, opening with only `main` expanded, and clicking a node highlights everything upstream and downstream of it.

**Architecture:** `JsonExporter` (Kotlin) turns the `GraphBuilderBlock` tree into a `{nodes, edges}` payload where each node carries its enclosing block as `parent` — Cytoscape's native compound-node field. `HtmlExporter` inlines that payload plus four vendored browser libraries into one file. All logic worth testing lives in the Kotlin exporter and in one pure JS function; the rest is the extensions doing their job.

**Tech Stack:** Kotlin/Gradle (JDK 21 toolchain), Cytoscape.js 3.30, ELK 0.9 via cytoscape-elk, cytoscape-expand-collapse 4.1, `node --test` (Node 23 built-in), Playwright 1.49.

**Spec:** `docs/superpowers/specs/2026-08-06-cytoscape-viewer-design.md`

## Global Constraints

- Node ids are `n${GraphNode.serial}`, block ids are `b${GraphBuilderBlock.serial}` — identical across all exporters, so one node is findable by the same string in every document.
- Never derive a rendered id by hashing attributes. `serial` only.
- `npm` is never wired into `./gradlew build`. A clone must build with no Node installed.
- Vendored browser libraries are committed under `app/src/main/resources/viewer/`, never fetched at build time.
- Exporters write their format by hand (as `MermaidExporter` and `GraphmlExporter` do). JSON libraries are test-only.
- Do not regenerate existing golden files. `UPDATE_SNAPSHOTS=1` must not be needed by any task here; if a snapshot moves, that is a bug in the change.
- New behaviour needs a behaviour test, not just a snapshot.
- Every `GraphException` message includes `ctx.location(tree)`.

---

### Task 1: JsonExporter

**Files:**
- Create: `app/src/main/kotlin/codeflow/JsonExporter.kt`
- Modify: `app/src/main/kotlin/codeflow/App.kt` (add `--json`)
- Modify: `app/build.gradle:33-38` (add test-only Gson)
- Test: `app/src/test/kotlin/codeflow/AppTest.kt`

**Interfaces:**
- Consumes: `GraphBuilderBlock.serial`, `.graph.getNodes()`, `.calledMethods`, `.getMethodName()`; `GraphNode.serial`, `.label`, `.getType()`, `.edgesIterator()`
- Produces: `JsonExporter().processMainMethod(block: GraphBuilderBlock, writer: (String) -> Unit)` writing a JSON document line by line

**Payload shape** (root block has no `parent` key at all):

```json
{
  "nodes": [
    {"id": "b0", "label": "main", "type": "METHOD"},
    {"id": "n1", "label": "main", "type": "RETURN", "parent": "b0"},
    {"id": "b7", "label": "methodA", "type": "METHOD", "parent": "b0"}
  ],
  "edges": [
    {"source": "n5", "target": "n8"}
  ]
}
```

- [ ] **Step 1: Add the test-only JSON parser**

In `app/build.gradle`, inside the existing `dependencies { }` block, add:

```groovy
    // Test-only. The exporter writes JSON by hand like the other two exporters; this is here so
    // the tests parse it with something that did not write it.
    testImplementation 'com.google.code.gson:gson:2.11.0'
```

- [ ] **Step 2: Write the failing tests**

Add to `AppTest.kt`. Imports to add at the top of the file:

```kotlin
import com.google.gson.JsonParser
import com.google.gson.JsonObject
```

Helpers, placed next to the existing `buildGraphml`:

```kotlin
    /** The JSON rendering, parsed with a library that did not write it. */
    private fun buildJson(testDir: String, testFiles: List<String>): JsonObject {
        val testDirPath = testResourcesPath.resolve(testDir)
        val testFilePaths = testFiles.map { testDirPath.resolve(it) }
        val mainMethod = AstReader(testResourcesPath).process(testFilePaths)

        val text = StringBuilder()
        JsonExporter().processMainMethod(mainMethod) { text.append(it).append("\n") }
        return JsonParser.parseString(text.toString()).asJsonObject
    }

    private fun jsonNodes(doc: JsonObject) = doc.getAsJsonArray("nodes").map { it.asJsonObject }

    private fun jsonLabel(node: JsonObject) = node.get("label").asString

    private fun jsonParent(node: JsonObject) = node.get("parent")?.asString

    /** The JSON edges as (source label, target label) pairs, to compare against [edgeLabels]. */
    private fun jsonEdgeLabels(doc: JsonObject): List<Pair<String, String>> {
        val labels = jsonNodes(doc).associate { it.get("id").asString to jsonLabel(it) }
        return doc.getAsJsonArray("edges").map { it.asJsonObject }.map {
            (labels[it.get("source").asString] ?: "?") to (labels[it.get("target").asString] ?: "?")
        }
    }
```

Tests, placed next to the existing `graphml*` tests:

```kotlin
    /**
     * `parent` is what Cytoscape draws as a containing box, so it is the method boundary and
     * nothing else carries it. A payload with the nesting flattened renders as a correct graph
     * with every boundary silently gone.
     */
    @Test
    fun jsonNestsACallInsideItsCaller() {
        val doc = buildJson("funcCall", listOf("App.java"))
        val nodes = jsonNodes(doc)
        val byId = nodes.associateBy { it.get("id").asString }

        val root = nodes.single { jsonParent(it) == null }
        assertEquals("main", jsonLabel(root), "the parentless node should be the outermost method")

        val methodC = nodes.filter { jsonLabel(it) == "methodC" && it.get("type").asString == "METHOD" }
        assertEquals(2, methodC.size, "methodC is inlined at two call sites and should appear twice")
        assertTrue(
            methodC.all { jsonLabel(byId.getValue(jsonParent(it)!!)) == "methodB" },
            "methodC is not nested in methodB"
        )

        val paramH = nodes.filter { jsonLabel(it) == "paramH" }
        assertTrue(paramH.isNotEmpty(), "methodC's parameter is missing")
        assertTrue(
            paramH.all { jsonLabel(byId.getValue(jsonParent(it)!!)) == "methodC" },
            "a node escaped its own method"
        )
    }

    /**
     * A label carrying a quote ends the JSON string early and the rest of the payload becomes
     * syntax errors, so the page renders nothing at all.
     */
    @Test
    fun jsonEscapesLabelsThatAreJsonSyntax() {
        val labels = jsonNodes(buildJson("constructor", listOf("App.java"))).map { jsonLabel(it) }
        assertTrue("\"test\"" in labels, "the string literal's label did not survive: $labels")
        assertTrue("<init>" in labels, "the constructor's return label did not survive: $labels")
    }

    /** The three exporters render one graph, so they have to agree on its edges. */
    @Test
    fun jsonKeepsEveryEdgeTheMermaidGraphHas() {
        val mermaid = edgeLabels(buildGraph("funcCall", listOf("App.java"))).sortedBy { it.toString() }
        val json = jsonEdgeLabels(buildJson("funcCall", listOf("App.java"))).sortedBy { it.toString() }
        assertEquals(mermaid, json, "the two renderings of one graph disagree on its edges")
    }
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew test --tests '*json*'`
Expected: compile error — `JsonExporter` does not exist. Create the stub below, then re-run and expect three assertion failures (not errors).

Stub, at `app/src/main/kotlin/codeflow/JsonExporter.kt`:

```kotlin
package codeflow

import codeflow.graph.GraphBuilderBlock

class JsonExporter {
    fun processMainMethod(mainMethod: GraphBuilderBlock, writer: (String) -> Unit) {
        writer("""{"nodes": [], "edges": []}""")
    }
}
```

Re-run. Expected: 3 tests fail on assertions — "the parentless node should be the outermost method", "the string literal's label did not survive", "the two renderings of one graph disagree on its edges".

- [ ] **Step 4: Write the implementation**

Replace `app/src/main/kotlin/codeflow/JsonExporter.kt` with:

```kotlin
package codeflow

import codeflow.graph.GraphBuilderBlock
import codeflow.graph.GraphNode

/**
 * The graph as JSON, in the shape Cytoscape.js consumes directly.
 *
 * A block becomes a node like any other, distinguished only by carrying type METHOD, and every
 * node inside it names it as `parent`. That is Cytoscape's compound-node model, and it is what a
 * viewer draws as a foldable box - so `parent` is the method boundary, and a payload that drops it
 * is a correct graph with every boundary silently gone.
 */
class JsonExporter {
    // The same `n` and `b` prefixes the other exporters use, so a node found in one document can be
    // found in the others by the serial that identifies it.
    private fun nodeId(node: GraphNode) = "n${node.serial}"

    private fun blockId(block: GraphBuilderBlock) = "b${block.serial}"

    /**
     * Labels are JSON syntax often enough that this is not an edge case: a string literal carries
     * its quotes, and a quote ends the string early and turns the rest of the payload into syntax
     * errors - which renders as a blank page, not as a partial graph.
     */
    private fun escape(text: String) = buildString {
        for (char in text) {
            when {
                char == '\\' -> append("\\\\")
                char == '"' -> append("\\\"")
                char == '\n' -> append("\\n")
                char == '\r' -> append("\\r")
                char == '\t' -> append("\\t")
                char < ' ' -> append("\\u%04x".format(char.code))
                else -> append(char)
            }
        }
    }

    fun processMainMethod(mainMethod: GraphBuilderBlock, writer: (String) -> Unit) {
        val nodes = ArrayList<String>()
        val edges = ArrayList<String>()
        collect(mainMethod, null, nodes, edges)

        writer("{")
        writer("""  "nodes": [""")
        nodes.forEachIndexed { index, node ->
            writer("    $node${if (index < nodes.size - 1) "," else ""}")
        }
        writer("  ],")
        writer("""  "edges": [""")
        edges.forEachIndexed { index, edge ->
            writer("    $edge${if (index < edges.size - 1) "," else ""}")
        }
        writer("  ]")
        writer("}")
    }

    /**
     * One pass over the tree filling both lists.
     *
     * Edges are a flat list with no placement rules, unlike GraphML, so an edge crossing a method
     * boundary needs no special handling at all.
     */
    private fun collect(
        block: GraphBuilderBlock,
        parentId: String?,
        nodes: MutableList<String>,
        edges: MutableList<String>
    ) {
        nodes.add(entry(blockId(block), block.getMethodName(), "METHOD", parentId))
        val ownId = blockId(block)

        for (node in block.graph.getNodes()) {
            nodes.add(entry(nodeId(node), node.label, node.getType().toString(), ownId))
            for (toNode in node.edgesIterator()) {
                edges.add("""{"source": "${nodeId(node)}", "target": "${nodeId(toNode)}"}""")
            }
        }
        for (calledMethod in block.calledMethods) {
            collect(calledMethod, ownId, nodes, edges)
        }
    }

    /** The outermost block has no enclosing box, so it carries no `parent` key at all. */
    private fun entry(id: String, label: String, type: String, parentId: String?): String {
        val parent = if (parentId == null) "" else """, "parent": "$parentId""""
        return """{"id": "$id", "label": "${escape(label)}", "type": "$type"$parent}"""
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew test --tests '*json*'`
Expected: 3 tests PASS.

Then run the whole suite: `./gradlew build`
Expected: all tests pass, no golden file changes. If a snapshot moved, stop — nothing in this task touches the Mermaid output.

- [ ] **Step 6: Wire up the CLI**

In `App.kt`, replace the format branch with:

```kotlin
    if (args.contains("--html")) {
        HtmlExporter().processMainMethod(mainMethod) { result.add(it) }
    } else if (args.contains("--json")) {
        JsonExporter().processMainMethod(mainMethod) { result.add(it) }
    } else if (args.contains("--graphml")) {
        GraphmlExporter().processMainMethod(mainMethod) { result.add(it) }
    } else {
        MermaidExporter().processMainMethod(mainMethod) { result.add(it) }
    }
```

`HtmlExporter` does not exist yet — comment out that first branch and restore it in Task 3. Verify:

```shell
./gradlew -q run --args="app/src/test/resources/funcCall --json" | head -5
```

Expected: valid JSON beginning `{` then `"nodes": [`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/codeflow/JsonExporter.kt app/src/main/kotlin/codeflow/App.kt app/build.gradle app/src/test/kotlin/codeflow/AppTest.kt
git commit -m "Export the graph as Cytoscape JSON"
```

---

### Task 2: The trace-flow closure, tested in Node

**Files:**
- Create: `app/src/main/resources/viewer/viewer.mjs`
- Create: `app/src/test/js/unit/trace.test.mjs`
- Modify: `package.json` (fix the test script paths)

**Interfaces:**
- Produces: `traceFrom(edges, startId) -> Set<string>` — exported from `viewer.mjs`, used by Task 5. `edges` is the payload's edge array (`{source, target}` objects). Returns the start id plus everything transitively reachable forwards and backwards.

- [ ] **Step 1: Fix the npm scripts**

In `package.json`, replace the `scripts` block with:

```json
  "scripts": {
    "test": "node --test app/src/test/js/unit/",
    "test:browser": "playwright test --config=app/src/test/js/playwright.config.mjs"
  },
```

The unit tests live in their own directory so `node --test` cannot pick up the Playwright spec, which needs a browser it does not have.

- [ ] **Step 2: Write the failing test**

Create `app/src/test/js/unit/trace.test.mjs`:

```javascript
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { traceFrom } from '../../../main/resources/viewer/viewer.mjs';

const ids = (set) => [...set].sort();

test('reaches both backwards and forwards from the clicked node', () => {
  const edges = [{ source: 'a', target: 'b' }, { source: 'b', target: 'c' }];
  assert.deepEqual(ids(traceFrom(edges, 'b')), ['a', 'b', 'c']);
});

test('leaves an unrelated component alone', () => {
  const edges = [{ source: 'a', target: 'b' }, { source: 'c', target: 'd' }];
  assert.deepEqual(ids(traceFrom(edges, 'a')), ['a', 'b']);
});

test('includes a node with no edges at all', () => {
  assert.deepEqual(ids(traceFrom([], 'lonely')), ['lonely']);
});

// A for-loop's counter flows into itself through the loop body, so the graph really does contain
// cycles. A naive walk follows one forever and the page hangs with no error to explain it.
test('terminates on a cycle', () => {
  const edges = [{ source: 'a', target: 'b' }, { source: 'b', target: 'a' }];
  assert.deepEqual(ids(traceFrom(edges, 'a')), ['a', 'b']);
});

// Two calls to one method are two subgraphs that share no nodes. Tracing from inside one must not
// light up the other, which is the whole reason nodes are per-occurrence.
test('does not cross into a sibling call with the same shape', () => {
  const edges = [
    { source: 'arg1', target: 'param1' }, { source: 'param1', target: 'ret1' },
    { source: 'arg2', target: 'param2' }, { source: 'param2', target: 'ret2' },
  ];
  assert.deepEqual(ids(traceFrom(edges, 'param1')), ['arg1', 'param1', 'ret1']);
});
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `npm test`
Expected: FAIL — cannot resolve `viewer.mjs`, or `traceFrom is not a function`.

- [ ] **Step 4: Write the minimal implementation**

Create `app/src/main/resources/viewer/viewer.mjs`:

```javascript
/**
 * Everything the exported page does that is ours.
 *
 * Exports its pure functions so `node --test` can import this file directly, and calls init() only
 * in a browser - the tests run in Node, where `document` and `cytoscape` do not exist.
 */

/**
 * The clicked node plus everything a value could have come from and everything it can reach.
 *
 * `seen` is what makes this terminate: the graph has cycles wherever a loop feeds a variable back
 * into itself, and following one forever hangs the page with nothing on screen to explain why.
 */
export function traceFrom(edges, startId) {
  const outgoing = new Map();
  const incoming = new Map();
  for (const edge of edges) {
    if (!outgoing.has(edge.source)) outgoing.set(edge.source, []);
    outgoing.get(edge.source).push(edge.target);
    if (!incoming.has(edge.target)) incoming.set(edge.target, []);
    incoming.get(edge.target).push(edge.source);
  }

  const reached = new Set([startId]);
  for (const adjacency of [outgoing, incoming]) {
    const queue = [startId];
    while (queue.length > 0) {
      const id = queue.pop();
      for (const next of adjacency.get(id) ?? []) {
        if (!reached.has(next)) {
          reached.add(next);
          queue.push(next);
        }
      }
    }
  }
  return reached;
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `npm test`
Expected: 5 tests pass, output clean.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/resources/viewer/viewer.mjs app/src/test/js/unit/trace.test.mjs package.json
git commit -m "Trace a value backwards and forwards from one node"
```

---

### Task 3: Vendor the libraries and export a page that renders

**Files:**
- Create: `app/src/main/resources/viewer/cytoscape.min.js` (copied)
- Create: `app/src/main/resources/viewer/elk.bundled.js` (copied)
- Create: `app/src/main/resources/viewer/cytoscape-elk.js` (copied)
- Create: `app/src/main/resources/viewer/cytoscape-expand-collapse.js` (copied)
- Create: `app/src/main/resources/viewer/template.html`
- Create: `app/src/main/kotlin/codeflow/HtmlExporter.kt`
- Modify: `app/src/main/resources/viewer/viewer.mjs` (add `init`)
- Modify: `app/src/main/kotlin/codeflow/App.kt` (restore the `--html` branch)
- Test: `app/src/test/kotlin/codeflow/AppTest.kt`

**Interfaces:**
- Consumes: `JsonExporter` from Task 1, `traceFrom` from Task 2
- Produces: `HtmlExporter().processMainMethod(block, writer)`; `init(payload)` in `viewer.mjs`

- [ ] **Step 1: Copy the vendored libraries**

```bash
mkdir -p app/src/main/resources/viewer
cp node_modules/cytoscape/dist/cytoscape.min.js app/src/main/resources/viewer/
cp node_modules/elkjs/lib/elk.bundled.js app/src/main/resources/viewer/
cp node_modules/cytoscape-elk/dist/cytoscape-elk.js app/src/main/resources/viewer/
cp node_modules/cytoscape-expand-collapse/cytoscape-expand-collapse.js app/src/main/resources/viewer/
ls -la app/src/main/resources/viewer/
```

Expected: four files, roughly 428K / 1.5M / 12K / 32K.

- [ ] **Step 2: Write the failing test**

Add to `AppTest.kt`:

```kotlin
    /**
     * The page has to carry its own libraries. A viewer that renders nothing because an asset was
     * missing looks exactly like a graph with no nodes, so the export fails loudly instead.
     */
    @Test
    fun htmlPageCarriesItsLibrariesAndItsData() {
        val page = StringBuilder()
        val testDirPath = testResourcesPath.resolve("funcCall")
        val mainMethod = AstReader(testResourcesPath).process(listOf(testDirPath.resolve("App.java")))
        HtmlExporter().processMainMethod(mainMethod) { page.append(it).append("\n") }
        val html = page.toString()

        assertTrue("cytoscape" in html, "the renderer was not inlined")
        assertTrue("ELK" in html, "the layout engine was not inlined")
        assertTrue("expandCollapse" in html, "the folding extension was not inlined")
        assertTrue("traceFrom" in html, "our own viewer code was not inlined")
        assertTrue("\"label\": \"methodC\"" in html, "the graph payload was not inlined")
        assertTrue("<script" in html && "</html>" in html, "the page is not a complete document")
    }
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew test --tests '*htmlPage*'`
Expected: compile error, `HtmlExporter` does not exist.

- [ ] **Step 4: Write the template**

Create `app/src/main/resources/viewer/template.html`:

```html
<!doctype html>
<html>
<head>
<meta charset="utf-8">
<title>codeflow</title>
<style>
  html, body { margin: 0; height: 100%; font: 13px system-ui, sans-serif; }
  #cy { width: 100%; height: 100%; }
  #hint { position: fixed; bottom: 8px; left: 8px; color: #666;
          background: #fffc; padding: 4px 8px; border-radius: 4px; }
</style>
</head>
<body>
<div id="cy"></div>
<div id="hint">click a method to fold it &middot; click a node to trace the value &middot; click the background to clear</div>
<script>/*__CYTOSCAPE__*/</script>
<script>/*__ELK__*/</script>
<script>/*__CYTOSCAPE_ELK__*/</script>
<script>/*__EXPAND_COLLAPSE__*/</script>
<script type="module">
/*__VIEWER__*/
init(/*__PAYLOAD__*/);
</script>
</body>
</html>
```

The four library scripts are classic scripts and run in order as the page parses. The module script is deferred and runs last, so `cytoscape`, `ELK` and the extensions are all registered before `init` is called.

- [ ] **Step 5: Write HtmlExporter**

Create `app/src/main/kotlin/codeflow/HtmlExporter.kt`:

```kotlin
package codeflow

import codeflow.graph.GraphBuilderBlock
import codeflow.graph.GraphException

/**
 * The graph as one self-contained page.
 *
 * Everything is inlined - four vendored libraries and the payload - so the file opens from disk
 * with no server and no network, and can be handed to someone else as a single artifact.
 *
 * There is deliberately no logic here beyond substitution. Anything that could make the graph wrong
 * lives in JsonExporter, where the tests are.
 */
class HtmlExporter {
    private fun asset(name: String): String =
        javaClass.getResource("/viewer/$name")?.readText()
        // A page whose libraries are missing renders as an empty canvas, which is indistinguishable
        // from a graph with no nodes. Failing here is the whole difference.
            ?: throw GraphException("viewer asset '/viewer/$name' is missing from the jar")

    fun processMainMethod(mainMethod: GraphBuilderBlock, writer: (String) -> Unit) {
        val payload = StringBuilder()
        JsonExporter().processMainMethod(mainMethod) { payload.append(it).append("\n") }

        val page = asset("template.html")
            .replace("/*__CYTOSCAPE__*/", asset("cytoscape.min.js"))
            .replace("/*__ELK__*/", asset("elk.bundled.js"))
            .replace("/*__CYTOSCAPE_ELK__*/", asset("cytoscape-elk.js"))
            .replace("/*__EXPAND_COLLAPSE__*/", asset("cytoscape-expand-collapse.js"))
            .replace("/*__VIEWER__*/", asset("viewer.mjs"))
            .replace("/*__PAYLOAD__*/", payload.toString())

        page.lineSequence().forEach(writer)
    }
}
```

- [ ] **Step 6: Add `init` to viewer.mjs**

Append to `app/src/main/resources/viewer/viewer.mjs`:

```javascript
const PALETTE = {
  // The Mermaid classDef colours, as rgba. OBJ_VARIABLE and MEM_SPACE have no classDef today and
  // render unstyled there; they get explicit colours here rather than silently sharing one.
  LITERAL:      'rgba(0, 255, 0, 0.19)',
  VARIABLE:     'rgba(128, 128, 128, 0.19)',
  OBJ_VARIABLE: 'rgba(128, 200, 128, 0.25)',
  BIN_OP:       'rgba(128, 128, 128, 0.50)',
  FUNC_PARAM:   'rgba(128, 128, 255, 0.19)',
  RETURN:       'rgba(255, 128, 128, 0.50)',
  EXTERNAL:     'rgba(255, 165, 0, 0.25)',
  MEM_SPACE:    'rgba(200, 200, 128, 0.25)',
  METHOD:       'rgba(240, 240, 240, 0.60)',
};

export const LAYOUT = {
  name: 'elk',
  elk: {
    algorithm: 'layered',
    'elk.direction': 'DOWN',
    'elk.layered.spacing.nodeNodeBetweenLayers': 40,
    'elk.spacing.nodeNode': 25,
    // Without this ELK lays out each container independently and the boxes overlap.
    'elk.hierarchyHandling': 'INCLUDE_CHILDREN',
  },
};

export function init(payload) {
  const cy = cytoscape({
    container: document.getElementById('cy'),
    elements: {
      nodes: payload.nodes.map((n) => ({ data: n })),
      edges: payload.edges.map((e) => ({ data: e })),
    },
    style: [
      { selector: 'node', style: {
        label: 'data(label)', 'text-valign': 'center', 'font-size': 11,
        shape: 'round-rectangle', 'background-color': (n) => PALETTE[n.data('type')] ?? '#ddd',
        'border-width': 1, 'border-color': '#999', width: 'label', padding: 6,
      } },
      { selector: ':parent', style: {
        'text-valign': 'top', 'font-weight': 'bold', 'background-opacity': 0.35,
      } },
      { selector: 'edge', style: {
        width: 1.5, 'line-color': '#999', 'target-arrow-color': '#999',
        'target-arrow-shape': 'triangle', 'curve-style': 'bezier',
      } },
      { selector: '.dimmed', style: { opacity: 0.15 } },
      { selector: '.traced', style: { 'border-width': 3, 'border-color': '#d33' } },
    ],
    layout: LAYOUT,
  });
  return cy;
}

// Node imports this file to test the pure functions; only a browser has a document to draw into.
if (typeof document !== 'undefined') {
  window.init = init;
}
```

Note the last block: the template calls a bare `init(...)`, and a module's exports are not global, so `init` is published on `window` for the template's call to find.

- [ ] **Step 7: Restore the CLI branch and run the tests**

Uncomment the `--html` branch in `App.kt` from Task 1 Step 6.

Run: `./gradlew test --tests '*htmlPage*'`
Expected: PASS.

Run: `./gradlew build`
Expected: all tests pass, no golden files move.

- [ ] **Step 8: Look at it**

```shell
./gradlew -q run --args="app/src/test/resources/funcCall --html" > /tmp/funcCall.html
open /tmp/funcCall.html
```

Expected: a graph draws, with `main` as an outer box containing `methodA` and `methodB` boxes. Everything is expanded at this point — folding is Task 5. If the page is blank, open the browser console: a missing extension registration shows up there and nowhere else.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/resources/viewer app/src/main/kotlin/codeflow/HtmlExporter.kt app/src/main/kotlin/codeflow/App.kt app/src/test/kotlin/codeflow/AppTest.kt
git commit -m "Export a self-contained viewer page"
```

---

### Task 4: Playwright harness and smoke test

**Files:**
- Create: `app/src/test/js/playwright.config.mjs`
- Create: `app/src/test/js/global-setup.mjs`
- Create: `app/src/test/js/browser/viewer.spec.mjs`

**Interfaces:**
- Consumes: the `--html` CLI from Task 3
- Produces: `build/viewer-test/funcCall.html`, regenerated by global setup on every run

- [ ] **Step 1: Write the global setup**

Create `app/src/test/js/global-setup.mjs`:

```javascript
import { execSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

/**
 * Builds a real page from a real fixture before the browser tests run.
 *
 * Going through `gradlew run` rather than the installed CLI means the JDK 21 toolchain is Gradle's
 * problem, not ours - the installed script uses whatever `java` is on PATH and dies on an older one.
 *
 * The fixture path is absolute because the `run` task's working directory is `app/`, not the repo
 * root, so a repo-relative path silently resolves to `app/app/...` and Files.walk throws.
 */
export default function globalSetup() {
  const fixture = resolve('app/src/test/resources/funcCall');
  mkdirSync('build/viewer-test', { recursive: true });
  const html = execSync(
    `./gradlew -q run --args="${fixture} --html"`,
    { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 },
  );
  writeFileSync('build/viewer-test/funcCall.html', html);
}
```

- [ ] **Step 2: Write the config**

Create `app/src/test/js/playwright.config.mjs`:

```javascript
import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './browser',
  globalSetup: './global-setup.mjs',
  use: { headless: true },
  reporter: 'list',
});
```

- [ ] **Step 3: Write the failing test**

Create `app/src/test/js/browser/viewer.spec.mjs`:

```javascript
import { test, expect } from '@playwright/test';
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';

const PAGE = pathToFileURL(resolve('build/viewer-test/funcCall.html')).href;

/** Cytoscape is on window and holds the graph; this is how the tests see what rendered. */
const counts = (page) => page.evaluate(() => ({
  nodes: window.cy.nodes().length,
  edges: window.cy.edges().length,
  methods: window.cy.nodes('[type = "METHOD"]').length,
}));

test.beforeEach(async ({ page }) => {
  const errors = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await page.goto(PAGE);
  await page.waitForFunction(() => window.cy !== undefined);
  expect(errors, 'the page threw while loading').toEqual([]);
});

// The failure this exists for: a page that renders nothing looks exactly like an empty graph.
test('renders the graph', async ({ page }) => {
  const { nodes, edges, methods } = await counts(page);
  expect(nodes).toBe(39);
  expect(edges).toBe(26);
  expect(methods).toBe(5);
});

test('draws the canvas at a usable size', async ({ page }) => {
  const box = await page.locator('#cy canvas').first().boundingBox();
  expect(box.width).toBeGreaterThan(100);
  expect(box.height).toBeGreaterThan(100);
});
```

- [ ] **Step 4: Run it to verify it fails**

Run: `npm run test:browser`
Expected: FAIL on `window.cy !== undefined` timing out — `init` returns `cy` but never publishes it.

- [ ] **Step 5: Publish `cy` for the tests**

In `viewer.mjs`, at the end of `init`, before `return cy;`:

```javascript
  // The browser tests read the graph off this. Nothing in the page uses it.
  window.cy = cy;
```

- [ ] **Step 6: Run it to verify it passes**

Run: `npm run test:browser`
Expected: 2 tests pass.

If the node count is not 39, do not edit the number to match. Compare against `grep -c '<node ' ~/Desktop/codeflow-graphs/funcCall.graphml`, which is 39 for the same fixture; a different number means nodes are being lost or duplicated.

- [ ] **Step 7: Commit**

```bash
git add app/src/test/js app/src/main/resources/viewer/viewer.mjs
git commit -m "Smoke-test the exported page in a real browser"
```

---

### Task 5: Fold methods, opening with only the root expanded

**Files:**
- Modify: `app/src/main/resources/viewer/viewer.mjs`
- Modify: `app/src/test/js/browser/viewer.spec.mjs`

**Interfaces:**
- Consumes: `init` and `LAYOUT` from Task 3
- Produces: `window.api`, the expand-collapse handle, for the browser tests

- [ ] **Step 1: Write the failing test**

Add to `app/src/test/js/browser/viewer.spec.mjs`:

```javascript
/** How many nodes are actually on screen — a collapsed block hides its children. */
const visible = (page) => page.evaluate(() => window.cy.nodes(':visible').length);

test('opens with only the outermost method expanded', async ({ page }) => {
  const collapsed = await page.evaluate(
    () => window.cy.nodes('[type = "METHOD"]').filter((n) => window.api.isCollapsible(n) === false).length,
  );
  // main stays open; methodA, methodB and both methodC blocks start folded.
  expect(collapsed).toBe(4);
  expect(await visible(page)).toBeLessThan(39);
});

test('expanding a method reveals its own nodes', async ({ page }) => {
  const before = await visible(page);
  await page.evaluate(() => {
    const methodA = window.cy.nodes('[type = "METHOD"]').filter((n) => n.data('label') === 'methodA');
    window.api.expand(methodA);
  });
  await page.waitForFunction((n) => window.cy.nodes(':visible').length > n, before);
  expect(await visible(page)).toBeGreaterThan(before);
});
```

- [ ] **Step 2: Run it to verify it fails**

Run: `npm run test:browser`
Expected: FAIL — `window.api` is undefined.

- [ ] **Step 3: Implement folding**

In `viewer.mjs`, inside `init`, after the `cytoscape({...})` call and before `window.cy = cy`:

```javascript
  const api = cy.expandCollapse({
    layoutBy: LAYOUT,
    fisheye: false,
    animate: false,
    undoable: false,
  });

  // Everything folded except the outermost method. The graph inlines a callee's body at every call
  // site, so node count grows with call sites rather than source size - opening it all at once is
  // the wall this viewer exists to avoid. The root stays open so the first click is never wasted.
  const root = cy.nodes('[type = "METHOD"]').filter((n) => n.isOrphan());
  api.collapseAll();
  api.expand(root);

  window.api = api;
```

Add to the stylesheet array, after the `.traced` selector:

```javascript
      // A collapsed block's edges are replaced by meta-edges, which say only that *something*
      // inside connects to the other end. That is a summary, not a flow the code has; drawn like a
      // real edge it would assert a connection between two nodes that never touched.
      { selector: 'edge.cy-expand-collapse-meta-edge', style: {
        'line-style': 'dashed', 'line-color': '#bbb', 'target-arrow-color': '#bbb',
      } },
```

- [ ] **Step 4: Run it to verify it passes**

Run: `npm run test:browser`
Expected: 4 tests pass.

- [ ] **Step 5: Look at it**

```shell
./gradlew -q run --args="app/src/test/resources/funcCall --html" > /tmp/funcCall.html && open /tmp/funcCall.html
```

Expected: `main` open, `methodA` and `methodB` as closed boxes, dashed meta-edges into them. Clicking a closed box opens it.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/resources/viewer/viewer.mjs app/src/test/js/browser/viewer.spec.mjs
git commit -m "Open the viewer with only the entry point expanded"
```

---

### Task 6: Trace a value through the graph

**Files:**
- Modify: `app/src/main/resources/viewer/viewer.mjs`
- Modify: `app/src/test/js/browser/viewer.spec.mjs`

**Interfaces:**
- Consumes: `traceFrom(edges, startId)` from Task 2

- [ ] **Step 1: Write the failing test**

Add to `app/src/test/js/browser/viewer.spec.mjs`:

```javascript
test('clicking a node traces the value and dims the rest', async ({ page }) => {
  await page.evaluate(() => {
    window.cy.nodes().filter((n) => n.data('label') === 'x').emit('tap');
  });
  const { traced, dimmed } = await page.evaluate(() => ({
    traced: window.cy.nodes('.traced').map((n) => n.data('label')),
    dimmed: window.cy.nodes('.dimmed').length,
  }));
  // 5 -> x -> a -> + -> c -> methodA -> y. The literal 8 and b reach + too, so they come along.
  expect(traced).toContain('x');
  expect(traced).toContain('5');
  expect(traced).toContain('y');
  expect(dimmed).toBeGreaterThan(0);
});

test('clicking the background clears the trace', async ({ page }) => {
  await page.evaluate(() => {
    window.cy.nodes().filter((n) => n.data('label') === 'x').emit('tap');
    window.cy.emit('tap', [{ target: window.cy }]);
  });
  expect(await page.evaluate(() => window.cy.elements('.dimmed').length)).toBe(0);
});
```

- [ ] **Step 2: Run it to verify it fails**

Run: `npm run test:browser`
Expected: FAIL — no `.traced` nodes, nothing is dimmed.

- [ ] **Step 3: Implement the interaction**

In `viewer.mjs`, inside `init`, before `window.cy = cy`:

```javascript
  const clear = () => cy.elements().removeClass('dimmed traced');

  cy.on('tap', 'node', (event) => {
    const node = event.target;
    // A method box is for folding, not tracing; expand-collapse owns that click.
    if (node.isParent()) return;

    clear();
    const lit = traceFrom(payload.edges, node.id());
    cy.elements().addClass('dimmed');
    // Keep a traced node's enclosing boxes lit, or the highlight floats in a dimmed container.
    cy.nodes().filter((n) => lit.has(n.id()))
      .forEach((n) => n.ancestors().add(n).removeClass('dimmed'));
    cy.nodes().filter((n) => lit.has(n.id())).addClass('traced');
    cy.edges().filter((e) => lit.has(e.source().id()) && lit.has(e.target().id()))
      .removeClass('dimmed');
  });

  cy.on('tap', (event) => {
    if (event.target === cy) clear();
  });
```

- [ ] **Step 4: Run it to verify it passes**

Run: `npm run test:browser`
Expected: 6 tests pass.

- [ ] **Step 5: Run everything**

```shell
./gradlew build && npm test && npm run test:browser
```

Expected: Kotlin suite green with no golden files moved, 5 node tests pass, 6 browser tests pass.

- [ ] **Step 6: Update CLAUDE.md**

Add to the Architecture section, after the `MermaidExporter` sentence:

```markdown
Three exporters render the same block tree: `MermaidExporter` (default), `GraphmlExporter`
(`--graphml`, for desktop editors like yEd) and `HtmlExporter` (`--html`, a self-contained
Cytoscape.js page). `JsonExporter` (`--json`) produces the payload `HtmlExporter` embeds, and is
where the tested logic lives — the HTML exporter is substitution only.

The viewer opens with every method folded except the entry point, because inlining per call site
means node count grows with call sites rather than source size. A collapsed method's edges are
replaced by *meta-edges*, which are drawn dashed: a meta-edge says something inside connects to the
other end, and drawing it like a real edge would assert a flow that does not exist.
```

Add to the Testing section:

```markdown
The viewer has its own tests, deliberately outside `./gradlew build` so a clone never needs Node:
`npm test` runs the trace-flow closure under `node --test`, and `npm run test:browser` loads a real
exported page in Playwright. The browser test is the only thing that can catch a page that renders
nothing, which is indistinguishable from a graph with no nodes.
```

- [ ] **Step 7: Commit**

```bash
git add app/src/main/resources/viewer/viewer.mjs app/src/test/js/browser/viewer.spec.mjs CLAUDE.md
git commit -m "Trace a value through the graph on click"
```

---

## Self-Review Notes

**Spec coverage:** JSON payload with `parent` (Task 1); vendored assets and self-contained page (Task 3); ELK layout (Task 3); root-only expansion (Task 5); meta-edge styling (Task 5); trace-flow (Tasks 2 and 6); colour by type (Task 3); four test layers (Tasks 1, 2, 4, 5, 6); build boundary — no npm in Gradle (Tasks 2 and 4); CLI flags (Tasks 1 and 3).

**Deferred, per the spec's out-of-scope section:** click-to-source, search by label, saved expansion state.

**Known risk:** the exact registration behaviour of `cytoscape-elk` and `cytoscape-expand-collapse` as inlined classic scripts is unverified — both are expected to self-register against a global `cytoscape`, but if Task 3 Step 8 shows a blank page, the console will name the missing registration and the fix is an explicit `cytoscape.use(...)` call before `init`.
