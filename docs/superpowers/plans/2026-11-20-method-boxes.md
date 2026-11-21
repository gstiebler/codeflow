# Method boxes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The condensed view's groups become one flat, empty box per call site (the METHOD box a leaf sits directly in), replacing `program` + per-method boundary groups.

**Architecture:** Only the grouping key in `condensed.ts`'s `index()` changes; `condensed`, `tapCondensed`, `focusOf`, `hoverText` already work on any group id. Renderer, Mermaid and layout already draw `GROUP` nodes flat.

**Tech Stack:** TypeScript viewer (React Flow + ELK, esbuild bundle), node:test, Playwright, Kotlin/Gradle.

**Spec:** `docs/superpowers/specs/2026-11-20-method-boxes-design.md`

## Global Constraints

- Work in `/Users/guistiebler/Documents/Projetos/codeflow/.claude/worktrees/no-boxes` on branch `no-boxes`. Don't push.
- One group per call site; group id = the METHOD box's payload id. Flat: never a box inside a box.
- Group order = payload order of the boxes. Caption unchanged: `label (members)`.
- A leaf with no `parent` box throws (`leaf <id> has no method box`). No `program` bucket.
- Boundary boxes keep `boundary: true` on their group.
- Legend heading "Boxes" → "Method boxes".
- Gradle: `JAVA_HOME=~/.sdkman/candidates/java/21.0.11-tem ./gradlew test --rerun-tasks`. `npm test` needs Gradle run first (writes graph.json).
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

- One method called twice from one caller → two boxes, same caption; hover must tell them apart by call site.
- A method box holding only a nested call's box (no leaves of its own) → not drawn; its callee box still drawn, no dangling edge.
- Revealing a box's last hidden leaf → the box disappears and its edges re-attach to the leaves.
- Two entry points (`twoEntryPoints` fixture) → each root's blocks are separate boxes; the SHARED edge runs box→box.
- A payload leaf without `parent` → clear error, not a silent group.

---

### Task 1: Group by call-site box

**Files:**
- Modify: `app/src/main/resources/viewer/condensed.ts` (`index()`, `PROGRAM`, `groupOf`, `condensed`, `hoverText`, header comment)
- Modify: `app/src/main/resources/viewer/types.ts` (comments on `CondensedNode.boundary`), `layout.ts:68` comment
- Test: `app/src/test/js/unit/condensed.test.ts`, `focus.test.ts`, `condensed-sweep.test.ts`, `mermaid.test.ts`, `layout.test.ts`

**Interfaces:**
- Produces: `groupOf(payload): Map<Id, Id>` maps leaf → its parent METHOD box id. `PROGRAM` export removed. `CondensedNode` for a group: `{ id: boxId, label: box.label, type: 'GROUP', members, boundary?: true }`.
- `hoverText` for a group: first line `${label}: ${n} nodes`, then `called at <File.java:N>` from the box's own `source` when it names a line, then the existing per-method ranges.

- [ ] **Step 1: Rewrite the tests to the new ids.** In `condensed.test.ts` replace `FETCH`/`SAVE`/`PROGRAM` with the box ids of the test payload; add:
  - `test('a method called twice is two groups')`: payload with one METHOD `m` box per call site (`b1`, `b2`, same `method`, same `label`), one leaf each → `condensed(p, new Set()).nodes` has two `GROUP`s with ids `b1`, `b2`, same label.
  - `test('a box with no leaves of its own is not drawn')`: box `outer` holding only box `inner` holding leaf `x` → groups drawn = `['inner']`.
  - `test('a leaf without a box throws')`: `assert.throws(() => condensed(p, new Set()), /has no method box/)`.
  - `test('a boundary box keeps the boundary look')`: group of a `boundary: true` box has `boundary: true`; a plain box's group has no `boundary` key.
  - `test('the group hover names the call site')`: box with `source` `p/A.java:12:5` → `hoverText(...)[1] === 'called at A.java:12'` (use the file-name form `where()` already yields).
  Fix `focus.test.ts`, `mermaid.test.ts`, `layout.test.ts`, `condensed-sweep.test.ts` assertions that name `program`/`boundary:`.
- [ ] **Step 2: Run** `npm test` → the new/rewritten tests FAIL.
- [ ] **Step 3: Implement** in `index()`: group = `leaf.parent` (throw if undefined or not a METHOD box); label = `box.label`; `groups` in payload order of the boxes (only boxes with members); mark boundary via the box. Drop `PROGRAM` and the `boundary:<method>` id. In `condensed()` set `boundary: true` from the box. Add the `called at` line in `hoverText` (check `JsonExporter.entry`: if a box's `source` is not the call site, say so in the report rather than guess). Update comments that describe `program`/per-method groups.
- [ ] **Step 4: Run** `npm test` → PASS.
- [ ] **Step 5: Commit** `Condensed: one flat group per call-site box`.

### Task 2: Legend, browser tests, docs, bundle

**Files:**
- Modify: `app/src/main/resources/viewer/reactflow.tsx` (legend heading), `theme.ts` (legend `method` row meaning: `"a method call's body, folded; click to open"`; `GROUP_OUTLINE` comment)
- Modify: `app/src/test/js/browser/*.spec.ts`, `browser/page.ts`, `global-setup.mjs` where they name `program` / `boundary:` or count groups
- Modify: `docs/decisions/viewer.md`, `CLAUDE.md` viewer bullet: groups are call-site boxes, flat, no `program`
- Rebuild: `app/src/main/resources/viewer/reactflow.bundle.js`

**Interfaces:**
- Consumes: Task 1's group ids and `groupOf`.

- [ ] **Step 1: Update legend test** (`unit/legend.test.ts`) and browser `legend.spec.ts` to expect heading `Method boxes`; run → FAIL.
- [ ] **Step 2: Implement** heading + legend row text; adapt browser specs/page helpers to call-site box captions.
- [ ] **Step 3: Rebuild** `npm run build:viewer`.
- [ ] **Step 4: Run** Gradle (constraint command) → BUILD SUCCESSFUL; `npm test` → all pass; `npm run test:browser` → all pass.
- [ ] **Step 5: Commit** `Viewer: method boxes in the legend, tests and docs`.

### Task 3: Fineract box count (main agent)

- [x] Run one `examples/fineract` case (`examples/fineract/run.sh`, see its README) on the branch build; count `GROUP` nodes in the condensed opening; report to the user. Boxes in the opening: repayment 18 (15 groups before), capitalized-income 85 (19), buy-down-fee 106 (14), repayment-domain 315 (28).
