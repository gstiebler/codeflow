# Progressive Reveal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the viewer's fold-and-dim interaction with one that opens nearly empty and grows a dataflow neighbourhood outward from whatever is clicked.

**Architecture:** One piece of state — a `Set` of revealed **leaf** node ids. Method boxes and edges are never touched; Cytoscape derives their visibility from their descendants and endpoints. Clicking a leaf unions in a depth-3 undirected ball; clicking a box subtracts its leaf descendants; `R` resets.

**Tech Stack:** Kotlin (exporters), Cytoscape.js + ELK (browser), `node --test` (unit), Playwright (browser).

## Global Constraints

- Spec: `docs/superpowers/specs/2026-08-06-progressive-reveal-design.md`.
- **Never set `display` on a `METHOD` node.** Cytoscape derives box visibility from descendants, transitively. `display:none` on a box hides visible grandchildren that have nowhere else to live. Verified on a real page.
- All behaviour lives in `viewer.mjs`. `HtmlExporter` is substitution only.
- Every exporter writes to **stdout**; nothing else may.
- Pure functions are exported from `viewer.mjs` and imported directly by `node --test`. Anything touching `document` or `cytoscape` stays inside `init()`.
- Every negative assertion (`not.toContain`, "box gone") must be paired with a positive one in the same test. A page that reveals nothing satisfies every negative assertion trivially — this has already produced two false greens in this viewer.
- `REVEAL_DEPTH = 3`.

## File Structure

| File | Change | Responsibility |
|---|---|---|
| `app/src/main/resources/viewer/viewer.mjs` | Modify | `neighbourhood` + the reveal interaction. All behaviour. |
| `app/src/test/js/unit/neighbourhood.test.mjs` | Create | The pure function, incl. the depth bound and cycles. |
| `app/src/test/js/unit/trace.test.mjs` | Delete | `traceFrom` is gone. |
| `app/src/test/js/browser/viewer.spec.mjs` | Modify | Reveal, accumulate, fold, reset. |
| `app/src/main/resources/viewer/template.html` | Modify | Drop the expand-collapse `<script>`; new hint text. |
| `app/src/main/kotlin/codeflow/HtmlExporter.kt` | Modify | Drop the `__EXPAND_COLLAPSE__` substitution. |
| `app/src/main/resources/viewer/cytoscape-expand-collapse.js` | Delete | Library no longer used. |
| `package.json` | Modify | Drop the `cytoscape-expand-collapse` devDependency. |
| `CLAUDE.md` | Modify | Replace the folding/meta-edge section with the reveal model. |

## Reference: the `funcCall` fixture

Every browser assertion below is derived from this. Do not re-derive it; it was computed from the real payload.

- **39 nodes** total (34 leaves + 5 `METHOD` boxes), **26 edges**.
- Boxes nest: `main` > `methodA`, `main` > `methodB` > `methodC` ×2.
- **Opening set** (main's own leaves, 8): `5, 8, app, args, e, main, x, y`.
- **Depth-3 ball from `x`** (6): `x, 5, a, +, b, c`. New vs. opening: `a, +, b, c`.
- **Exactly 4 hops from `x`**: the `methodA` *return* node, and `8`. Only `methodA` is a usable off-by-one guard, because `8` is already in the opening set.
- **Depth-3 ball from `e`** (4): `e, methodB, d, methodC`. New vs. opening: `methodB, d, methodC`.

Note `methodA` and `methodC` each name **both** a box and a leaf return node. Always filter by `type` in assertions.

---

### Task 1: The `neighbourhood` function

Adds the pure function only. `viewer.mjs` still uses `traceFrom` after this task, so everything stays green.

**Files:**
- Modify: `app/src/main/resources/viewer/viewer.mjs`
- Test: `app/src/test/js/unit/neighbourhood.test.mjs` (create)

**Interfaces:**
- Consumes: nothing.
- Produces: `export function neighbourhood(edges, startId, depth) -> Set<string>` and `export const REVEAL_DEPTH = 3`. `edges` is the payload's array of `{source, target}`. Returns a `Set` always containing `startId`.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/js/unit/neighbourhood.test.mjs`:

```javascript
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { neighbourhood } from '../../../main/resources/viewer/viewer.mjs';

const ids = (set) => [...set].sort();
const chain = [
  { source: 'a', target: 'b' },
  { source: 'b', target: 'c' },
  { source: 'c', target: 'd' },
];

test('depth 0 returns only the start node', () => {
  assert.deepEqual(ids(neighbourhood(chain, 'b', 0)), ['b']);
});

// Undirected on purpose: `c = a + b` clicked at `a` has to show `b`, or the operator is
// drawn with one operand missing.
test('reaches neighbours in both directions', () => {
  assert.deepEqual(ids(neighbourhood(chain, 'b', 1)), ['a', 'b', 'c']);
});

// The assertion that fails if the bound is off by one. `d` is exactly one hop too far.
test('stops exactly at the depth bound', () => {
  assert.deepEqual(ids(neighbourhood(chain, 'a', 2)), ['a', 'b', 'c']);
});

// Reaching `d` by the short edge must not be defeated by also reaching it the long way round.
// A LIFO walk can record the long distance first and prune from there.
test('uses the shortest path to a node reachable two ways', () => {
  const diamond = [
    { source: 'a', target: 'b' },
    { source: 'b', target: 'c' },
    { source: 'c', target: 'd' },
    { source: 'a', target: 'x' },
    { source: 'x', target: 'd' },
  ];
  // d is 2 hops via x, 3 via b/c. At depth 2 it must be in.
  assert.ok(neighbourhood(diamond, 'a', 2).has('d'));
});

// A for-loop's counter flows into itself, so the graph really does contain cycles. A naive walk
// follows one forever and the page hangs with nothing on screen to explain why.
test('terminates on a cycle', () => {
  const edges = [{ source: 'a', target: 'b' }, { source: 'b', target: 'a' }];
  assert.deepEqual(ids(neighbourhood(edges, 'a', 10)), ['a', 'b']);
});

test('returns a lone node with no edges at all', () => {
  assert.deepEqual(ids(neighbourhood([], 'lonely', 3)), ['lonely']);
});

// Two calls to one method are two subgraphs sharing no nodes. Revealing from inside one must not
// pull in the other, which is the whole reason nodes are per-occurrence.
test('does not cross into a sibling call with the same shape', () => {
  const edges = [
    { source: 'arg1', target: 'param1' }, { source: 'param1', target: 'ret1' },
    { source: 'arg2', target: 'param2' }, { source: 'param2', target: 'ret2' },
  ];
  assert.deepEqual(ids(neighbourhood(edges, 'param1', 5)), ['arg1', 'param1', 'ret1']);
});
```

- [ ] **Step 2: Run to verify it fails**

Run: `npm test`
Expected: FAIL — `The requested module ... does not provide an export named 'neighbourhood'`.

- [ ] **Step 3: Implement it**

In `app/src/main/resources/viewer/viewer.mjs`, add below `traceFrom` (leave `traceFrom` in place for now):

```javascript
/** How far a click reaches. Three hops is enough to cross a call and land in the callee's body. */
export const REVEAL_DEPTH = 3;

/**
 * Every node within `depth` edges of `startId`, following edges in either direction.
 *
 * Breadth-first, and that matters: the walk is bounded, so a node reached by a long path before
 * a short one would be recorded at the wrong distance and pruned early. `traceFrom` popped from
 * the end of its queue, which is harmless for an unbounded closure and wrong here.
 *
 * `distance` is also what makes this terminate, since the graph has cycles wherever a loop feeds
 * a variable back into itself.
 */
export function neighbourhood(edges, startId, depth) {
  const adjacent = new Map();
  const link = (from, to) => {
    if (!adjacent.has(from)) adjacent.set(from, []);
    adjacent.get(from).push(to);
  };
  for (const edge of edges) {
    link(edge.source, edge.target);
    link(edge.target, edge.source);
  }

  const distance = new Map([[startId, 0]]);
  const queue = [startId];
  // Index rather than shift(): same order, without re-indexing the array on every step.
  for (let head = 0; head < queue.length; head += 1) {
    const id = queue[head];
    if (distance.get(id) === depth) continue;
    for (const next of adjacent.get(id) ?? []) {
      if (!distance.has(next)) {
        distance.set(next, distance.get(id) + 1);
        queue.push(next);
      }
    }
  }
  return new Set(distance.keys());
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `npm test`
Expected: PASS — 7 `neighbourhood` tests plus the 5 existing `traceFrom` tests, 12 total.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/resources/viewer/viewer.mjs app/src/test/js/unit/neighbourhood.test.mjs
git commit -m "Bound a walk to a neighbourhood instead of a whole closure"
```

---

### Task 2: Reveal on click

Swaps the interaction. Dimming, tracing and the expand-collapse *calls* all go; the library file itself is removed in Task 4.

**Files:**
- Modify: `app/src/main/resources/viewer/viewer.mjs`
- Modify: `app/src/test/js/browser/viewer.spec.mjs`
- Delete: `app/src/test/js/unit/trace.test.mjs`

**Interfaces:**
- Consumes: `neighbourhood`, `REVEAL_DEPTH` from Task 1.
- Produces: a page where `window.cy` holds the whole payload and visibility is read with `node.visible()`. **`window.api` no longer exists** — nothing replaces it; tests read `cy` directly.

- [ ] **Step 1: Rewrite the browser tests**

Replace the whole body of `app/src/test/js/browser/viewer.spec.mjs` below the `beforeEach` block. Keep the file's existing imports, `PAGE` constant and `beforeEach` exactly as they are, and keep the `draws the canvas at a usable size` test. Delete `counts`, `methodState`, `tapNode`, `markedCount` and every trace/fold/meta-edge test. Add:

```javascript
const leafLabels = (page) => page.evaluate(() => window.cy.nodes()
  .filter((n) => n.data('type') !== 'METHOD' && n.visible())
  .map((n) => n.data('label')).sort());

const boxLabels = (page) => page.evaluate(() => window.cy.nodes('[type = "METHOD"]')
  .filter((n) => n.visible())
  .map((n) => n.data('label')).sort());

const tapLeaf = (page, label) => page.evaluate((l) => window.cy.nodes()
  .filter((n) => n.data('type') !== 'METHOD' && n.data('label') === l).emit('tap'), label);

const tapBox = (page, label) => page.evaluate((l) => window.cy.nodes('[type = "METHOD"]')
  .filter((n) => n.data('label') === l).emit('tap'), label);

const OPENING = ['5', '8', 'app', 'args', 'e', 'main', 'x', 'y'];

// Hiding, not removing. Under the old folding this read 11 - which is exactly the trap that made
// a node count comparable to the payload only after expanding everything first.
test('holds the whole payload however little is displayed', async ({ page }) => {
  const { nodes, edges } = await page.evaluate(() => ({
    nodes: window.cy.nodes().length,
    edges: window.cy.edges().length,
  }));
  expect(nodes).toBe(39);
  expect(edges).toBe(26);
});

test('opens showing the entry method body and nothing from a callee', async ({ page }) => {
  expect(await leafLabels(page)).toEqual(OPENING);
  // No callee has a visible node, so no callee box is drawn.
  expect(await boxLabels(page)).toEqual(['main']);
});

test('clicking a node reveals its neighbourhood three hops out', async ({ page }) => {
  await tapLeaf(page, 'x');
  const leaves = await leafLabels(page);
  // a, + , b and c are 1..3 hops from x and were all hidden a moment ago.
  expect(leaves).toContain('a');
  expect(leaves).toContain('+');
  expect(leaves).toContain('b');
  expect(leaves).toContain('c');
  // methodA's return node is exactly 4 hops out. This is what fails if the bound is off by one,
  // and it only means anything next to the four assertions above.
  expect(leaves).not.toContain('methodA');
  // The box appears because it now contains something, never because we showed it.
  expect(await boxLabels(page)).toEqual(['main', 'methodA']);
});

test('reveals accumulate across clicks', async ({ page }) => {
  await tapLeaf(page, 'x');
  const afterX = await leafLabels(page);
  await tapLeaf(page, 'e');
  const afterE = await leafLabels(page);

  // Nothing the first click revealed may vanish on the second.
  for (const label of afterX) expect(afterE).toContain(label);
  // And the second click has to actually add something, or the loop above proves nothing.
  expect(afterE.length).toBeGreaterThan(afterX.length);
  expect(afterE).toContain('d');
});
```

- [ ] **Step 2: Run to verify they fail**

Run: `npm run test:browser`
Expected: FAIL. `holds the whole payload` reports 11 nodes (expand-collapse removed the rest); the opening/reveal tests fail because clicking still traces.

- [ ] **Step 3: Rewrite `init`**

In `app/src/main/resources/viewer/viewer.mjs`: delete `traceFrom` entirely, and delete the `.dimmed` and `.traced` style rules and the `edge.cy-expand-collapse-meta-edge` rule. Replace everything from `const api = cy.expandCollapse({` down to `window.api = api;` with:

```javascript
  const isBox = (node) => node.data('type') === 'METHOD';
  const entryBox = cy.nodes('[type = "METHOD"]').filter((n) => n.isOrphan());
  // The entry method's own values, and nothing from anything it calls.
  const opening = () => new Set(
    entryBox.children().filter((n) => !isBox(n)).map((n) => n.id()),
  );

  let revealed = opening();

  const apply = () => {
    for (const node of cy.nodes()) {
      // Never a box. Cytoscape works a box's visibility out from its descendants, transitively -
      // display:none here would hide a box whose only visible node is a grandchild, and that
      // grandchild would have nowhere to live.
      if (isBox(node)) continue;
      node.style('display', revealed.has(node.id()) ? 'element' : 'none');
    }
    cy.layout(LAYOUT).run();
  };

  cy.on('tap', 'node', (event) => {
    const node = event.target;
    if (isBox(node)) {
      // descendants(), not children(): a box holds boxes, and folding one has to take the lot.
      for (const inside of node.descendants()) {
        if (!isBox(inside)) revealed.delete(inside.id());
      }
    } else {
      for (const id of neighbourhood(payload.edges, node.id(), REVEAL_DEPTH)) revealed.add(id);
    }
    apply();
  });

  apply();

  // The browser tests read the graph off this. Nothing in the page uses it.
  window.cy = cy;
```

Then delete `app/src/test/js/unit/trace.test.mjs`.

- [ ] **Step 4: Run to verify it passes**

Run: `npm test && npm run test:browser`
Expected: `npm test` PASS with 7 tests (the 5 `traceFrom` ones are gone). `npm run test:browser` PASS with 5 tests.

- [ ] **Step 5: Confirm the tests fail against a wrong implementation**

Temporarily change the reveal line to ignore the bound:

```javascript
      for (const id of payload.nodes.map((n) => n.id)) revealed.add(id);
```

Run: `npm run test:browser`
Expected: **two** tests fail — `clicking a node reveals its neighbourhood three hops out` on `not.toContain('methodA')`, and `reveals accumulate across clicks` on `afterE.length > afterX.length`, because the first click already revealed everything and the second adds nothing. `opens showing the entry method body` still passes, since it runs before any click. **Revert the change** and re-run to confirm green.

- [ ] **Step 6: Commit**

```bash
git add -A app/src/main/resources/viewer/viewer.mjs app/src/test/js/browser/viewer.spec.mjs app/src/test/js/unit/trace.test.mjs
git commit -m "Grow the graph outward from a click instead of dimming it"
```

---

### Task 3: Fold a box, and reset

The tap handler already folds; this task tests it and adds `R`.

**Files:**
- Modify: `app/src/main/resources/viewer/viewer.mjs`
- Modify: `app/src/main/resources/viewer/template.html`
- Modify: `app/src/test/js/browser/viewer.spec.mjs`

**Interfaces:**
- Consumes: `revealed`, `opening()`, `apply()` from Task 2.
- Produces: a `keydown` listener on `document` for `r`/`R`.

- [ ] **Step 1: Write the failing tests**

Append to `app/src/test/js/browser/viewer.spec.mjs`:

```javascript
test('folding a method box hides its contents, nested boxes and all', async ({ page }) => {
  await tapLeaf(page, 'e');
  // methodC's return node sits two levels down, inside methodB's methodC box.
  expect(await boxLabels(page)).toEqual(['main', 'methodB', 'methodC']);

  await tapBox(page, 'methodB');
  // Both go. If the fold used children() instead of descendants(), methodC would survive.
  expect(await boxLabels(page)).toEqual(['main']);
  expect(await leafLabels(page)).toEqual(OPENING);
});

test('R returns to the opening set', async ({ page }) => {
  await tapLeaf(page, 'x');
  await tapLeaf(page, 'e');
  // Without this, the reset below would pass on a page where clicking never revealed anything.
  expect((await leafLabels(page)).length).toBeGreaterThan(OPENING.length);

  await page.keyboard.press('r');
  expect(await leafLabels(page)).toEqual(OPENING);
});
```

- [ ] **Step 2: Run to verify they fail**

Run: `npm run test:browser`
Expected: the fold test PASSES already (Task 2 implemented folding); `R returns to the opening set` FAILS — the leaf list is unchanged after the keypress.

Note the fold test passing here is expected, not a TDD violation: it is the first assertion covering behaviour Task 2 wrote but did not test.

The spec also lists "a box is visible when only a grandchild is revealed". That state is not
reachable by clicking — every route into `methodC` passes through a `methodB` leaf, so `methodB`
always has visible children of its own — and it is Cytoscape's behaviour rather than ours. The fold
test covers the direction that *is* ours and can break: `descendants()` vs `children()`. Do not add
a test hook to `viewer.mjs` to force the unreachable case.

- [ ] **Step 3: Add the reset**

In `viewer.mjs`, immediately after the `cy.on('tap', 'node', ...)` handler:

```javascript
  // Folding needs a box, so a sprawl inside the entry method has nothing to fold. Without this
  // the only way back is a reload, which re-runs the whole layout.
  document.addEventListener('keydown', (event) => {
    if (event.key === 'r' || event.key === 'R') {
      revealed = opening();
      apply();
    }
  });
```

- [ ] **Step 4: Update the hint text**

In `app/src/main/resources/viewer/template.html`, replace the `#hint` div's contents with:

```html
<div id="hint">click a node to reveal what it flows through &middot; click a method box to hide it &middot; press R to reset</div>
```

- [ ] **Step 5: Run to verify it passes**

Run: `npm run test:browser`
Expected: PASS, 7 tests.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/resources/viewer/viewer.mjs app/src/main/resources/viewer/template.html app/src/test/js/browser/viewer.spec.mjs
git commit -m "Fold a box away, and find the way back"
```

---

### Task 4: Drop cytoscape-expand-collapse

Nothing has called it since Task 2. This removes the weight and the documentation it required.

**Files:**
- Delete: `app/src/main/resources/viewer/cytoscape-expand-collapse.js`
- Modify: `app/src/main/kotlin/codeflow/HtmlExporter.kt`
- Modify: `app/src/main/resources/viewer/template.html`
- Modify: `package.json`
- Modify: `CLAUDE.md`

**Interfaces:**
- Consumes: nothing.
- Produces: `HtmlExporter` substitutes five tokens, not six.

- [ ] **Step 1: Remove the substitution**

In `app/src/main/kotlin/codeflow/HtmlExporter.kt`, delete this line:

```kotlin
            .replace("/*__EXPAND_COLLAPSE__*/", asset("cytoscape-expand-collapse.js"))
```

- [ ] **Step 2: Remove the script tag**

In `app/src/main/resources/viewer/template.html`, delete this line:

```html
<script>/*__EXPAND_COLLAPSE__*/</script>
```

- [ ] **Step 3: Remove the library and the dependency**

```bash
git rm app/src/main/resources/viewer/cytoscape-expand-collapse.js
```

In `package.json`, delete the `"cytoscape-expand-collapse": "^4.1.1",` line from `devDependencies`.

- [ ] **Step 4: Run everything**

Run: `./gradlew build && npm test && npm run test:browser`
Expected: all PASS — Kotlin 34 tests, unit 7, browser 7. The page still builds because nothing referenced the token.

- [ ] **Step 5: Update CLAUDE.md**

In the `### The interactive viewer` section: drop `cytoscape-expand-collapse` from the library list, and replace the two bullets about collapsing removing children and meta-edges, plus the `traceFrom` paragraph, with:

```markdown
Visibility is derived, and that is the one rule to respect: **never set `display` on a `METHOD`
node.** A `Set` of revealed *leaf* ids is the only state. Cytoscape works out the rest — an edge
hides when either endpoint does, and a box hides when every descendant does, transitively. Setting
`display:none` on a box breaks the transitive case: a box whose only visible node is a grandchild
would be hidden, and the grandchild would have nowhere to live.

Nothing is ever removed from the graph, so `cy.nodes().length` is always the payload's node count.

`neighbourhood(edges, startId, depth)` is the click behaviour: an undirected ball of radius
`REVEAL_DEPTH`. It is breadth-first on purpose — the walk is bounded, so a node first reached by a
long path would be recorded at the wrong distance and pruned early. Undirected on purpose too: for
`c = a + b`, clicking `a` shows `b`, because an operator drawn with one operand missing is worse
than one more node.

Clicks union into the revealed set and never subtract. Only a box click (which removes its leaf
*descendants* — a box holds boxes) or `R` takes anything away.
```

In the `### The viewer's tests` section, replace the first bullet (which names `traceFrom`) with:

```markdown
- `app/src/test/js/unit/` (`npm test`) — the pure functions, imported straight from `viewer.mjs`.
  `neighbourhood` is tested here: the depth bound, a node reachable by both a short and a long
  path, and that it terminates on a cycle.
```

Both traps below it (`#graph` not `#cy`, and negative assertions needing a positive partner) stay
exactly as they are — both still apply, and the second is now load-bearing for the reveal tests.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "Retire the folding library the reveal replaced"
```

---

## Verification

After Task 4, from a clean tree:

```bash
./gradlew build && npm test && npm run test:browser
```

Expected: Kotlin 34, unit 7, browser 7, all green.

Then confirm the page by eye — the automated tests check classes and counts, not that anything is legible:

```bash
./gradlew -q run --args="$(pwd)/app/src/test/resources/funcCall --html" > /tmp/reveal.html
```

Open it. It should show ~8 nodes in one `main` box. Click `x`: `methodA` appears containing `a`, `b`, `+`, `c`. Click `e`: `methodB` appears, with a `methodC` box nested inside it, and everything from the `x` click is still there. Click the `methodB` box: it and its nested `methodC` vanish together. Press `R`: back to ~8 nodes.
