# React Flow Renderer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Draw the graph with React Flow, make that the page `--html` emits, and keep the Cytoscape
page working from the same view model — so that what the model fails to state gets drawn wrong by
one renderer and right by the other, which is a disagreement a test can read.

**Architecture:** The viewer becomes TypeScript behind esbuild. `model.ts` stays pure and
Node-tested; `layout.ts` turns a view into an ELK graph, also pure; `cytoscape.ts` and
`reactflow.tsx` are two adapters over the same `screen()` output, each bundled into one committed
IIFE that `HtmlExporter` inlines. `screen()` grows `visible` on every node — including boxes, where
it means "some descendant leaf is showing" — because React Flow derives none of what Cytoscape
derives.

**Tech Stack:** TypeScript (Node 26 type stripping, no build for the tested layer), esbuild, React 18,
`@xyflow/react` 12, elkjs, Cytoscape + cytoscape-elk, `node --test`, Playwright, Kotlin/Gradle.

## Global Constraints

- **This plan starts where `docs/superpowers/plans/2026-08-09-viewer-view-model.md` finishes.** Do
  not begin Task 1 until that plan's Task 8 is committed on `rebuild-batch-1` and this branch is
  rebased onto it. `model.mjs` must already export `opening`, `tap` and `screen`.
- `model.ts`, `theme.ts` and `layout.ts` have **no imports, no DOM, no library access**. `node --test`
  imports them directly.
- **Gradle never runs npm.** It substitutes a committed bundle. `HtmlExporter` stays substitution
  only — no regex, no logic.
- Node 26 (`nvm alias default 26`, v26.7.0) strips types, so `.ts` runs unbuilt. Gradle `test` and
  `run` still need JDK 25 (`def runtimeJdk = 25`).
- Unit tests live in `app/src/test/js/unit/` and must be named `*.test.ts` — `package.json` globs
  exactly that.
- No exporter's graph changes. `truth.md` goldens must not move; `git status` is checked for that at
  the end of Tasks 1, 2 and 4.
- Generated bundles are **committed**: `reactflow.bundle.js`, `cytoscape.bundle.js`. Never hand-edit
  one.
- Every new test must be confirmed against a **wrong** implementation, not merely an absent one.
  Each task names its mutation.
- The palette (`#2e7d32`, `#c62828`, `#6a6a6a` and `PALETTE`) has exactly one definition in
  `theme.ts` for the viewer side. `MermaidExporter`'s copy stays where it is.

---

### Task 1: TypeScript behind esbuild, with the Cytoscape page unchanged

**Files:**
- Create: `app/src/main/resources/viewer/model.ts` (from `model.mjs`)
- Create: `app/src/main/resources/viewer/theme.ts`
- Create: `app/src/main/resources/viewer/cytoscape.ts` (from `viewer.mjs`)
- Create: `app/src/main/resources/viewer/types.ts`
- Create: `scripts/build-viewer.mjs`
- Create: `app/src/test/js/unit/bundle.test.ts`
- Create: `tsconfig.json`, `app/src/main/resources/viewer/css.d.ts`
- Delete: `app/src/main/resources/viewer/model.mjs`, `viewer.mjs`, `cytoscape.min.js`,
  `elk.bundled.js`, `cytoscape-elk.js`
- Modify: `app/src/main/resources/viewer/template.html`
- Modify: `app/src/main/kotlin/codeflow/HtmlExporter.kt`
- Modify: `package.json`, `.gitignore`
- Rename: `app/src/test/js/unit/*.test.mjs` → `*.test.ts` (all of them), `corpus.mjs` → `corpus.ts`

**Interfaces:**
- Consumes: `model.mjs`'s exports as they stand after the prior plan — `REVEAL_DEPTH`,
  `neighbourhood(edges, startId, depth)`, `hiddenDegree(edges, revealed)`, `badgeLabel(name, hidden)`,
  `ownLeaves(nodes, boxId)`, `stubOf(nodes)`, `descendantLeaves(nodes, boxId)`,
  `withStubs(nodes, revealed)`, `opening(payload)`, `tap(payload, revealed, id)`,
  `screen(payload, revealed)`.
- Produces: the same exports, typed, from `model.ts`; `PALETTE`/`EDGE_COLOURS` from `theme.ts`; the
  shared types from `types.ts`; `npm run build:viewer`; `app/src/main/resources/viewer/cytoscape.bundle.js`.

- [ ] **Step 1: Add the dependencies**

Run:
```bash
npm install --save-dev typescript esbuild react@18 react-dom@18 @xyflow/react @types/react @types/react-dom
```

`react`, `react-dom` and `@xyflow/react` are installed here rather than in Task 4 so that one lockfile
change covers the whole plan. `cytoscape`, `cytoscape-elk`, `elkjs` and `@playwright/test` are already
there.

- [ ] **Step 2: Write `types.ts`**

Create `app/src/main/resources/viewer/types.ts`:

```ts
/**
 * The payload's shape and the view's, in one place.
 *
 * JsonExporter writes the payload; nothing here may add a field to it. `ViewNode` is what a renderer
 * is handed, and the two renderers must agree on it or the page they draw is not the same page.
 */

export type Id = string;

/** Every node type JsonExporter emits. METHOD is a box; everything else is a leaf. */
export type NodeType =
  | 'METHOD' | 'RETURN' | 'VARIABLE' | 'OBJ_VARIABLE' | 'LITERAL' | 'BIN_OP'
  | 'FUNC_PARAM' | 'EXTERNAL' | 'MEM_SPACE' | 'UNMODELLED';

export type EdgeKind = 'FLOW' | 'TRUE' | 'FALSE' | 'CONDITION';

export type PayloadNode = { id: Id; label: string; type: NodeType; parent?: Id };
export type PayloadEdge = { source: Id; target: Id; kind: EdgeKind };
export type Payload = { nodes: PayloadNode[]; edges: PayloadEdge[] };

/** How many edges at a node lead somewhere off screen, per direction. */
export type HiddenDegree = { in: number; out: number };

export type ViewNode = PayloadNode & {
  /** The label plus what is missing around it - `total ↑2 ↓3`. Never replaces `label`. */
  badge: string;
  /** On screen. For a box: some descendant leaf is showing. See Task 2. */
  visible: boolean;
};

export type ViewEdge = PayloadEdge & { visible: boolean };

export type View = {
  showing: Set<Id>;
  stubs: Set<Id>;
  hidden: Map<Id, HiddenDegree>;
  nodes: ViewNode[];
  edges: ViewEdge[];
};
```

- [ ] **Step 3: Move `model.mjs` to `model.ts` and type it**

`git mv app/src/main/resources/viewer/model.mjs app/src/main/resources/viewer/model.ts`, then add
types to every signature. **Do not change any logic or any doc comment.** Add to the top, under the
existing file header:

```ts
import type {
  Id, Payload, PayloadNode, PayloadEdge, HiddenDegree, View, ViewNode, ViewEdge,
} from './types.ts';
```

The `.ts` extension in the specifier is required: Node's type stripping resolves the real file, and
esbuild is configured to accept it. The signatures become:

```ts
export const REVEAL_DEPTH = 3;
export function neighbourhood(edges: PayloadEdge[], startId: Id, depth: number): Set<Id>
export function hiddenDegree(edges: PayloadEdge[], revealed: Set<Id>): Map<Id, HiddenDegree>
export function badgeLabel(name: string, hidden?: HiddenDegree): string
export function ownLeaves(nodes: PayloadNode[], boxId: Id): Set<Id>
export function stubOf(nodes: PayloadNode[]): Map<Id, Id>
export function descendantLeaves(nodes: PayloadNode[], boxId: Id): Set<Id>
export function withStubs(nodes: PayloadNode[], revealed: Set<Id>): Set<Id>
export function opening(payload: Payload): Set<Id>
export function tap(payload: Payload, revealed: Set<Id>, id: Id): Set<Id>
export function screen(payload: Payload, revealed: Set<Id>): View
```

and the one internal helper becomes `const isBoxNode = (node: PayloadNode) => node.type === 'METHOD';`.

Inside `hiddenDegree`, the local `count` needs `(id: Id, direction: 'in' | 'out')`. Inside
`descendantLeaves`, `childrenOf` is `new Map<Id, PayloadNode[]>()`. Nothing else needs an annotation.

- [ ] **Step 4: Write `theme.ts`**

Create `app/src/main/resources/viewer/theme.ts` by moving `PALETTE` and `EDGE_COLOURS` out of
`viewer.mjs` **with their comments unchanged**, adding:

```ts
import type { NodeType, EdgeKind } from './types.ts';

/**
 * One definition of what a colour means, because there are two renderers.
 *
 * A graph that changes colour between the Cytoscape page and the React Flow page is two claims
 * about one program, which is the failure this repo cares most about. MermaidExporter keeps its own
 * copy - it is Kotlin, and the duplication there is already documented in CLAUDE.md.
 */
export const PALETTE: Record<NodeType, string> = { /* ...unchanged... */ };

export const EDGE_COLOURS: Partial<Record<EdgeKind, string>> = { /* ...unchanged... */ };

/** The grey every FLOW edge keeps, which is nearly every edge on the page. */
export const EDGE_DEFAULT = '#999';
```

- [ ] **Step 5: Move `viewer.mjs` to `cytoscape.ts`**

`git mv app/src/main/resources/viewer/viewer.mjs app/src/main/resources/viewer/cytoscape.ts`. Replace
its file header with:

```ts
/**
 * The Cytoscape renderer.
 *
 * Kept because it is the second opinion: it derives an edge's visibility from its endpoints and a
 * box's from its descendants, so a page it draws differently from the React Flow one is a fact
 * about model.ts that no single renderer could have told us. --html-cytoscape is what emits it.
 *
 * Nothing here decides what is on screen. That is model.ts, where the tests are.
 */
```

Then make it import rather than rely on concatenation — it is bundled now, so imports resolve:

```ts
import cytoscape from 'cytoscape';
import elk from 'cytoscape-elk';
import { opening, screen, tap } from './model.ts';
import { PALETTE, EDGE_COLOURS } from './theme.ts';
import type { Id, Payload } from './types.ts';

cytoscape.use(elk);
```

`init` becomes `export function init(payload: Payload)`, `revealed` becomes `let revealed: Set<Id>`,
and the `window.init` block at the bottom is **deleted** — the bundle calls `init` itself (Step 7).
Everything else in the file stays as it is.

- [ ] **Step 6: Delete the vendored libraries**

```bash
git rm app/src/main/resources/viewer/cytoscape.min.js \
       app/src/main/resources/viewer/elk.bundled.js \
       app/src/main/resources/viewer/cytoscape-elk.js
```

They came from `node_modules` by hand; esbuild now resolves them from there directly, and a
committed copy is a second and staler answer to "which version is this".

- [ ] **Step 7: Write the two entry points**

Create `app/src/main/resources/viewer/cytoscape.entry.ts`:

```ts
/**
 * What the bundle runs. The page has one script and no module scope to call into, so the entry
 * point reads the payload the exporter substituted and starts the renderer itself.
 */
import { init } from './cytoscape.ts';
import type { Payload } from './types.ts';

declare const __PAYLOAD__: Payload;
init(__PAYLOAD__);
```

`__PAYLOAD__` is a name esbuild is told nothing about; `HtmlExporter` defines it in the page above the
bundle (Step 9), which keeps the exporter's job pure substitution.

- [ ] **Step 8: Write the build script**

Create `scripts/build-viewer.mjs`:

```js
/**
 * Bundles each renderer into one IIFE that HtmlExporter inlines.
 *
 * Committed output, so that `./gradlew build` never needs npm - the same contract the hand-vendored
 * libraries had. `npm test` rebuilds into a temp directory and compares, because a stale bundle is a
 * page rendering yesterday's model while every test passes on today's source.
 *
 * Pass a directory to write elsewhere; that is what the freshness check does.
 */
import { build } from 'esbuild';
import { join } from 'node:path';

const VIEWER = 'app/src/main/resources/viewer';

export const BUNDLES = [
  { entry: `${VIEWER}/cytoscape.entry.ts`, out: 'cytoscape.bundle.js' },
  { entry: `${VIEWER}/reactflow.entry.tsx`, out: 'reactflow.bundle.js' },
];

export async function buildViewer(outdir = VIEWER) {
  for (const { entry, out } of BUNDLES) {
    await build({
      entryPoints: [entry],
      outfile: join(outdir, out),
      bundle: true,
      format: 'iife',
      minify: true,
      target: 'es2020',
      jsx: 'automatic',
      // The CSS arrives as a string the renderer puts in a <style>, so the page stays one file.
      loader: { '.css': 'text' },
      // React ships both branches of this and picks at runtime; without it the dev build is bundled,
      // which is three times the size and warns in the console.
      define: { 'process.env.NODE_ENV': '"production"' },
      logLevel: 'warning',
    });
  }
}

if (import.meta.filename === process.argv[1]) await buildViewer();
```

Add to `package.json`:

```json
    "build:viewer": "node scripts/build-viewer.mjs",
    "test": "npm run check:bundle && node --test app/src/test/js/unit/*.test.ts",
    "check:bundle": "node --test app/src/test/js/unit/bundle.test.ts"
```

**Note for Task 4:** `BUNDLES` already lists `reactflow.entry.tsx`, which does not exist yet. Until
Task 4 creates it, comment that line out — and uncomment it in Task 4 Step 2. It is listed here so
the build script is written once.

- [ ] **Step 9: One slot in `template.html`**

Replace the whole `<script>` section (today's four script tags) with:

```html
<script>window.__PAYLOAD__ = /*__PAYLOAD__*/;</script>
<script>/*__BUNDLE__*/</script>
```

`__PAYLOAD__` is a global rather than an argument because the bundle is an IIFE with no exports for
the page to call into.

In `cytoscape.entry.ts`, change the declaration to match: `declare global { interface Window {
__PAYLOAD__: Payload } }` and call `init(window.__PAYLOAD__)`.

- [ ] **Step 10: Parameterise `HtmlExporter`**

Replace the substitution chain in `HtmlExporter.kt`:

```kotlin
/**
 * The graph as one self-contained page.
 *
 * Everything is inlined - the renderer's bundle and the payload - so the file opens from disk with
 * no server and no network, and can be handed to someone else as a single artifact.
 *
 * `bundle` names which renderer. React Flow is the default and what `--html` emits; the Cytoscape
 * bundle is still built and still tested, because a drawing that looks wrong needs something to be
 * checked against.
 *
 * There is deliberately no logic here beyond substitution. Anything that could make the graph wrong
 * lives in JsonExporter, where the tests are. The bundles are built by `npm run build:viewer` and
 * committed, so this never needs npm.
 */
class HtmlExporter(private val bundle: String = "reactflow.bundle.js") {
    ...
    val page = asset("template.html")
        .replace("/*__BUNDLE__*/", asset(bundle))
        .replace("/*__PAYLOAD__*/", payload.toString())
```

**Order matters and is easy to get backwards: the bundle goes first, the payload last** — which is
the order the existing chain already uses. The payload carries labels taken from the analysed source,
so a Java file containing the literal text `/*__BUNDLE__*/` in a string or a comment would, if the
payload were substituted first, have a megabyte of JavaScript spliced into the middle of the graph.
Going the other way is safe because the bundle is minified and holds no comments. These stay
`replace` and never `Regex.replace` for the reason already documented: the bundle is full of `$` and
`\` that a regex would read as group references.

For this task pass `HtmlExporter("cytoscape.bundle.js")` from `App.kt` and `AppTest`, so the page is
unchanged; Task 4 flips the default over.

- [ ] **Step 11: Rename the unit tests to TypeScript**

```bash
git mv app/src/test/js/unit/neighbourhood.test.mjs  app/src/test/js/unit/neighbourhood.test.ts
git mv app/src/test/js/unit/hidden-degree.test.mjs  app/src/test/js/unit/hidden-degree.test.ts
git mv app/src/test/js/unit/call-structure.test.mjs app/src/test/js/unit/call-structure.test.ts
git mv app/src/test/js/unit/opening.test.mjs        app/src/test/js/unit/opening.test.ts
git mv app/src/test/js/unit/tap.test.mjs            app/src/test/js/unit/tap.test.ts
git mv app/src/test/js/unit/screen.test.mjs         app/src/test/js/unit/screen.test.ts
git mv app/src/test/js/unit/invariants.test.mjs     app/src/test/js/unit/invariants.test.ts
git mv app/src/test/js/unit/reachability.test.mjs   app/src/test/js/unit/reachability.test.ts
git mv app/src/test/js/unit/corpus.mjs              app/src/test/js/unit/corpus.ts
```

In each, change the import specifier from `../../../main/resources/viewer/model.mjs` to
`.../model.ts`, and `./corpus.mjs` to `./corpus.ts`. **No assertion changes.** The hand-written
payload literals need one annotation each so type stripping does not complain about the `type` field
being a plain string — add `satisfies Payload` after each literal and import the type:

```ts
import type { Payload } from '../../../main/resources/viewer/types.ts';
const payload = { nodes: [...], edges: [...] } satisfies Payload;
```

- [ ] **Step 12: Write the bundle freshness test**

Create `app/src/test/js/unit/bundle.test.ts`:

```ts
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { buildViewer, BUNDLES } from '../../../../../scripts/build-viewer.mjs';

const VIEWER = 'app/src/main/resources/viewer';

/**
 * The one way this design can be silently wrong.
 *
 * The bundles are generated and committed, so the page can render yesterday's model while every
 * other test passes on today's source - green, and drawing something nobody asked for. Rebuilding
 * into a temp directory and comparing bytes is the only check that reads what the page will actually
 * run.
 */
test('the committed bundles are what the sources build', async () => {
  const out = mkdtempSync(join(tmpdir(), 'codeflow-bundle-'));
  await buildViewer(out);
  for (const { out: name } of BUNDLES) {
    const fresh = readFileSync(join(out, name), 'utf8');
    const committed = readFileSync(join(VIEWER, name), 'utf8');
    assert.equal(fresh, committed, `${name} is stale - run \`npm run build:viewer\``);
  }
});
```

- [ ] **Step 12b: Make the types mean something**

Neither Node nor esbuild checks a type — Node strips them and esbuild deletes them — so without this
step every annotation in the plan is a comment that looks like a check. Confirmed rather than
assumed: `reactflow.tsx` as written in Task 4 had a real type error (`.then((result: never) => …)`,
which nothing can call), and `tsc` found it before the file had ever run.

Create `tsconfig.json`:

```json
{
  "compilerOptions": {
    "strict": true,
    "noEmit": true,
    "skipLibCheck": true,
    "target": "es2020",
    "lib": ["es2020", "dom"],
    "module": "preserve",
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "jsx": "react-jsx",
    "types": ["node"]
  },
  "include": ["app/src/main/resources/viewer/**/*.ts", "app/src/main/resources/viewer/**/*.tsx",
              "app/src/test/js/**/*.ts"]
}
```

`allowImportingTsExtensions`, because every import here names `./model.ts` — which is what Node's
type stripping requires and what esbuild resolves.

Create `app/src/main/resources/viewer/css.d.ts`:

```ts
/** React Flow's stylesheet arrives as text, via esbuild's `--loader:.css=text`. */
declare module '*.css' {
  const text: string;
  export default text;
}
```

Add to `package.json`, and put it in front of the tests, since a type error makes every downstream
failure harder to read:

```json
    "typecheck": "tsc",
    "test": "npm run typecheck && npm run check:bundle && node --test app/src/test/js/unit/*.test.ts"
```

- [ ] **Step 13: Build, and run everything**

Run:
```bash
npm run build:viewer
npm test
./gradlew test --rerun-tasks
npm run test:browser
```
Expected: all PASS. The browser suite is the real check on Steps 7-10 — a mis-substituted payload or
a bundle that does not run makes every browser test fail at load.

- [ ] **Step 14: Confirm nothing moved that should not have**

Run: `git status --short`
Expected: no modified `truth.md` anywhere. The renderer changed; the graph did not.

- [ ] **Step 15: Confirm the freshness guard fires (mutation)**

Edit `model.ts` — add a space inside a function body — and run `npm test` **without** rebuilding.
Expected: FAIL, naming the stale bundle. Then `npm run build:viewer && npm test` passes. Revert the
edit and rebuild.

- [ ] **Step 16: Commit**

```bash
git add -A app/src/main/resources/viewer app/src/test/js scripts package.json package-lock.json \
        app/src/main/kotlin/codeflow/HtmlExporter.kt .gitignore
git commit -m "Build the viewer, and write it in TypeScript

esbuild bundles each renderer into one committed IIFE, so the hand-vendored copies of cytoscape,
elk and cytoscape-elk go away - node_modules is now the single answer to which version this is, and
Gradle still never runs npm. The pure layer needs no build at all: Node 26 strips types, so
model.ts is what node --test reads.

The concatenation trick goes with it. A bundle has real imports, so model.ts's exports stop being
free identifiers that read as a mistake.

No behaviour change. The one new risk is a stale bundle - a page running yesterday's model while
every test passes on today's source - so npm test rebuilds into a temp directory and compares bytes."
```

---

### Task 2: `screen()` states what Cytoscape was deriving

**Files:**
- Modify: `app/src/main/resources/viewer/model.ts` (`screen`)
- Modify: `app/src/main/resources/viewer/cytoscape.ts` (`apply`)
- Modify: `app/src/test/js/unit/screen.test.ts`
- Modify: `app/src/test/js/unit/invariants.test.ts`

**Interfaces:**
- Consumes: `screen(payload, revealed): View` from Task 1.
- Produces: `View.nodes[].visible: boolean` — for a leaf, "is it showing"; for a box, "is some
  descendant leaf showing". The `display` field is **gone**.

- [ ] **Step 1: Write the failing tests**

In `app/src/test/js/unit/screen.test.ts`, replace the test named *no METHOD node carries a display*
and the one named *a revealed leaf is element and an unrevealed one is none* with:

```ts
// The rule Cytoscape applies internally, stated so that a renderer which derives nothing can be
// told it. A box is on screen because something inside it is - transitively, since a box holds
// boxes, and the case that has always been the trap is a box whose only visible node is a
// grandchild.
test('a box is visible exactly when some descendant leaf is showing', () => {
  const view = screen(payload, new Set(['mR', 'x', 'y']));
  assert.equal(nodeNamed(view, 'm').visible, true);
  // f is visible too - closed, but its caller is open, so its stub is offered inside it.
  assert.equal(nodeNamed(view, 'f').visible, true);

  const empty = screen(payload, new Set([]));
  assert.equal(nodeNamed(empty, 'f').visible, false);
});

test('a revealed leaf is visible and an unrevealed one is not', () => {
  const view = screen(payload, new Set(['mR', 'x', 'y']));
  assert.equal(nodeNamed(view, 'x').visible, true);
  assert.equal(nodeNamed(view, 'a').visible, false);
});
```

Add, using a payload where a box's only showing node is a grandchild — append to the same file:

```ts
// main { main, x, f { g { g, b } } }: f holds no leaf of its own, so its visibility can only come
// from b, two levels down. Deriving it from own children instead - the obvious wrong
// implementation - hides f and leaves b nowhere to live.
const nested = {
  nodes: [
    { id: 'm', type: 'METHOD', label: 'main' },
    { id: 'mR', type: 'RETURN', label: 'main', parent: 'm' },
    { id: 'f', type: 'METHOD', label: 'f', parent: 'm' },
    { id: 'g', type: 'METHOD', label: 'g', parent: 'f' },
    { id: 'gR', type: 'RETURN', label: 'g', parent: 'g' },
    { id: 'b', type: 'VARIABLE', label: 'b', parent: 'g' },
  ],
  edges: [],
} satisfies Payload;

test('a box whose only showing node is a grandchild is visible', () => {
  const view = screen(nested, new Set(['b']));
  assert.equal(view.nodes.find((n) => n.id === 'f')!.visible, true);
  assert.equal(view.nodes.find((n) => n.id === 'g')!.visible, true);
});
```

- [ ] **Step 2: Run to verify they fail**

Run: `node --test app/src/test/js/unit/screen.test.ts`
Expected: FAIL — `visible` is `undefined`, `display` is what exists.

- [ ] **Step 3: Implement it**

In `model.ts`, replace the `nodes` mapping inside `screen` with:

```ts
  // A box is on screen because something inside it is. Cytoscape works this out itself and must not
  // be told - see the `apply` in cytoscape.ts - but React Flow derives nothing, so the model is
  // where the rule now lives, and it is one line a unit test can read instead of an `if` in the
  // middle of a render loop.
  const nodes: ViewNode[] = payload.nodes.map((node) => ({
    ...node,
    badge: badgeLabel(node.label, hidden.get(node.id)),
    visible: isBoxNode(node)
      ? [...descendantLeaves(payload.nodes, node.id)].some((leaf) => showing.has(leaf))
      : showing.has(node.id),
  }));
```

Update `screen`'s doc comment: delete the paragraph about `display` being null and put in its place:

```
 * `visible` is stated for a box as well as a leaf, and for a box it means "some descendant leaf is
 * showing" - descendants, because a box holds boxes and one whose only showing node is a grandchild
 * is still on screen. That is exactly what Cytoscape derives internally, which is why the Cytoscape
 * adapter ignores the field; React Flow derives nothing and needs it. Saying it here is what keeps
 * the two pages drawing one picture.
```

- [ ] **Step 4: Run to verify they pass**

Run: `node --test app/src/test/js/unit/screen.test.ts`
Expected: PASS.

- [ ] **Step 5: Keep the Cytoscape adapter ignoring it**

In `cytoscape.ts`'s `apply`, the write becomes:

```ts
      // Never a box. Cytoscape derives a box's visibility from its descendants, transitively, and a
      // display of ours would hide one whose only visible node is a grandchild, leaving that
      // grandchild nowhere to live. `drawn.visible` says the same thing for boxes - this renderer
      // just has no use for it.
      if (drawn.type === 'METHOD') continue;
      node.style('display', drawn.visible ? 'element' : 'none');
```

- [ ] **Step 6: Update the invariants sweep**

In `app/src/test/js/unit/invariants.test.ts`, replace the test *no METHOD node is ever given a
display* with:

```ts
// P3, restated now that there are two renderers. The Cytoscape page derives this and the React Flow
// page is told it; asserting it over the whole corpus is what says the two draw one picture.
test('a box is visible exactly when some descendant leaf is showing', () => {
  for (const { name, payload } of corpus) {
    for (const revealed of states(payload)) {
      const { showing, nodes } = screen(payload, revealed);
      for (const node of nodes) {
        if (node.type !== 'METHOD') continue;
        const inside = [...descendantLeaves(payload.nodes, node.id)].some((leaf) => showing.has(leaf));
        assert.equal(node.visible, inside, `${name}: box ${node.label} visible=${node.visible}, ${inside ? 'has' : 'has no'} showing descendant`);
      }
    }
  }
});
```

- [ ] **Step 7: Run everything**

Run: `npm run build:viewer && npm test && npm run test:browser`
Expected: PASS. The browser test *draws a box whose only revealed nodes are grandchildren* is what
proves the Cytoscape page is unaffected.

- [ ] **Step 8: Confirm the tests catch a wrong implementation (mutation)**

Temporarily derive a box's visibility from `ownLeaves` instead of `descendantLeaves`. Run
`npm test` and confirm both *a box whose only showing node is a grandchild is visible* and the
corpus sweep **fail**. Restore.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/resources/viewer/model.ts app/src/main/resources/viewer/cytoscape.ts \
        app/src/test/js/unit/screen.test.ts app/src/test/js/unit/invariants.test.ts
git commit -m "Say which boxes are on screen, instead of leaving it to Cytoscape

A box is visible when some descendant leaf is showing. Cytoscape works that out itself, which is why
`display: null` was enough while it was the only renderer - but it is a rule no test could read, and
a renderer that derives nothing cannot be handed an absence.

Stated in screen(), it is one assertion per fixture over the whole corpus, and the case it has
always guarded - a box whose only visible node is a grandchild - has a unit test of its own."
```

---

### Task 3: `layout.ts` — the ELK graph as a value

**Files:**
- Create: `app/src/main/resources/viewer/layout.ts`
- Create: `app/src/test/js/unit/layout.test.ts`

**Interfaces:**
- Consumes: `View`, `ViewNode` from `types.ts`.
- Produces: `elkGraph(view: View): ElkNode` and `ELK_OPTIONS`, plus `type ElkNode` and
  `type Positioned = { id: Id; x: number; y: number; width: number; height: number; parent?: Id }`
  and `positions(laidOut: ElkNode): Positioned[]`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/js/unit/layout.test.ts`:

```ts
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { elkGraph, positions } from '../../../main/resources/viewer/layout.ts';
import type { View } from '../../../main/resources/viewer/types.ts';

// main { main, x, f { f, a } }, x -> y hidden, x -> f visible. `a` and the edge to it are off screen.
const view: View = {
  showing: new Set(['mR', 'x', 'fR']),
  stubs: new Set(['fR']),
  hidden: new Map(),
  nodes: [
    { id: 'm', type: 'METHOD', label: 'main', badge: 'main', visible: true },
    { id: 'mR', type: 'RETURN', label: 'main', parent: 'm', badge: 'main', visible: true },
    { id: 'x', type: 'VARIABLE', label: 'x', parent: 'm', badge: 'x', visible: true },
    { id: 'f', type: 'METHOD', label: 'f', parent: 'm', badge: 'f', visible: true },
    { id: 'fR', type: 'RETURN', label: 'f', parent: 'f', badge: 'f', visible: true },
    { id: 'a', type: 'VARIABLE', label: 'a', parent: 'f', badge: 'a', visible: false },
  ],
  edges: [
    { source: 'x', target: 'fR', kind: 'FLOW', visible: true },
    { source: 'x', target: 'a', kind: 'FLOW', visible: false },
  ],
};

const childIds = (node: any) => (node.children ?? []).map((c: any) => c.id).sort();

// Containment is what a box means, and ELK is told it the same way React Flow is: a child sits
// inside its parent's `children`, and its coordinates come back relative to it.
test('the graph nests a box inside its parent', () => {
  const root = elkGraph(view);
  assert.deepEqual(childIds(root), ['m']);
  const main = root.children![0];
  assert.deepEqual(childIds(main), ['f', 'mR', 'x']);
  assert.deepEqual(childIds(main.children!.find((c: any) => c.id === 'f')), ['fR']);
});

// A hidden node laid out is a hole in the diagram: ELK reserves space for it and the boxes come out
// too big, with nothing on the page explaining the gap.
test('nothing hidden reaches the layout', () => {
  const root = elkGraph(view);
  const ids = JSON.stringify(root);
  assert.equal(ids.includes('"a"'), false);
  assert.equal(root.edges?.length, 1);
});

// Every edge at the root, whatever it connects. ELK requires an edge to sit in a graph enclosing
// both endpoints, and only the root always qualifies - the same rule GraphmlExporter follows.
test('edges are declared at the root', () => {
  const root = elkGraph(view);
  assert.deepEqual(root.edges, [{ id: 'x->fR', sources: ['x'], targets: ['fR'] }]);
});

// A leaf needs a size before anything is rendered; a box must not have one, or ELK honours it
// instead of sizing the box to fit its children.
test('a leaf is sized from its caption and a box is not sized at all', () => {
  const root = elkGraph(view);
  const main = root.children![0];
  const x = main.children!.find((c: any) => c.id === 'x')!;
  const f = main.children!.find((c: any) => c.id === 'f')!;
  assert.ok(x.width! > 0 && x.height! > 0);
  assert.equal(f.width, undefined);
  assert.equal(f.height, undefined);
});

// Longer captions get wider boxes. Without this every node is one width and a badge overflows it.
test('a longer badge is wider', () => {
  const wide: View = { ...view, nodes: view.nodes.map((n) => (n.id === 'x' ? { ...n, badge: 'x ↑2 ↓3' } : n)) };
  const at = (v: View, id: string): any => elkGraph(v).children![0].children!.find((c: any) => c.id === id);
  assert.ok(at(wide, 'x').width > at(view, 'x').width);
});

test('positions flattens the laid-out tree, keeping each parent', () => {
  const laid = {
    id: 'root',
    children: [{
      id: 'm', x: 0, y: 0, width: 200, height: 100,
      children: [{ id: 'x', x: 10, y: 20, width: 90, height: 30 }],
    }],
  };
  assert.deepEqual(positions(laid as any), [
    { id: 'm', x: 0, y: 0, width: 200, height: 100, parent: undefined },
    { id: 'x', x: 10, y: 20, width: 90, height: 30, parent: 'm' },
  ]);
});
```

- [ ] **Step 2: Run to verify it fails**

Run: `node --test app/src/test/js/unit/layout.test.ts`
Expected: FAIL — cannot resolve `layout.ts`.

- [ ] **Step 3: Implement `layout.ts`**

Create `app/src/main/resources/viewer/layout.ts`:

```ts
/**
 * Where each node goes, as an ELK problem rather than as pixels.
 *
 * Cytoscape runs ELK through cytoscape-elk and never says what came back. React Flow positions
 * nothing at all, so the layout has to be driven directly - which is the good half of the bargain:
 * the *input* becomes a value, and a value can be tested without a browser. Only running ELK and
 * writing the answer into React Flow state needs one.
 *
 * No imports beyond types: `node --test` reads this file directly.
 */
import type { Id, View } from './types.ts';

export type ElkNode = {
  id: string;
  width?: number;
  height?: number;
  x?: number;
  y?: number;
  layoutOptions?: Record<string, string>;
  children?: ElkNode[];
  edges?: { id: string; sources: string[]; targets: string[] }[];
};

/**
 * The options the Cytoscape page already uses, as strings.
 *
 * cytoscape-elk takes numbers and converts; the ELK API does not, and a number here is ignored
 * silently - the layout still runs, just without the spacing.
 */
export const ELK_OPTIONS: Record<string, string> = {
  algorithm: 'layered',
  'elk.direction': 'DOWN',
  'elk.layered.spacing.nodeNodeBetweenLayers': '40',
  'elk.spacing.nodeNode': '25',
  // Without this ELK lays each container out independently and the boxes overlap.
  'elk.hierarchyHandling': 'INCLUDE_CHILDREN',
};

/** Rough, and deliberately so - see the design note. Wrong by a few pixels is loose spacing. */
const CHAR_WIDTH = 6.5;
const PADDING = 16;
export const NODE_HEIGHT = 26;

export const nodeWidth = (badge: string) => Math.round(badge.length * CHAR_WIDTH) + PADDING;

/**
 * The visible subgraph, nested, ready for ELK.
 *
 * Hidden nodes are left out rather than sized zero: ELK reserves space for anything it is given, so
 * a hidden node laid out is a hole in the diagram with nothing on the page explaining the gap.
 *
 * A box gets no width or height. ELK sizes a parent to fit its children, and a size of ours would be
 * honoured instead - which is how a box ends up smaller than what it contains.
 *
 * Every edge is declared at the root, whatever it connects: ELK requires an edge to sit in a graph
 * enclosing both endpoints, and only the root always qualifies. GraphmlExporter has the same rule
 * for the same reason.
 */
export function elkGraph(view: View): ElkNode {
  const visible = view.nodes.filter((node) => node.visible);
  const byParent = new Map<Id | undefined, ElkNode[]>();

  for (const node of visible) {
    const elk: ElkNode = node.type === 'METHOD'
      ? { id: node.id, layoutOptions: ELK_OPTIONS, children: [] }
      : { id: node.id, width: nodeWidth(node.badge), height: NODE_HEIGHT };
    if (!byParent.has(node.parent)) byParent.set(node.parent, []);
    byParent.get(node.parent)!.push(elk);
  }

  const attach = (elk: ElkNode) => {
    if (!elk.children) return;
    elk.children = byParent.get(elk.id) ?? [];
    for (const child of elk.children) attach(child);
  };

  const roots = byParent.get(undefined) ?? [];
  for (const root of roots) attach(root);

  return {
    id: 'root',
    layoutOptions: ELK_OPTIONS,
    children: roots,
    edges: view.edges
      .filter((edge) => edge.visible)
      .map((edge) => ({ id: `${edge.source}->${edge.target}`, sources: [edge.source], targets: [edge.target] })),
  };
}

export type Positioned = { id: Id; x: number; y: number; width: number; height: number; parent?: Id };

/**
 * The laid-out tree, flattened, each node remembering its parent.
 *
 * ELK returns a child's coordinates relative to its parent, which is already React Flow's
 * `parentId` convention - so nothing is re-based here, and re-basing it would be the bug.
 */
export function positions(laidOut: ElkNode, parent?: Id, into: Positioned[] = []): Positioned[] {
  for (const child of laidOut.children ?? []) {
    into.push({ id: child.id, x: child.x ?? 0, y: child.y ?? 0, width: child.width ?? 0, height: child.height ?? 0, parent });
    positions(child, child.id, into);
  }
  return into;
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `node --test app/src/test/js/unit/layout.test.ts`
Expected: PASS (6 tests).

- [ ] **Step 5: Confirm the tests catch a wrong implementation (mutation)**

Make `elkGraph` include every node rather than only the visible ones. Run
`node --test app/src/test/js/unit/layout.test.ts` and confirm *nothing hidden reaches the layout*
**fails**. Then give a box `width`/`height` and confirm *a leaf is sized from its caption and a box
is not sized at all* **fails**. Restore both.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/resources/viewer/layout.ts app/src/test/js/unit/layout.test.ts
git commit -m "Make the layout's input a value

React Flow positions nothing, so ELK has to be driven directly - and the good half of that bargain
is that what ELK is asked becomes testable without a browser: the hierarchy, that nothing hidden
reserves space, that a box is sized by its children rather than by us."
```

---

### Task 4: The React Flow renderer, and `--html`

**Files:**
- Create: `app/src/main/resources/viewer/reactflow.tsx`
- Create: `app/src/main/resources/viewer/reactflow.entry.tsx`
- Modify: `scripts/build-viewer.mjs` (uncomment the second bundle)
- Modify: `app/src/main/kotlin/codeflow/App.kt`
- Modify: `app/src/test/kotlin/codeflow/AppTest.kt` (`writePage`)
- Modify: `.gitignore`

**Interfaces:**
- Consumes: `opening`, `tap`, `screen` from `model.ts`; `elkGraph`, `positions`, `ELK_OPTIONS` from
  `layout.ts`; `PALETTE`, `EDGE_COLOURS`, `EDGE_DEFAULT` from `theme.ts`.
- Produces: `init(payload: Payload): void` from `reactflow.tsx`, and
  `app/src/main/resources/viewer/reactflow.bundle.js`.

- [ ] **Step 1: Write the renderer**

Create `app/src/main/resources/viewer/reactflow.tsx`:

```tsx
/**
 * The React Flow renderer, and what --html emits.
 *
 * It derives nothing. Cytoscape drops an edge when either endpoint hides and hides a box when its
 * last descendant does; here every one of those is a field on the view, which is why screen() states
 * them. Anything this page draws differently from the Cytoscape one is a fact about model.ts.
 *
 * Nothing here decides what is on screen. That is model.ts, where the tests are.
 */
import { useCallback, useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { Background, MarkerType, ReactFlow, type Edge, type Node, type NodeProps } from '@xyflow/react';
import { Handle, Position } from '@xyflow/react';
import css from '@xyflow/react/dist/style.css';
import ELK from 'elkjs/lib/elk.bundled.js';
import { opening, screen, tap } from './model.ts';
import { elkGraph, positions, NODE_HEIGHT, type Positioned } from './layout.ts';
import { PALETTE, EDGE_COLOURS, EDGE_DEFAULT } from './theme.ts';
import type { Id, Payload, View, ViewNode } from './types.ts';

/** The stylesheet arrives as text so the page stays one file. */
function installStyles() {
  const style = document.createElement('style');
  style.textContent = `${css}
    .cf-node { border: 1px solid #999; border-radius: 4px; font: 11px system-ui, sans-serif;
               display: flex; align-items: center; justify-content: center; box-sizing: border-box;
               width: 100%; height: 100%; }
    .cf-box { border: 1px solid #999; border-radius: 4px; background: ${PALETTE.METHOD};
              box-sizing: border-box; width: 100%; height: 100%; }
    .cf-box > .cf-title { font: bold 11px system-ui, sans-serif; padding: 2px 6px; }
    .react-flow__handle { opacity: 0; }`;
  document.head.appendChild(style);
}

/**
 * A leaf. The caption is `badge`, never `label`: the hidden-neighbour counts are a property of the
 * current view rather than of the value, and every test that looks a node up by name needs the name
 * to go on being there.
 *
 * The handles are invisible and exist because React Flow will not draw an edge without them.
 */
function LeafNode({ data }: NodeProps) {
  const node = data as unknown as ViewNode;
  return (
    <div className="cf-node" style={{ background: PALETTE[node.type] ?? '#ddd' }}>
      <Handle type="target" position={Position.Top} />
      {node.badge}
      <Handle type="source" position={Position.Bottom} />
    </div>
  );
}

/** A method. Captioned at the top, so an open box reads as a container rather than as a value. */
function BoxNode({ data }: NodeProps) {
  const node = data as unknown as ViewNode;
  return <div className="cf-box"><div className="cf-title">{node.label}</div></div>;
}

const NODE_TYPES = { leaf: LeafNode, box: BoxNode };

function edgesOf(view: View): Edge[] {
  return view.edges.filter((edge) => edge.visible).map((edge) => {
    const colour = EDGE_COLOURS[edge.kind] ?? EDGE_DEFAULT;
    return {
      id: `${edge.source}->${edge.target}`,
      source: edge.source,
      target: edge.target,
      // CONDITION reads `if` rather than `condition`, and is dashed, exactly as the Mermaid document
      // and the Cytoscape page draw it. FLOW is unlabelled: it is nearly every edge.
      label: edge.kind === 'CONDITION' ? 'if' : (edge.kind === 'FLOW' ? undefined : edge.kind.toLowerCase()),
      style: { stroke: colour, strokeWidth: 1.5, ...(edge.kind === 'CONDITION' ? { strokeDasharray: '4 3' } : {}) },
      markerEnd: { type: MarkerType.ArrowClosed, color: colour },
    };
  });
}

/**
 * Boxes first, then leaves.
 *
 * React Flow requires a parent to appear before its children in the array and silently drops a child
 * that arrives first. Sorting by depth is what guarantees it for a box nested inside a box.
 */
function nodesOf(view: View, laid: Positioned[]): Node[] {
  const at = new Map(laid.map((p) => [p.id, p]));
  const byId = new Map(view.nodes.map((n) => [n.id, n]));
  const depth = (id: Id): number => {
    const parent = byId.get(id)?.parent;
    return parent ? depth(parent) + 1 : 0;
  };

  return view.nodes
    .filter((node) => node.visible && at.has(node.id))
    .sort((a, b) => depth(a.id) - depth(b.id))
    .map((node) => {
      const p = at.get(node.id)!;
      return {
        id: node.id,
        type: node.type === 'METHOD' ? 'box' : 'leaf',
        position: { x: p.x, y: p.y },
        data: node as unknown as Record<string, unknown>,
        ...(node.parent ? { parentId: node.parent, extent: 'parent' as const } : {}),
        style: { width: p.width, height: p.height || NODE_HEIGHT },
        draggable: false,
      };
    });
}

function Graph({ payload }: { payload: Payload }) {
  const [revealed, setRevealed] = useState<Set<Id>>(() => opening(payload));
  const [laid, setLaid] = useState<Positioned[]>([]);
  const view = useMemo(() => screen(payload, revealed), [payload, revealed]);

  useEffect(() => {
    let current = true;
    // No cast either way: layout.ts's ElkNode is assignable to the library's, and what comes back is
    // assignable to positions(). Keeping the two types structurally compatible is what lets layout.ts
    // stay importable by `node --test`, which cannot load elkjs.
    new ELK().layout(elkGraph(view)).then((result) => {
      if (current) setLaid(positions(result));
    });
    return () => { current = false; };
  }, [view]);

  // Folding needs a box, so a sprawl inside the entry method has nothing to fold. Without this the
  // only way back is a reload, which re-runs the whole layout.
  useEffect(() => {
    const reset = (event: KeyboardEvent) => {
      if (event.key === 'r' || event.key === 'R') setRevealed(opening(payload));
    };
    document.addEventListener('keydown', reset);
    return () => document.removeEventListener('keydown', reset);
  }, [payload]);

  const onNodeClick = useCallback(
    (_: unknown, node: Node) => setRevealed((was) => tap(payload, was, node.id)),
    [payload],
  );

  const nodes = useMemo(() => nodesOf(view, laid), [view, laid]);
  const edges = useMemo(() => edgesOf(view), [view]);

  // The browser tests read the graph off the DOM, not off this. It is here for the same reason
  // window.cy is: something to inspect when a page looks wrong.
  (window as unknown as { view: View }).view = view;

  return (
    <ReactFlow nodes={nodes} edges={edges} nodeTypes={NODE_TYPES} onNodeClick={onNodeClick}
               fitView minZoom={0.05} proOptions={{ hideAttribution: true }}>
      <Background />
    </ReactFlow>
  );
}

export function init(payload: Payload) {
  installStyles();
  createRoot(document.getElementById('graph')!).render(<Graph payload={payload} />);
}
```

Create `app/src/main/resources/viewer/reactflow.entry.tsx`:

```tsx
import { init } from './reactflow.tsx';
import type { Payload } from './types.ts';

declare global { interface Window { __PAYLOAD__: Payload } }
init(window.__PAYLOAD__);
```

- [ ] **Step 2: Turn the second bundle on and build**

In `scripts/build-viewer.mjs`, uncomment the `reactflow.entry.tsx` line in `BUNDLES`.

Run: `npm run build:viewer`
Expected: two bundles written, no errors. Note the reported size of `reactflow.bundle.js`.

- [ ] **Step 3: See it, before wiring anything to it**

Run:
```bash
./gradlew -q run --args="$(pwd)/app/src/test/resources/funcCall --html" > /tmp/rf.html
open /tmp/rf.html
```

`--html` still emits Cytoscape at this point, so temporarily pass `HtmlExporter("reactflow.bundle.js")`
in `App.kt` for this step. Expected: boxes with nested nodes, arrows between them, and clicking a node
revealing more. **If nested boxes do not draw, stop and report it** — that is the risk the spec names,
and it is a finding rather than something to work around.

- [ ] **Step 4: Make `--html` React Flow**

`HtmlExporter`'s default parameter is already `reactflow.bundle.js` from Task 1, so `App.kt` becomes:

```kotlin
    if (args.flags.contains("--html")) {
        HtmlExporter().processMainMethod(mainMethod) { result.add(it) }
    } else if (args.flags.contains("--html-cytoscape")) {
        // Kept because it is the second opinion: Cytoscape derives an edge's visibility from its
        // endpoints and a box's from its descendants, so a page it draws differently from the React
        // Flow one is a fact about the view model that no single renderer could have told us.
        HtmlExporter("cytoscape.bundle.js").processMainMethod(mainMethod) { result.add(it) }
    } else if (args.flags.contains("--json")) {
```

- [ ] **Step 5: Write both pages beside each fixture**

In `AppTest.kt`'s `writePage`, add after the existing `graph.html` write:

```kotlin
        // The same fixture through the other renderer. Cytoscape derives what React Flow is told, so
        // two pages from one payload is the cheapest way to see which of them is wrong.
        val cytoscape = StringBuilder()
        HtmlExporter("cytoscape.bundle.js").processMainMethod(mainMethod) { cytoscape.append(it).append("\n") }
        Files.writeString(testDirPath.resolve("graph-cytoscape.html"), cytoscape)
```

and extend that method's KDoc with one sentence naming `graph-cytoscape.html`.

Add to `.gitignore`, directly under the `graph.html` entry:

```
# The same fixture through the other renderer, written on every test run beside graph.html. Two
# pages from one payload is how a disagreement between the renderers becomes visible.
app/src/test/resources/*/graph-cytoscape.html
```

- [ ] **Step 6: Run everything**

Run:
```bash
npm run build:viewer && npm test
./gradlew test --rerun-tasks
npm run test:browser
```
Expected: PASS. The browser suite still only exercises the Cytoscape page at this point — Task 5 is
what points it at both.

- [ ] **Step 7: Confirm nothing moved that should not have**

Run: `git status --short`
Expected: no modified `truth.md`, and no `graph-cytoscape.html` showing as untracked.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/resources/viewer scripts/build-viewer.mjs \
        app/src/main/kotlin/codeflow/App.kt app/src/test/kotlin/codeflow/AppTest.kt .gitignore
git commit -m "Draw the graph with React Flow, and make that the page

Same payload, same model, a renderer that derives none of what Cytoscape derives - which is the
point of having it. Every visibility Cytoscape works out internally is a field on the view now, so
the two pages agreeing is a claim about model.ts rather than a coincidence.

--html emits it; --html-cytoscape keeps the old page, and AppTest writes both beside every fixture."
```

---

### Task 5: One browser suite, two renderers

**Files:**
- Create: `app/src/test/js/browser/probes.ts`
- Modify: `app/src/test/js/global-setup.mjs`
- Rename + modify: `app/src/test/js/browser/viewer.spec.mjs` → `viewer.spec.ts`,
  `call-structure.spec.mjs` → `call-structure.spec.ts`
- Modify: `app/src/test/js/playwright.config.mjs`

**Interfaces:**
- Consumes: pages built by `global-setup.mjs`.
- Produces: `RENDERERS: Probe[]` from `probes.ts`, where
  `Probe = { name: string; page(fixture: string): string; ready(page): Promise<void>;
  leafLabels(page): Promise<string[]>; boxLabels(page): Promise<string[]>;
  tapLeaf(page, label): Promise<void>; tapBox(page, label): Promise<void> }`.

- [ ] **Step 1: Build both pages in `global-setup.mjs`**

Replace the loop body so each fixture is exported twice:

```js
  for (const name of FIXTURES) {
    for (const [suffix, flag] of [['', '--html'], ['-cytoscape', '--html-cytoscape']]) {
      const html = execSync(
        `./gradlew -q run --args="${resolve(`app/src/test/resources/${name}`)} ${flag}"`,
        { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024, stdio: ['ignore', 'pipe', 'ignore'] },
      );
      writeFileSync(`build/viewer-test/${name}${suffix}.html`, html);
    }
  }
```

- [ ] **Step 2: Write the probes**

Create `app/src/test/js/browser/probes.ts`:

```ts
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';
import type { Page } from '@playwright/test';

/**
 * The same five questions, asked of each renderer in the way that renderer can answer them.
 *
 * Both read what is *drawn*. The React Flow probe goes through the DOM rather than through the view
 * object the page publishes, because a test reading our own screen() output passes on a page that
 * draws nothing - the trap this repo already names for reading `data('badge')` instead of
 * `style('label')`.
 */
export type Probe = {
  name: string;
  page(fixture: string): string;
  ready(page: Page): Promise<void>;
  leafLabels(page: Page): Promise<string[]>;
  boxLabels(page: Page): Promise<string[]>;
  tapLeaf(page: Page, label: string): Promise<void>;
  tapBox(page: Page, label: string): Promise<void>;
};

const at = (name: string) => pathToFileURL(resolve(`build/viewer-test/${name}.html`)).href;

/**
 * A caption carries the badge - `x ↓3` - and every assertion in the suite names the plain label, so
 * the counts are stripped here rather than in each test. Splitting on the arrow is enough: no label
 * contains one.
 */
const plain = (caption: string) => caption.split(' ↑')[0].split(' ↓')[0].trim();

export const reactFlow: Probe = {
  name: 'react flow',
  page: (fixture) => at(fixture),
  ready: async (page) => { await page.waitForSelector('.react-flow__node'); },
  leafLabels: (page) => page.evaluate(() =>
    [...document.querySelectorAll('.react-flow__node .cf-node')].map((n) => n.textContent ?? '')),
  boxLabels: (page) => page.evaluate(() =>
    [...document.querySelectorAll('.react-flow__node .cf-title')].map((n) => n.textContent ?? '')),
  tapLeaf: async (page, label) => {
    await page.locator('.react-flow__node .cf-node', { hasText: new RegExp(`^${label}( [↑↓].*)?$`) }).first().click();
  },
  tapBox: async (page, label) => {
    await page.locator('.react-flow__node .cf-title', { hasText: new RegExp(`^${label}$`) }).first().click();
  },
};

export const cytoscape: Probe = {
  name: 'cytoscape',
  page: (fixture) => at(`${fixture}-cytoscape`),
  ready: async (page) => { await page.waitForFunction(() => typeof (window as any).cy?.nodes === 'function'); },
  leafLabels: (page) => page.evaluate(() => (window as any).cy.nodes()
    .filter((n: any) => n.data('type') !== 'METHOD' && n.visible()).map((n: any) => n.style('label'))),
  boxLabels: (page) => page.evaluate(() => (window as any).cy.nodes('[type = "METHOD"]')
    .filter((n: any) => n.visible()).map((n: any) => n.style('label'))),
  tapLeaf: (page, label) => page.evaluate((l) => (window as any).cy.nodes()
    .filter((n: any) => n.data('type') !== 'METHOD' && n.data('label') === l).emit('tap'), label),
  tapBox: (page, label) => page.evaluate((l) => (window as any).cy.nodes('[type = "METHOD"]')
    .filter((n: any) => n.data('label') === l).emit('tap'), label),
};

export const RENDERERS = [reactFlow, cytoscape];

/** Labels as the assertions want them: plain names, sorted, badges stripped. */
export const labels = async (from: Promise<string[]>) => (await from).map(plain).sort();
```

- [ ] **Step 3: Point `call-structure.spec.ts` at both**

`git mv app/src/test/js/browser/call-structure.spec.mjs app/src/test/js/browser/call-structure.spec.ts`.
Replace its helpers and wrap both `describe` blocks in a loop:

```ts
import { test, expect } from '@playwright/test';
import { RENDERERS, labels, type Probe } from './probes.ts';

const open = (probe: Probe, name: string) => async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await page.goto(probe.page(name));
  await probe.ready(page);
  expect(errors, 'the page threw while loading').toEqual([]);
};

for (const probe of RENDERERS) {
  const leafLabels = (page) => labels(probe.leafLabels(page));
  const boxLabels = (page) => labels(probe.boxLabels(page));
  const tapBox = (page, label: string) => probe.tapBox(page, label);

  test.describe(`${probe.name} - member: a callee no edge reaches`, () => {
    test.beforeEach(open(probe, 'member'));
    // ...every existing test in this block, unchanged...
  });

  test.describe(`${probe.name} - funcCall: one level per click`, () => {
    test.beforeEach(open(probe, 'funcCall'));
    // ...unchanged...
  });
}
```

The assertions themselves do not change. Add the stub-click test from the prior plan's Task 7 to the
`member` block if it is not already there, using `probe.tapLeaf`.

- [ ] **Step 4: Do the same for `viewer.spec.ts`**

`git mv app/src/test/js/browser/viewer.spec.mjs app/src/test/js/browser/viewer.spec.ts` and apply the
same wrapping. Two of its assertions are Cytoscape-specific and must stay that way — the one reading
`cy.nodes().length` against the payload count, and the one reading `n.style('label')` to prove the
badge reaches the stylesheet. Move both into a `test.describe('cytoscape only', …)` block outside the
loop, with this comment:

```ts
// Cytoscape-specific on purpose. `nothing is ever removed from the graph` is a claim about the
// Cytoscape adapter, which hides nodes rather than dropping them; React Flow is handed only the
// visible ones, so the same assertion there would be asserting the opposite design.
```

- [ ] **Step 5: Let Playwright load TypeScript**

Playwright compiles `.ts` specs itself, but `testDir` needs the new extension picked up. In
`playwright.config.mjs`, add `testMatch: '**/*.spec.ts'`.

- [ ] **Step 6: Run the browser suite**

Run: `npm run test:browser`
Expected: PASS, with every test now reported twice — once per renderer. Confirm the count doubled:
a suite that silently ran one renderer would look identical otherwise.

- [ ] **Step 7: Confirm the React Flow half is load-bearing (mutation)**

In `reactflow.tsx`, make `nodesOf` drop the `visible` filter so every node renders. Run
`npm run test:browser` and confirm the React Flow half of *a callee of a closed callee is not drawn*
and *opening a box shows its own leaves and only the names of the boxes inside it* **fail** while the
Cytoscape half passes. Restore.

- [ ] **Step 8: Commit**

```bash
git add app/src/test/js
git commit -m "Ask both renderers the same questions

One spec, two probes, every fixture exported twice. The React Flow probe reads captions out of the
DOM rather than out of the view object the page publishes - reading our own screen() output would
pass on a page that draws nothing, which is the trap already named for data('badge').

Two assertions stay Cytoscape-only and say why: nothing is ever removed from *that* graph, which is
a claim about the adapter and not about the model."
```

---

### Task 6: Write down where things live now

**Files:**
- Modify: `CLAUDE.md` — "The interactive viewer" and "The viewer's tests"

**Interfaces:** Consumes everything above. Produces no code.

- [ ] **Step 1: Replace the opening paragraph of "The interactive viewer"**

The paragraph beginning "`HtmlExporter` substitutes the vendored libraries" is now wrong in every
detail. Replace it with:

```markdown
`HtmlExporter` substitutes one bundle and the payload into `template.html`. It is substitution only
— all of the behaviour lives in the viewer sources, which is where changes go. The bundles are built
by `npm run build:viewer` (esbuild, one IIFE per renderer) and **committed**, so `./gradlew build`
never needs npm; `npm test` rebuilds them into a temp directory and compares bytes, because a stale
bundle is a page rendering yesterday's model while every test passes on today's source.

There are two renderers over one view model:

| File | |
|---|---|
| `model.ts` | every decision about what is on screen — `opening`, `tap`, `screen`. Pure. |
| `layout.ts` | the visible subgraph as an ELK problem. Pure. |
| `theme.ts` | the palette and the edge colours, so one graph does not change colour between pages |
| `reactflow.tsx` | the renderer `--html` emits |
| `cytoscape.ts` | the renderer `--html-cytoscape` emits |

`model.ts` and `layout.ts` have no DOM and no library, so `node --test` imports them directly — Node
26 strips types, so the tested layer needs no build at all.

**The second renderer is the point, not a preference.** Cytoscape derives three things the payload
never states: an edge disappears when either endpoint does, a box disappears when its last visible
descendant does — transitively — and a box is however big its children need. While it was the only
renderer, none of that had to be written down, and "what is on screen" was a question only a
Playwright click could ask. React Flow derives none of it, so each one is now a field on what
`screen()` returns, and the two pages agreeing is a claim `npm test` checks rather than a
coincidence.
```

- [ ] **Step 2: Replace the "never set `display`" paragraph**

The rule is still true and no longer lives where that paragraph says. Replace it with:

```markdown
Visibility is derived, and `screen()` is where: a `Set` of revealed *leaf* ids is the only state, and
everything else follows from it. **A box is visible exactly when some descendant leaf is showing** —
descendants, because a box holds boxes and one whose only showing node is a grandchild is still on
screen. The Cytoscape adapter ignores that field and must go on ignoring it: Cytoscape works a box
out from its children itself, and a `display` of ours would hide the grandchild's box and leave the
grandchild nowhere to live. React Flow is told. The browser test *draws a box whose only revealed
nodes are grandchildren* is the end of that argument, and the corpus sweep is the rest of it.

Nothing is ever removed from the Cytoscape graph, so `cy.nodes().length` is always the payload's node
count. React Flow is handed only what is visible, which is why that assertion is Cytoscape-only in
the browser suite.
```

- [ ] **Step 3: Add layout to that section**

After the paragraph above, add:

```markdown
Layout is ELK `layered` with `elk.hierarchyHandling: INCLUDE_CHILDREN` — without that it lays each
container out independently and the boxes overlap. Cytoscape reaches it through `cytoscape-elk` and
never says what came back; React Flow positions nothing at all, so `layout.ts` drives ELK directly,
which is what makes the *input* a value a test can read: that the hierarchy nests, that nothing
hidden reserves space, that a leaf is sized from its caption and a box is not sized at all (ELK sizes
a parent to fit its children, and a size of ours is honoured instead). ELK returns a child's
coordinates relative to its parent, which is already React Flow's `parentId` convention, so nothing
is re-based — re-basing it would be the bug.
```

- [ ] **Step 4: Extend "The viewer's tests"**

Replace the two bullets with:

```markdown
- `app/src/test/js/unit/` (`npm test`) — the pure functions, imported straight from `model.ts` and
  `layout.ts`. `neighbourhood` is tested here: the depth bound, that a node reachable both ways is
  recorded at the *short* distance so nodes past it stay in range, and that it terminates on a cycle.
  The corpus sweeps live here too, over the `graph.json` the golden suite writes beside each fixture.
  `bundle.test.ts` is the freshness check on the committed bundles and runs first.
- `app/src/test/js/browser/` (`npm run test:browser`) — Playwright against pages built from real
  fixtures, **once per renderer**. `probes.ts` gives each one the same five operations and the specs
  loop over both, so a behaviour is asserted of the model rather than of a rendering. The React Flow
  probe reads captions out of the DOM and never out of the view object the page publishes: a test
  reading our own `screen()` output passes on a page that draws nothing, which is the same trap as
  reading `data('badge')` instead of `style('label')`.
```

- [ ] **Step 5: Update the exporter table and the commands**

In the exporter table, `--html` now reads "one self-contained interactive page (React Flow)", and a
row is added for `--html-cytoscape`, "the same page drawn by Cytoscape — the second opinion".

In the Commands section, add under the existing npm lines:

```shell
npm run build:viewer                 # rebuild the committed renderer bundles (esbuild)
```

and note that `npm test` fails if they are stale.

- [ ] **Step 6: Commit**

```bash
git add CLAUDE.md
git commit -m "Write down that there are two renderers and why"
```

---

## Self-Review

**Spec coverage.** Every section of `docs/superpowers/specs/2026-08-09-react-flow-renderer-design.md`
maps to a task: `screen()` stating box visibility → Task 2; `elkGraph` → Task 3; TypeScript behind
esbuild, the deleted vendored libraries, the committed bundles and the freshness guard → Task 1
(guard in Steps 12/15); `--html` becoming React Flow and `--html-cytoscape` keeping the old page →
Task 4; the parity list → Task 4 Step 1, checked by Task 5; the probes and the DOM-not-view-object
rule → Task 5; the risk spike → already done (`build/spike/`, verdict PASS: nested nodes drawn, ELK
positioning them, one edge crossing out, no page errors from `file://`), and Task 4 Step 3 is the
second look at it with real data.

**Type consistency.** `View`/`ViewNode`/`ViewEdge`/`Payload`/`Id` are defined once in Task 1's
`types.ts` and imported by `model.ts` (Task 1), `layout.ts` (Task 3) and `reactflow.tsx` (Task 4).
`screen(payload, revealed): View` keeps one signature across Tasks 1, 2 and 4. `elkGraph(view):
ElkNode` and `positions(laidOut): Positioned[]` are defined in Task 3 and consumed in Task 4 under
those names. `Probe`'s five operations are named identically in Task 5's type, its two
implementations and the specs. `HtmlExporter(bundle)` takes the same parameter in Tasks 1, 4 and 5's
`global-setup`.

**Ordering.** Task 2 needs Task 1's `model.ts`. Task 4 needs Tasks 2 and 3. Task 5 needs Task 4's two
pages. Only Tasks 2 and 4 change behaviour, and each ends with `git status` checked for a moved
`truth.md`.

**Known risk.** Task 1 is large — a rename, a bundler, and a Kotlin change in one commit. It is one
task because none of its parts is independently testable: the page does not load until the bundle,
the template and `HtmlExporter` all agree. If it has to be split, the seam is Step 11 (the test
renames), which can land separately.
