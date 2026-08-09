# Viewer View Model Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move every decision about what the viewer puts on screen out of Cytoscape into a pure, Node-testable `model.mjs`, then use that seam to fix `member`, where the page opens on 5 of 23 leaves and every visible node is a dead click.

**Architecture:** A new `app/src/main/resources/viewer/model.mjs` holds the pure functions — the ones already there plus three new ones (`opening`, `tap`, `screen`). `viewer.mjs` keeps `init()` and the Cytoscape stylesheet and becomes an adapter that writes `screen()`'s output into the graph. The page cannot `import` across files (it is inlined, served from `file://`), so `HtmlExporter` concatenates `model.mjs` before `viewer.mjs` into one module scope and `viewer.mjs` uses the model's exports as free identifiers.

**Tech Stack:** ES modules, `node --test` (no framework), Playwright for the browser suite, Kotlin/Gradle for the exporter side.

## Global Constraints

- `model.mjs` has **no imports, no DOM access, no Cytoscape access**. It must run under bare `node`.
- `viewer.mjs` is never loaded standalone — only concatenated after `model.mjs`. It must not `import`.
- `HtmlExporter` stays **substitution only**. No regex stripping, no logic. Its class comment says so.
- No exporter output changes except the new `graph.json` file. `truth.md` goldens must not move.
- `graph.json` is **gitignored**, rewritten every run, like `ir.txt` and `graph.html`.
- Gradle `test` and `run` need JDK 25 (`def runtimeJdk = 25` in `app/build.gradle`); the installed CLI needs `JAVA_HOME` pointing at a 25.
- Node test files live in `app/src/test/js/unit/` and must be named `*.test.mjs` — `package.json` globs exactly `app/src/test/js/unit/*.test.mjs`.
- Every new test must be confirmed against a **wrong** implementation, not merely an absent one. Each task names its mutation.

---

### Task 1: Split model.mjs out of viewer.mjs, with no behaviour change

**Files:**
- Create: `app/src/main/resources/viewer/model.mjs`
- Modify: `app/src/main/resources/viewer/viewer.mjs` (delete lines 8-158, the pure half; keep `PALETTE`, `EDGE_COLOURS`, `LAYOUT`, `init`)
- Modify: `app/src/main/resources/viewer/template.html:19-21`
- Modify: `app/src/main/kotlin/codeflow/HtmlExporter.kt:28-33`
- Modify: `app/src/test/js/unit/neighbourhood.test.mjs:3`, `app/src/test/js/unit/hidden-degree.test.mjs:3`, `app/src/test/js/unit/call-structure.test.mjs:3`

**Interfaces:**
- Consumes: nothing.
- Produces: `model.mjs` exporting `REVEAL_DEPTH`, `neighbourhood(edges, startId, depth)`, `hiddenDegree(edges, revealed)`, `badgeLabel(name, hidden)`, `ownLeaves(nodes, boxId)`, `withStubs(nodes, revealed)`, `stubOf(nodes)`, `descendantLeaves(nodes, boxId)`.

- [ ] **Step 1: Create `model.mjs` by moving lines 8-158 of `viewer.mjs` verbatim**

Move `REVEAL_DEPTH`, `neighbourhood`, `hiddenDegree`, `badgeLabel`, `isBoxNode`, `ownLeaves`, `withStubs` **with their doc comments unchanged**. Add this file header:

```js
/**
 * What the viewer puts on screen, decided without a browser.
 *
 * No imports, no DOM, no Cytoscape: `node --test` imports this file directly, and the exported page
 * gets it by concatenation - HtmlExporter substitutes it into the same module script as viewer.mjs,
 * because an inlined `<script type="module">` served from file:// has nothing to resolve an import
 * against. `export` inside an inline module is legal and simply unused.
 *
 * Everything here is a pure function of (payload, revealed). That is the point: the three decisions
 * that used to live inside Cytoscape traversals - the opening view, what a click does, and which
 * nodes get a display - are the ones a reader most needs to be right, and were the only ones no
 * test could reach.
 */
```

- [ ] **Step 2: Add `stubOf` and `descendantLeaves` to `model.mjs`, and make `withStubs` use `stubOf`**

Append after `ownLeaves`:

```js
/**
 * The one leaf that stands for each box: its RETURN node, which carries the method's name.
 *
 * Extracted so that "what is a stub" has a single definition. [withStubs] decides whether to offer
 * one and [tap] decides what clicking one does, and the two disagreeing would put a node on screen
 * that does nothing when pressed - which is the bug this whole change exists to fix.
 */
export function stubOf(nodes) {
  const stubs = new Map();
  for (const node of nodes) {
    if (node.type === 'RETURN' && node.parent && !stubs.has(node.parent)) {
      stubs.set(node.parent, node.id);
    }
  }
  return stubs;
}

/**
 * Every non-box node under `boxId`, however deep - what folding a box has to take.
 *
 * descendants, not children: a box holds boxes, and folding one that leaves a nested method's nodes
 * on screen would draw a callee floating with no caller around it.
 */
export function descendantLeaves(nodes, boxId) {
  const childrenOf = new Map();
  for (const node of nodes) {
    if (!node.parent) continue;
    if (!childrenOf.has(node.parent)) childrenOf.set(node.parent, []);
    childrenOf.get(node.parent).push(node);
  }

  const leaves = new Set();
  const boxes = [boxId];
  while (boxes.length > 0) {
    for (const child of childrenOf.get(boxes.pop()) ?? []) {
      if (isBoxNode(child)) boxes.push(child.id);
      else leaves.add(child.id);
    }
  }
  return leaves;
}
```

Then in `withStubs`, replace the inline stub map with the helper. The two lines

```js
  const stubOf = new Map();
  for (const node of nodes) {
    if (node.type === 'RETURN' && node.parent && !stubOf.has(node.parent)) stubOf.set(node.parent, node.id);
  }
```

become:

```js
  const stubs = stubOf(nodes);
```

and every later `stubOf.get(...)` / `stubOf.has(...)` inside `withStubs` becomes `stubs.get(...)` / `stubs.has(...)`. There are three such uses: the `stubOf.get(box) === id` test, the `stubOf.has(box.id)` test, and the `stubOf.get(box.id)` read.

- [ ] **Step 3: Strip the moved code out of `viewer.mjs` and rewrite its header**

`viewer.mjs` keeps only `PALETTE`, `EDGE_COLOURS`, `LAYOUT`, `init`, and the `window.init` assignment. Replace its file header with:

```js
/**
 * The renderer: everything that needs a browser.
 *
 * Never loaded on its own. HtmlExporter substitutes model.mjs into the same module script directly
 * above this one, so `withStubs`, `hiddenDegree` and the rest are free identifiers here rather than
 * imports - an inlined module served from file:// has nothing to resolve an import against, and
 * HtmlExporter is substitution only, so it cannot strip one either.
 *
 * Nothing here decides what is on screen. That is model.mjs, where the tests are.
 */
```

- [ ] **Step 4: Add the `/*__MODEL__*/` slot to `template.html`**

Replace lines 19-21:

```html
<script type="module">
/*__MODEL__*/
/*__VIEWER__*/
init(/*__PAYLOAD__*/);
</script>
```

- [ ] **Step 5: Substitute it in `HtmlExporter.kt`**

Add one line to the chain at `HtmlExporter.kt:28-33`, before the `__VIEWER__` replace:

```kotlin
            .replace("/*__MODEL__*/", asset("model.mjs"))
```

- [ ] **Step 6: Point the three existing unit tests at `model.mjs`**

In each of `neighbourhood.test.mjs`, `hidden-degree.test.mjs`, `call-structure.test.mjs`, change line 3's path from `viewer.mjs` to `model.mjs`. Only the path changes; the imported names are unchanged.

- [ ] **Step 7: Run the unit tests**

Run: `npm test`
Expected: PASS — same tests, same assertions, new import path.

- [ ] **Step 8: Run the browser tests, which is what proves the concatenation works**

Run: `npm run test:browser`
Expected: PASS. This is the real check on Steps 4-5: if the substitution is wrong the page throws `withStubs is not defined` at load and every browser test fails.

- [ ] **Step 9: Confirm the wiring is load-bearing (mutation)**

Temporarily delete the `.replace("/*__MODEL__*/", asset("model.mjs"))` line, run `npm run test:browser`, and confirm the browser tests **fail**. Restore the line. This proves Step 8 was testing the concatenation and not passing for another reason.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/resources/viewer/model.mjs \
        app/src/main/resources/viewer/viewer.mjs \
        app/src/main/resources/viewer/template.html \
        app/src/main/kotlin/codeflow/HtmlExporter.kt \
        app/src/test/js/unit/
git commit -m "Move what is on screen out of the renderer

The pure half of viewer.mjs becomes model.mjs, which node --test imports and the page gets by
concatenation - an inlined module has nothing to resolve an import against. No behaviour change:
same functions, same assertions, one new substitution slot."
```

---

### Task 2: `opening(payload)` as a pure function

**Files:**
- Modify: `app/src/main/resources/viewer/model.mjs`
- Modify: `app/src/main/resources/viewer/viewer.mjs` (the `entryBox` / `opening` block, and the `R` key handler)
- Create: `app/src/test/js/unit/opening.test.mjs`

**Interfaces:**
- Consumes: `ownLeaves(nodes, boxId)` from Task 1.
- Produces: `opening(payload) -> Set<id>`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/js/unit/opening.test.mjs`:

```js
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { opening } from '../../../main/resources/viewer/model.mjs';

// main { main, x, f { f, a } } - the entry method's own values, and nothing from what it calls.
const payload = {
  nodes: [
    { id: 'm', type: 'METHOD', label: 'main' },
    { id: 'mR', type: 'RETURN', label: 'main', parent: 'm' },
    { id: 'x', type: 'VARIABLE', label: 'x', parent: 'm' },
    { id: 'f', type: 'METHOD', label: 'f', parent: 'm' },
    { id: 'fR', type: 'RETURN', label: 'f', parent: 'f' },
    { id: 'a', type: 'VARIABLE', label: 'a', parent: 'f' },
  ],
  edges: [],
};

test('the opening view is the entry method own leaves', () => {
  assert.deepEqual([...opening(payload)].sort(), ['mR', 'x']);
});

// The callee's body is inlined at every call site, so opening everything is the wall the viewer
// exists to avoid. `a` being absent is the assertion.
test('the opening view holds nothing from a called method', () => {
  assert.equal(opening(payload).has('a'), false);
});

// The root is the box with no parent. Picking the first METHOD in the list instead would open a
// callee's body whenever the exporter happened to emit one first.
test('the entry method is the box with no parent, not the first box listed', () => {
  const reordered = { nodes: [payload.nodes[3], ...payload.nodes.filter((n) => n.id !== 'f')], edges: [] };
  assert.deepEqual([...opening(reordered)].sort(), ['mR', 'x']);
});
```

- [ ] **Step 2: Run it to verify it fails**

Run: `node --test app/src/test/js/unit/opening.test.mjs`
Expected: FAIL — `The requested module ... does not provide an export named 'opening'`.

- [ ] **Step 3: Implement `opening` in `model.mjs`**

Append after `descendantLeaves`:

```js
/**
 * What the page opens on: the entry method's own leaves, and nothing from anything it calls.
 *
 * The entry method is the box with no parent - there is exactly one, since the payload is the root
 * block and everything it reached. Taking the first METHOD in the list instead would open a
 * callee's body whenever the exporter happened to emit one first.
 */
export function opening(payload) {
  const root = payload.nodes.find((node) => isBoxNode(node) && !node.parent);
  return root ? ownLeaves(payload.nodes, root.id) : new Set();
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `node --test app/src/test/js/unit/opening.test.mjs`
Expected: PASS (3 tests).

- [ ] **Step 5: Use it from `viewer.mjs`**

Delete the `entryBox` constant and the `opening` arrow function from `init()`, and replace the initial assignment and the `R` handler's reassignment with calls to the model's `opening(payload)`:

```js
  let revealed = opening(payload);
```

and inside the `keydown` handler:

```js
      revealed = opening(payload);
```

`isBox` stays in `viewer.mjs` — `apply()` and the tap handler still use it until Tasks 3 and 4.

- [ ] **Step 6: Run both suites**

Run: `npm test && npm run test:browser`
Expected: PASS. The browser suite is what proves `opening(payload)` picks the same set the Cytoscape version did.

- [ ] **Step 7: Confirm the test catches a wrong implementation (mutation)**

Temporarily change `opening` to `ownLeaves(payload.nodes, payload.nodes.find(isBoxNode).id)` — first box rather than parentless box. Run `node --test app/src/test/js/unit/opening.test.mjs` and confirm the third test **fails**. Restore.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/resources/viewer/model.mjs \
        app/src/main/resources/viewer/viewer.mjs \
        app/src/test/js/unit/opening.test.mjs
git commit -m "Decide the opening view without asking Cytoscape

The entry method is the box with no parent, which is a fact about the payload rather than about
the rendered graph. Same set as isOrphan() picked; now a test can read it."
```

---

### Task 3: `tap(payload, revealed, id)` as a pure function, behaviour preserved

**Files:**
- Modify: `app/src/main/resources/viewer/model.mjs`
- Modify: `app/src/main/resources/viewer/viewer.mjs` (the `cy.on('tap')` handler)
- Create: `app/src/test/js/unit/tap.test.mjs`

**Interfaces:**
- Consumes: `ownLeaves`, `descendantLeaves`, `neighbourhood`, `REVEAL_DEPTH` from Task 1.
- Produces: `tap(payload, revealed, id) -> Set<id>` — a new Set; never mutates `revealed`.

**This task preserves today's behaviour exactly, including the stub click doing nothing.** Task 6 is what changes it, driven by a failing test. Do not fix it here.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/js/unit/tap.test.mjs`:

```js
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { tap } from '../../../main/resources/viewer/model.mjs';

// main { main, x, y, f { f, a, g { g, b } } }, with x -> y so one leaf click has somewhere to go.
const payload = {
  nodes: [
    { id: 'm', type: 'METHOD', label: 'main' },
    { id: 'mR', type: 'RETURN', label: 'main', parent: 'm' },
    { id: 'x', type: 'VARIABLE', label: 'x', parent: 'm' },
    { id: 'y', type: 'VARIABLE', label: 'y', parent: 'm' },
    { id: 'f', type: 'METHOD', label: 'f', parent: 'm' },
    { id: 'fR', type: 'RETURN', label: 'f', parent: 'f' },
    { id: 'a', type: 'VARIABLE', label: 'a', parent: 'f' },
    { id: 'g', type: 'METHOD', label: 'g', parent: 'f' },
    { id: 'gR', type: 'RETURN', label: 'g', parent: 'g' },
    { id: 'b', type: 'VARIABLE', label: 'b', parent: 'g' },
  ],
  edges: [{ source: 'x', target: 'y', kind: 'FLOW' }],
};

const after = (revealed, id) => [...tap(payload, new Set(revealed), id)].sort();

test('clicking a closed box reveals its own leaves and not a nested box leaves', () => {
  assert.deepEqual(after(['mR', 'x'], 'f'), ['a', 'fR', 'mR', 'x']);
});

// descendants, not children: folding a box that left a nested method's nodes on screen would draw
// a callee floating with no caller around it.
test('clicking an open box folds every leaf under it, however deep', () => {
  assert.deepEqual(after(['mR', 'x', 'a', 'fR', 'b', 'gR'], 'f'), ['mR', 'x']);
});

test('a box open only through a grandchild still folds', () => {
  assert.deepEqual(after(['mR', 'x', 'b'], 'f'), ['mR', 'x']);
});

test('clicking a leaf unions its neighbourhood', () => {
  assert.deepEqual(after(['mR', 'x'], 'x'), ['mR', 'x', 'y']);
});

// Clicks union and never subtract - only a box click or R takes anything away.
test('clicking a leaf never removes anything', () => {
  assert.deepEqual(after(['mR', 'x', 'y', 'a', 'fR'], 'y'), ['a', 'fR', 'mR', 'x', 'y']);
});

test('tap does not mutate the set it was given', () => {
  const revealed = new Set(['mR', 'x']);
  tap(payload, revealed, 'f');
  assert.deepEqual([...revealed].sort(), ['mR', 'x']);
});

test('tapping an id that is not in the payload changes nothing', () => {
  assert.deepEqual(after(['mR', 'x'], 'nope'), ['mR', 'x']);
});
```

- [ ] **Step 2: Run it to verify it fails**

Run: `node --test app/src/test/js/unit/tap.test.mjs`
Expected: FAIL — no export named `tap`.

- [ ] **Step 3: Implement `tap` in `model.mjs`**

Append after `opening`:

```js
/**
 * What one click does, as a set of revealed ids.
 *
 * One function, so that "what does clicking this do" has one answer to test. Returns a new Set and
 * never mutates its argument, so a caller can compare before against after.
 *
 * Clicks union and never subtract. Folding a box is the only thing that takes anything away, which
 * is why the box branch is the only one that deletes.
 */
export function tap(payload, revealed, id) {
  const node = payload.nodes.find((candidate) => candidate.id === id);
  if (!node) return new Set(revealed);

  const next = new Set(revealed);
  if (isBoxNode(node)) {
    const inside = descendantLeaves(payload.nodes, id);
    const open = [...inside].some((leaf) => next.has(leaf));
    if (open) for (const leaf of inside) next.delete(leaf);
    else for (const leaf of ownLeaves(payload.nodes, id)) next.add(leaf);
    return next;
  }

  for (const reached of neighbourhood(payload.edges, id, REVEAL_DEPTH)) next.add(reached);
  return next;
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `node --test app/src/test/js/unit/tap.test.mjs`
Expected: PASS (7 tests).

- [ ] **Step 5: Use it from `viewer.mjs`**

Replace the whole `cy.on('tap', 'node', …)` handler body — every line from `const node = event.target;` to the closing brace before `apply();` — with:

```js
  cy.on('tap', 'node', (event) => {
    revealed = tap(payload, revealed, event.target.id());
    apply();
  });
```

- [ ] **Step 6: Run both suites**

Run: `npm test && npm run test:browser`
Expected: PASS. The browser suite proves the pure `descendantLeaves` walk agrees with Cytoscape's `descendants()`.

- [ ] **Step 7: Confirm the fold test catches a wrong implementation (mutation)**

Temporarily change the fold branch to iterate `ownLeaves(payload.nodes, id)` instead of `inside`. Run `node --test app/src/test/js/unit/tap.test.mjs` and confirm *clicking an open box folds every leaf under it* **fails**. Restore.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/resources/viewer/model.mjs \
        app/src/main/resources/viewer/viewer.mjs \
        app/src/test/js/unit/tap.test.mjs
git commit -m "Decide what a click does without asking Cytoscape

The tap handler becomes one pure function over (payload, revealed, id), so what a click does has
one answer and a test can read it. No behaviour change - the stub click is still inert, which is
the next commit."
```

---

### Task 4: `screen(payload, revealed)` and a renderer that only writes it

**Files:**
- Modify: `app/src/main/resources/viewer/model.mjs`
- Modify: `app/src/main/resources/viewer/viewer.mjs` (the `apply` function and `isBox`)
- Create: `app/src/test/js/unit/screen.test.mjs`

**Interfaces:**
- Consumes: `withStubs`, `hiddenDegree`, `badgeLabel`, `stubOf` from Task 1.
- Produces: `screen(payload, revealed) -> { showing: Set<id>, stubs: Set<id>, hidden: Map<id, {in, out}>, nodes: [{ id, label, type, parent, badge, display }], edges: [{ source, target, kind, visible }] }` where `display` is `'element' | 'none'` for leaves and **`null` for every `METHOD` node**.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/js/unit/screen.test.mjs`:

```js
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { screen } from '../../../main/resources/viewer/model.mjs';

// main { main, x, y, f { f, a } }, x -> y inside main and y -> a crossing into f.
const payload = {
  nodes: [
    { id: 'm', type: 'METHOD', label: 'main' },
    { id: 'mR', type: 'RETURN', label: 'main', parent: 'm' },
    { id: 'x', type: 'VARIABLE', label: 'x', parent: 'm' },
    { id: 'y', type: 'VARIABLE', label: 'y', parent: 'm' },
    { id: 'f', type: 'METHOD', label: 'f', parent: 'm' },
    { id: 'fR', type: 'RETURN', label: 'f', parent: 'f' },
    { id: 'a', type: 'VARIABLE', label: 'a', parent: 'f' },
  ],
  edges: [
    { source: 'x', target: 'y', kind: 'FLOW' },
    { source: 'y', target: 'a', kind: 'FLOW' },
  ],
};

const nodeNamed = (view, id) => view.nodes.find((n) => n.id === id);

// The rule Cytoscape derives a box's visibility from its descendants, transitively. A display of
// our own on a box hides one whose only visible node is a grandchild, and that grandchild then has
// nowhere to live. As data it is one assertion instead of an `if` in the middle of a loop.
test('no METHOD node carries a display', () => {
  const view = screen(payload, new Set(['mR', 'x', 'y']));
  const boxes = view.nodes.filter((n) => n.type === 'METHOD');
  assert.equal(boxes.length, 2);
  assert.deepEqual(boxes.map((n) => n.display), [null, null]);
});

test('a revealed leaf is element and an unrevealed one is none', () => {
  const view = screen(payload, new Set(['mR', 'x', 'y']));
  assert.equal(nodeNamed(view, 'x').display, 'element');
  assert.equal(nodeNamed(view, 'a').display, 'none');
});

// Nothing is ever removed from the graph, so a node that just left the screen still needs 'none'
// written onto it. A filtered list would leave it lit from the pass before.
test('every payload node is described, visible or not', () => {
  const view = screen(payload, new Set(['mR']));
  assert.equal(view.nodes.length, payload.nodes.length);
  assert.equal(view.edges.length, payload.edges.length);
});

test('an edge is visible only when both of its endpoints are showing', () => {
  const view = screen(payload, new Set(['mR', 'x', 'y']));
  assert.equal(view.edges.find((e) => e.source === 'x').visible, true);
  assert.equal(view.edges.find((e) => e.source === 'y').visible, false);
});

test('the badge carries the hidden counts and the label stays the plain name', () => {
  const view = screen(payload, new Set(['mR', 'x', 'y']));
  assert.equal(nodeNamed(view, 'y').badge, 'y ↓1');
  assert.equal(nodeNamed(view, 'y').label, 'y');
  assert.equal(nodeNamed(view, 'x').badge, 'x');
});

// A closed box whose caller is open is offered as its RETURN node, and `stubs` is what distinguishes
// "f is on screen because you opened it" from "f is on screen as its own name".
test('an offered callee is showing and is marked a stub', () => {
  const view = screen(payload, new Set(['mR', 'x', 'y']));
  assert.equal(view.showing.has('fR'), true);
  assert.equal(view.stubs.has('fR'), true);
});

test('an opened box own RETURN is showing and is not a stub', () => {
  const view = screen(payload, new Set(['mR', 'x', 'y', 'a', 'fR']));
  assert.equal(view.showing.has('fR'), true);
  assert.equal(view.stubs.has('fR'), false);
});
```

- [ ] **Step 2: Run it to verify it fails**

Run: `node --test app/src/test/js/unit/screen.test.mjs`
Expected: FAIL — no export named `screen`.

- [ ] **Step 3: Implement `screen` in `model.mjs`**

Append after `tap`:

```js
/**
 * The whole view, as data.
 *
 * Every node and every edge in the payload is described, visible or not: nothing is ever removed
 * from the graph, so a node that just left the screen needs 'none' written onto it as much as an
 * arrival needs 'element'. Both arrays keep payload order, so a test can compare them directly.
 *
 * `display` is null for a METHOD node, never a string. Cytoscape derives a box's visibility from
 * its descendants, transitively, and a display of our own would hide a box whose only visible node
 * is a grandchild - leaving that grandchild nowhere to live. Saying so in the data makes it an
 * assertion a unit test can read, rather than a rule living in the middle of the render loop.
 */
export function screen(payload, revealed) {
  const showing = withStubs(payload.nodes, revealed);
  const hidden = hiddenDegree(payload.edges, showing);

  const stubs = new Set();
  for (const id of stubOf(payload.nodes).values()) {
    if (showing.has(id) && !revealed.has(id)) stubs.add(id);
  }

  const nodes = payload.nodes.map((node) => ({
    id: node.id,
    label: node.label,
    type: node.type,
    parent: node.parent,
    badge: badgeLabel(node.label, hidden.get(node.id)),
    display: isBoxNode(node) ? null : (showing.has(node.id) ? 'element' : 'none'),
  }));

  const edges = payload.edges.map((edge) => ({
    source: edge.source,
    target: edge.target,
    kind: edge.kind,
    visible: showing.has(edge.source) && showing.has(edge.target),
  }));

  return { showing, stubs, hidden, nodes, edges };
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `node --test app/src/test/js/unit/screen.test.mjs`
Expected: PASS (7 tests).

- [ ] **Step 5: Make `apply()` write it and decide nothing**

Replace the whole `apply` function in `viewer.mjs` with:

```js
  const apply = () => {
    // Everything about what is on screen was decided in model.mjs. This writes it, and the rule it
    // must not break is expressed as data: a display of null is a box, which Cytoscape works out
    // from its descendants and we must never touch.
    const view = screen(payload, revealed);
    const state = new Map(view.nodes.map((node) => [node.id, node]));
    for (const node of cy.nodes()) {
      const drawn = state.get(node.id());
      node.data('badge', drawn.badge);
      if (drawn.display !== null) node.style('display', drawn.display);
    }
    cy.layout(LAYOUT).run();
  };
```

Delete the now-unused `const isBox = (node) => node.data('type') === 'METHOD';` line from `init()`.

- [ ] **Step 6: Run both suites**

Run: `npm test && npm run test:browser`
Expected: PASS. The browser test *draws a box whose only revealed nodes are grandchildren* is the one that proves the `null` case is handled right end to end.

- [ ] **Step 7: Confirm the display test catches a wrong implementation (mutation)**

Temporarily change `screen`'s `display` to `showing.has(node.id) ? 'element' : 'none'` for every node, boxes included. Run `npm test` and confirm *no METHOD node carries a display* **fails**; then run `npm run test:browser` and confirm the grandchildren test **fails** too. Restore.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/resources/viewer/model.mjs \
        app/src/main/resources/viewer/viewer.mjs \
        app/src/test/js/unit/screen.test.mjs
git commit -m "Return the whole view as data, and let the renderer only write it

display is null for a METHOD node rather than an `if` inside the render loop, so 'never set display
on a box' - the rule that keeps a grandchild from having nowhere to live - is something a unit test
can assert instead of something a browser test has to click its way to."
```

---

### Task 5: Write `graph.json` beside each fixture

**Files:**
- Modify: `app/src/test/kotlin/codeflow/AppTest.kt:193-197` (`writePage`)
- Modify: `.gitignore` (after the `graph.html` entry at line 20)

**Interfaces:**
- Consumes: `JsonExporter().processMainMethod(mainMethod) { }`, already used at `AppTest.kt:75`.
- Produces: `app/src/test/resources/<fixture>/graph.json` for every fixture the golden suite runs, which Task 6 reads.

- [ ] **Step 1: Extend `writePage`**

Replace the body of `writePage` at `AppTest.kt:193-197`:

```kotlin
    private fun writePage(testDirPath: Path, mainMethod: GraphBuilderBlock) {
        val page = StringBuilder()
        HtmlExporter().processMainMethod(mainMethod) { page.append(it).append("\n") }
        Files.writeString(testDirPath.resolve("graph.html"), page)

        // The same payload the page inlines, on its own, because the viewer's Node tests need it:
        // model.mjs decides what is on screen and a sweep over the real corpus is the only thing
        // that can say a fixture is reachable at all. Gitignored and rewritten like the page.
        val payload = StringBuilder()
        JsonExporter().processMainMethod(mainMethod) { payload.append(it).append("\n") }
        Files.writeString(testDirPath.resolve("graph.json"), payload)
    }
```

Extend that method's KDoc with one sentence naming `graph.json` alongside `graph.html`.

- [ ] **Step 2: Ignore it**

Add after line 20 of `.gitignore`:

```
# The same payload as a file the viewer's Node tests can read, written beside graph.html on every
# run. `npm test` sweeps these for reachability - see app/src/test/js/unit/reachability.test.mjs.
app/src/test/resources/*/graph.json
```

- [ ] **Step 3: Run the Kotlin suite**

Run: `./gradlew test --rerun-tasks`
Expected: PASS, unchanged. `truth.md` files must not move — `git status` should show no modified `truth.md`.

- [ ] **Step 4: Verify the payloads exist and are parseable**

Run:
```bash
ls app/src/test/resources/*/graph.json | wc -l
node -e "const {readFileSync}=require('fs');const f=require('child_process').execSync('ls app/src/test/resources/*/graph.json').toString().trim().split('\n');f.forEach(p=>JSON.parse(readFileSync(p,'utf8')));console.log(f.length,'payloads parse')"
```
Expected: at least 60 files, all parsing. Record the exact count — Task 6's guard uses it.

- [ ] **Step 5: Confirm nothing leaked into git**

Run: `git status --short app/src/test/resources | grep graph.json`
Expected: no output — the ignore rule works.

- [ ] **Step 6: Commit**

```bash
git add app/src/test/kotlin/codeflow/AppTest.kt .gitignore
git commit -m "Write each fixture's payload where the viewer's tests can read it

graph.json beside graph.html, gitignored and rewritten every run like ir.txt. The viewer decides
what is on screen from this, so a sweep over the real corpus needs it on disk."
```

---

### Task 6: The corpus sweep — P2, P3, P4

**Files:**
- Create: `app/src/test/js/unit/corpus.mjs` (the loader, shared with Task 7)
- Create: `app/src/test/js/unit/invariants.test.mjs`

**Interfaces:**
- Consumes: `screen`, `withStubs`, `stubOf`, `descendantLeaves`, `hiddenDegree`, `opening` from Tasks 1-4; `graph.json` from Task 5.
- Produces: `loadCorpus() -> [{ name, payload }]` from `corpus.mjs`, used by Task 7.

`corpus.mjs` is deliberately **not** named `*.test.mjs`: `package.json` globs `app/src/test/js/unit/*.test.mjs`, so a helper named that way would be run as an empty test file.

- [ ] **Step 1: Write the corpus loader**

Create `app/src/test/js/unit/corpus.mjs`:

```js
import { readdirSync, readFileSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';

const RESOURCES = fileURLToPath(new URL('../../resources', import.meta.url));

/** How many fixtures the golden suite writes. Below this, something did not run - see [loadCorpus]. */
export const EXPECTED_AT_LEAST = 60;

/**
 * Every fixture's payload, read from the graph.json the Kotlin suite writes.
 *
 * These are gitignored and rewritten on every `./gradlew test`, so a checkout that has not run one
 * has none - and a sweep over zero fixtures passes every property it is given while proving nothing.
 * That is the same trap as a negative browser assertion, so finding too few is a failure with an
 * instruction, not an empty loop.
 */
export function loadCorpus() {
  const corpus = [];
  for (const name of readdirSync(RESOURCES, { withFileTypes: true })) {
    if (!name.isDirectory()) continue;
    const path = join(RESOURCES, name.name, 'graph.json');
    if (existsSync(path)) corpus.push({ name: name.name, payload: JSON.parse(readFileSync(path, 'utf8')) });
  }
  if (corpus.length < EXPECTED_AT_LEAST) {
    throw new Error(
      `found ${corpus.length} fixture payloads, expected at least ${EXPECTED_AT_LEAST}. ` +
      'Run `./gradlew test` first - graph.json is gitignored and written by the golden suite.',
    );
  }
  return corpus;
}
```

- [ ] **Step 2: Write the invariants sweep**

Create `app/src/test/js/unit/invariants.test.mjs`:

```js
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadCorpus } from './corpus.mjs';
import { opening, tap, screen, descendantLeaves, withStubs } from '../../../main/resources/viewer/model.mjs';

const corpus = loadCorpus();

/**
 * Two states per fixture: what the reader sees first, and what they see after clicking everything
 * on screen once. One state would let a property hold at open and break on the first click.
 */
function states(payload) {
  const open = opening(payload);
  let clicked = new Set(open);
  for (const id of withStubs(payload.nodes, open)) clicked = tap(payload, clicked, id);
  return [open, clicked];
}

// P2. Cytoscape will not draw a compound node with no visible children, whatever display it is
// given, so a box offered to the reader with nothing showing inside it is a box that is not there.
test('every box whose parent is open has something showing inside it', () => {
  for (const { name, payload } of corpus) {
    for (const revealed of states(payload)) {
      const { showing } = screen(payload, revealed);
      const openBoxes = new Set(
        payload.nodes.filter((n) => n.type === 'METHOD')
          .filter((box) => [...descendantLeaves(payload.nodes, box.id)].some((leaf) => showing.has(leaf)))
          .map((box) => box.id),
      );
      for (const box of payload.nodes.filter((n) => n.type === 'METHOD' && openBoxes.has(n.parent))) {
        const inside = [...descendantLeaves(payload.nodes, box.id)].filter((leaf) => showing.has(leaf));
        assert.ok(inside.length > 0, `${name}: box ${box.label} is inside an open box with nothing showing in it`);
      }
    }
  }
});

// P3. The rule that keeps a box whose only visible node is a grandchild from being hidden, and that
// grandchild from having nowhere to live.
test('no METHOD node is ever given a display', () => {
  for (const { name, payload } of corpus) {
    for (const revealed of states(payload)) {
      for (const node of screen(payload, revealed).nodes) {
        if (node.type === 'METHOD') {
          assert.equal(node.display, null, `${name}: box ${node.label} was given display ${node.display}`);
        }
      }
    }
  }
});

// P4. The per-node counts and the edge list, from opposite directions: badgeLabel reads the former
// and the reader believes it about the latter.
test('the hidden counts add up to the edges that cross the screen edge', () => {
  for (const { name, payload } of corpus) {
    for (const revealed of states(payload)) {
      const { showing, hidden } = screen(payload, revealed);
      let counted = 0;
      for (const { in: incoming, out } of hidden.values()) counted += incoming + out;
      const crossing = payload.edges.filter(
        (e) => showing.has(e.source) !== showing.has(e.target),
      ).length;
      assert.equal(counted, crossing, `${name}: badges claim ${counted} hidden edges, the payload has ${crossing}`);
    }
  }
});
```

- [ ] **Step 3: Run the sweep**

Run: `node --test app/src/test/js/unit/invariants.test.mjs`
Expected: PASS (3 tests). If P2 fails naming a specific fixture, **stop and report it** — that is a real finding about `withStubs`, not something to loosen the assertion for.

- [ ] **Step 4: Confirm the guard fires (mutation)**

Run: `mv app/src/test/resources/member/graph.json /tmp/ && node --test app/src/test/js/unit/invariants.test.mjs`
Expected: still PASS — one fixture short of 63 is above the floor. Now temporarily raise `EXPECTED_AT_LEAST` above the real count and confirm the run **fails** with the "run ./gradlew test first" message. Restore both.

- [ ] **Step 5: Confirm P4 catches a wrong implementation (mutation)**

Temporarily change `hiddenDegree`'s early-continue in `model.mjs` from `if (fromShowing === revealed.has(edge.target)) continue;` to `if (fromShowing && revealed.has(edge.target)) continue;`, which makes it count both-endpoints-hidden edges too. Run `node --test app/src/test/js/unit/invariants.test.mjs` and confirm P4 **fails**. Restore.

- [ ] **Step 6: Run everything**

Run: `npm test && npm run test:browser`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/test/js/unit/corpus.mjs app/src/test/js/unit/invariants.test.mjs
git commit -m "Sweep the viewer's properties over every fixture, not over a toy payload

Three properties that need no expected output and no fixture author to have anticipated the
failure - the argument InvariantsTest makes on the Kotlin side, for the half of the viewer that
decides what a reader sees. Finding too few payloads fails with an instruction rather than passing
an empty loop."
```

---

### Task 7: Reachability, and the stub click that makes it true

**Files:**
- Create: `app/src/test/js/unit/reachability.test.mjs`
- Modify: `app/src/main/resources/viewer/model.mjs` (`tap`)
- Modify: `app/src/test/js/unit/tap.test.mjs` (add the stub cases)
- Modify: `app/src/test/js/browser/call-structure.spec.mjs` (add the end-to-end case)

**Interfaces:**
- Consumes: everything from Tasks 1-6.
- Produces: no new exports. `tap`'s signature is unchanged; only its behaviour on a stub changes.

- [ ] **Step 1: Write the failing reachability sweep**

Create `app/src/test/js/unit/reachability.test.mjs`:

```js
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadCorpus } from './corpus.mjs';
import { opening, tap, screen } from '../../../main/resources/viewer/model.mjs';

const corpus = loadCorpus();

/**
 * Click everything on screen, repeatedly, until nothing new appears.
 *
 * Bounded by the payload: a click only ever adds, and there are finitely many leaves, so the set
 * grows or the loop stops. Box clicks are left out on purpose - a box can fold, so including them
 * would let the walk oscillate, and the claim being made is that the reader can get everywhere by
 * clicking what they can see.
 */
function fixpoint(payload) {
  let revealed = opening(payload);
  for (;;) {
    const before = screen(payload, revealed).showing;
    for (const id of before) revealed = tap(payload, revealed, id);
    const after = screen(payload, revealed).showing;
    if (after.size === before.size) return after;
  }
}

// The property d9c12ff swept 63 fixtures for by hand and nothing re-checked. `member` is what that
// cost: main calls app.func1() passing and returning nothing, so no edge crosses into the callee,
// and the stub standing in for it did nothing when clicked.
test('every leaf can be put on screen by clicking what is on screen', () => {
  const short = [];
  for (const { name, payload } of corpus) {
    const leaves = payload.nodes.filter((n) => n.type !== 'METHOD');
    const reached = fixpoint(payload);
    if (reached.size !== leaves.length) short.push(`${name}: ${reached.size} of ${leaves.length}`);
  }
  assert.deepEqual(short, [], `fixtures the reader cannot fully reach:\n  ${short.join('\n  ')}`);
});
```

- [ ] **Step 2: Run it to verify it fails, and record which fixtures**

Run: `node --test app/src/test/js/unit/reachability.test.mjs`
Expected: FAIL, listing `member: 5 of 23` and probably others. Copy the full list into the commit message in Step 9 — it is the measurement of what this fixes.

- [ ] **Step 3: Add the failing unit tests for the stub click**

Append to `app/src/test/js/unit/tap.test.mjs`:

```js
// The bug this whole change exists to fix. `fR` is on screen because f is closed and its caller is
// open, it carries f's name, it is the obvious thing to press - and a RETURN node that no value
// flows through has no neighbours, so the walk returned it to itself and nothing happened.
test('clicking a stub opens the box it stands for', () => {
  assert.deepEqual(after(['mR', 'x'], 'fR'), ['a', 'fR', 'mR', 'x']);
});

// One level per click. If a stub click opened the boxes below it too, one press would unfold the
// call tree - the wall the viewer exists to avoid.
test('clicking a stub does not open the boxes inside it', () => {
  assert.equal(after(['mR', 'x'], 'fR').includes('b'), false);
});

// An open box's own RETURN is not a stub: it is revealed like any other leaf, so clicking it means
// what clicking a value means. Reading `y` here is the assertion - a stub click would have added a.
test('clicking the RETURN of an already open box walks its neighbourhood', () => {
  assert.deepEqual(after(['mR', 'x', 'a', 'fR'], 'x'), ['a', 'fR', 'mR', 'x', 'y']);
});
```

- [ ] **Step 4: Run them to verify they fail**

Run: `node --test app/src/test/js/unit/tap.test.mjs`
Expected: FAIL — *clicking a stub opens the box it stands for* gets `['fR', 'mR', 'x']`, missing `a`.

- [ ] **Step 5: Route a stub click to its box**

In `model.mjs`, insert into `tap` between the box branch and the neighbourhood walk:

```js
  // A stub is on screen only because its box is closed, so clicking it means "open this method".
  // Following its edges instead is what left `member` unopenable: a RETURN node that carries no
  // value back to its caller has no edges at all, so the walk returns it to itself.
  //
  // This is not the rule withStubs enforces, and the two must not be merged. That one governs the
  // derivation: a stub counting as its box being open would offer that box's callees' stubs, and
  // theirs, and one pass would unfold every method in the payload. A click cannot cascade - opening
  // this box only offers the stubs one level down, each needing a press of its own.
  if (!revealed.has(id) && stubOf(payload.nodes).get(node.parent) === id) {
    for (const leaf of ownLeaves(payload.nodes, node.parent)) next.add(leaf);
    return next;
  }
```

- [ ] **Step 6: Run the unit tests to verify they pass**

Run: `node --test app/src/test/js/unit/tap.test.mjs`
Expected: PASS (10 tests) — the 7 from Task 3 unchanged, plus the 3 new ones.

- [ ] **Step 7: Run the reachability sweep to verify it passes**

Run: `node --test app/src/test/js/unit/reachability.test.mjs`
Expected: PASS. If any fixture is still short, **stop and report it with its name and counts** — a box with no `RETURN` node has no stub and cannot be opened, which would be a real gap in `withStubs` rather than something to exclude from the sweep.

- [ ] **Step 8: Add the browser case**

Add a leaf-tapping helper beside the existing `tapBox` at the top of `app/src/test/js/browser/call-structure.spec.mjs` — by label, not by id, because ids are serials and move whenever the graph does:

```js
const tapLeaf = (page, label) => page.evaluate((l) => window.cy.nodes()
  .filter((n) => n.data('type') !== 'METHOD' && n.data('label') === l).emit('tap'), label);
```

Then append inside the existing `test.describe('member: a callee no edge reaches', …)` block, which already has `open('member')` in its `beforeEach`:

```js
// The end-to-end half of the unit test: the node the reader can see is the node that opens the
// method. `func1` matches exactly one leaf - the box of the same name is filtered out by type -
// and the count before is what makes this fail if the click does nothing.
test('clicking the stub of a closed method opens its body', async ({ page }) => {
  expect(await leafLabels(page)).toEqual(['App', 'app', 'args', 'func1', 'main']);
  await tapLeaf(page, 'func1');
  const opened = await leafLabels(page);
  expect(opened).toHaveLength(23);
  expect(opened).toContain('memberX');
});
```

- [ ] **Step 9: Run everything**

Run: `./gradlew test --rerun-tasks && npm test && npm run test:browser`
Expected: PASS throughout. `git status` must show no modified `truth.md` — this change moves nothing in the graph, only what is on screen.

- [ ] **Step 10: Confirm the sweep catches the bug it was written for (mutation)**

Temporarily delete the stub branch added in Step 5. Run `node --test app/src/test/js/unit/reachability.test.mjs` and confirm it **fails** with `member: 5 of 23`, and `npm run test:browser` and confirm the new browser test **fails**. Restore.

- [ ] **Step 11: Commit**

```bash
git add app/src/main/resources/viewer/model.mjs \
        app/src/test/js/unit/tap.test.mjs \
        app/src/test/js/unit/reachability.test.mjs \
        app/src/test/js/browser/call-structure.spec.mjs
git commit -m "Make the node standing for a closed method open it

A stub carries the method's name, sits where the method is, and did nothing when clicked: it is a
RETURN node, and a method that passes and returns nothing has no edges at all, so the walk from it
returned it to itself. member opened on 5 of its 23 leaves with all five inert, and the only way in
was the thin box border around the stub.

Clicking a stub now opens its box, one level. The rule in withStubs is untouched - a stub still
does not count as its box being open, or one pass would unfold the whole call tree - because that
governs the derivation and this governs a gesture, and they were sharing one sentence.

The sweep is what says it is fixed and stays fixed: from the opening view, clicking what is on
screen to a fixpoint now reaches every leaf of every fixture. [paste the Step 2 list here]"
```

---

### Task 8: Fold the change into CLAUDE.md

**Files:**
- Modify: `CLAUDE.md` (the "The interactive viewer" section, and "The viewer's tests")

**Interfaces:**
- Consumes: everything above. Produces: no code.

- [ ] **Step 1: Describe the split in "The interactive viewer"**

After the paragraph beginning "`HtmlExporter` substitutes the vendored libraries", add:

```markdown
The viewer is two files and the boundary is the point. `model.mjs` is every decision about what is
on screen — `opening`, `tap`, `screen`, and the functions they rest on — as pure functions of
`(payload, revealed)`, with no DOM and no Cytoscape, so `node --test` imports it directly.
`viewer.mjs` is the renderer: it builds the graph, and on every click calls `tap` and writes what
`screen` returns. It decides nothing.

They are not linked by an import. The page is one self-contained file opened from `file://`, where
an inlined `<script type="module">` has nothing to resolve `./model.mjs` against, and `HtmlExporter`
is substitution only so it cannot strip an import either. So `model.mjs` is substituted into the
same module script directly above `viewer.mjs` and its exports are free identifiers there — which
is why `viewer.mjs` must never be loaded on its own.

`screen` returns `display: null` for every `METHOD` node rather than a string. That is the "never
set `display` on a `METHOD` node" rule expressed as data: as an `if` in the render loop it was a
rule no test could reach, and the failure it guards — a box whose only visible node is a grandchild
being hidden, leaving the grandchild nowhere to live — is reachable by clicking.
```

- [ ] **Step 2: Correct the stub sentence**

In the paragraph describing stubs, split the one rule into two. Replace the sentence saying a stub does not open the box it stands for with:

```markdown
Two rules that read alike and are not the same. In `withStubs`, a stub does not count as its box
being **open** — otherwise offering one callee's stub would open that callee, which would offer its
callees' stubs, and one pass would unfold the entire call tree at load. But **clicking** a stub does
open its box, one level, and cannot cascade, because the newly-open box only offers the stubs
directly inside it and each needs a press of its own. Merging the two is what left `member`
unopenable: its stub is a `RETURN` node for a method that passes and returns nothing, so it has no
edges, and the neighbourhood walk returned it to itself.
```

- [ ] **Step 3: Describe the sweeps in "The viewer's tests"**

Extend the `app/src/test/js/unit/` bullet:

```markdown
  The corpus sweeps live here too, over the `graph.json` the golden suite writes beside each
  fixture (gitignored, rewritten every run, like `ir.txt`). `invariants.test.mjs` asserts that a box
  offered to the reader has something showing inside it, that no `METHOD` node is given a display,
  and that the badges' hidden counts add up to the edges actually crossing the screen edge.
  `reachability.test.mjs` asserts the one that matters: from the opening view, clicking what is on
  screen to a fixpoint reaches every leaf. That property was checked by hand once and then broke.

  `corpus.mjs` fails when it finds fewer than 60 payloads rather than sweeping an empty list — a
  checkout that has not run `./gradlew test` has none, and a sweep over nothing passes every
  property it is given.
```

- [ ] **Step 4: Commit**

```bash
git add CLAUDE.md
git commit -m "Write down where the viewer's decisions live now"
```

---

## Self-Review

**Spec coverage.** Every section of `docs/superpowers/specs/2026-08-09-viewer-view-model-design.md` maps to a task: the split → Task 1; `opening`/`tap`/`screen` → Tasks 2, 3, 4; the `screen` shape including `display: null`, `stubs`, and `edges[].visible` → Task 4; `graph.json` from `AppTest.writePage` plus the gitignore → Task 5; P2/P3/P4 and the fewer-than-60 guard → Task 6; P1 and the stub-click fix → Task 7. The spec's "not in scope" items (no renderer swap, no payload change, goldens untouched) are enforced by Task 5 Step 3 and Task 7 Step 9, which both check that no `truth.md` moved.

**Type consistency.** `stubOf(nodes)` returns `Map<boxId, stubId>` and is called that way in `withStubs` (Task 1), `screen` (Task 4) and `tap` (Task 7). `descendantLeaves(nodes, boxId)` returns `Set<id>` and is consumed by `tap` (Task 3) and the P2 sweep (Task 6). `screen` returns `showing`/`stubs` as Sets and `hidden` as a Map throughout; Task 6's P4 iterates `hidden.values()` as `{in, out}`, matching `hiddenDegree`. `tap(payload, revealed, id)` keeps one signature across Tasks 3 and 7. `loadCorpus()` returns `[{name, payload}]` and both sweep files destructure it that way.

**Ordering.** Task 6 depends on Task 5's `graph.json`; Task 7 depends on Task 6's `corpus.mjs`. Task 7's reachability test is written before the fix, so the fix is driven by a red test rather than confirmed after the fact.

**Known risk.** Task 7's fixpoint is `O(showing × payload)` per round across 63 fixtures. It is specified for correctness, not speed. If it drags past a few seconds, that is a finding to report, not a reason to sample fixtures.
