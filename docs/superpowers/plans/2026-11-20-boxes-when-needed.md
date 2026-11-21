# Boxes when needed Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hidden leaves default to `program`; a call-site method box is drawn only when boundary or open (spec rule).

**Architecture:** One function, `groupOf(payload, revealed)`, decides each hidden leaf's group; `condensed`, `tapCondensed`, `focusOf`, `focusPath`, `hoverText` all draw through it instead of the static `ix.group`/`ix.members`. Renderer, Mermaid and layout already draw any `GROUP` flat.

**Tech Stack:** TypeScript viewer (React Flow + ELK, esbuild bundle), node:test, Playwright, Kotlin/Gradle.

**Spec:** `docs/superpowers/specs/2026-11-20-boxes-when-needed-design.md`

## Global Constraints

- Work in `/Users/guistiebler/Documents/Projetos/codeflow/.claude/worktrees/no-boxes` on branch `no-boxes`. Don't push.
- A hidden leaf's group: its box if the box is `boundary`; its box if the box is open (a member revealed, or a hidden member with a non-EVENT edge, either direction, to a revealed leaf); else `program`.
- Group id and label `program`; no `boundary`, no `called` line. Order: `program` first, then boxes in payload order. A group with no hidden member is not drawn.
- Flat: never a box inside a box.
- Clicks unchanged: frontier, or all members when there is no frontier - for `program` too.
- Legend "Method boxes" rows: `program` (`everything not yet in a method box; click to open`), method, boundary, then the existing rows.
- Gradle: `JAVA_HOME=~/.sdkman/candidates/java/21.0.11-tem ./gradlew test --rerun-tasks`. `npm test` needs Gradle run first (writes graph.json); it checks the bundle is fresh, so run `npm run build:viewer` before it.
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

- A box whose only revealed-leaf contact is an EVENT edge stays in `program`.
- Revealing the hidden member that touched the screen keeps the box drawn (a member is now revealed) - never back into `program`.
- A nested call's box opens on its own contacts, not because its caller's box opened.
- Clicking `program` when it has no frontier reveals all its members (the sweep must still end).
- Two call sites of one method: one can be open while the other is still in `program`.

---

### Task 1: The rule in the model

**Files:**
- Modify: `app/src/main/resources/viewer/condensed.ts` (`index`, `groupOf`, `shownAs`, `condensed`, `tapCondensed`, `focusOf`, `focusPath`, `hoverText`, header comment), `types.ts` comments if they describe groups
- Test: `app/src/test/js/unit/condensed.test.ts`, `condensed-sweep.test.ts`, `focus.test.ts`, `mermaid.test.ts`, `layout.test.ts`

**Interfaces:**
- Produces: `export const PROGRAM = 'program'`; `groupOf(payload: Payload, revealed: Set<Id>): Map<Id, Id>` - every HIDDEN leaf -> its group id (`PROGRAM` or a box id); revealed leaves are absent. Keep the static leaf -> box map internal (`ix.box`).

- [ ] **Step 1: Tests first.** In `condensed.test.ts` add:
  - `test('everything starts in program')`: a payload with one plain box `b1` holding `x`, `y` and an EVENT `e` (box `b0`) with an EVENT edge `e->x`; `condensed(p, new Set(['e'])).nodes` groups = `[['program', 2]]` (id, members).
  - `test('a data edge to a revealed leaf opens the box')`: same payload plus a FLOW edge `e->y`; groups = `[['b1', 2]]` (no `program` group: nothing else is hidden).
  - `test('an open box stays drawn after its contact is revealed')`: reveal the touching member; box still drawn while it has a hidden member.
  - `test('a boundary box is drawn from the start')`: boundary box group present with `boundary: true` and empty `revealed`.
  - `test('a nested box opens on its own contacts')`: outer box opens; inner box with no contact stays in `program`.
  - `test('one call site open, the other in program')`: two boxes, same `method`; only one touches.
  - `test('clicking program with no frontier reveals all its members')`.
  Rewrite `groupOf` uses (`groupOf(p, revealed)`), and update ids/counts in `condensed.test.ts`, `focus.test.ts`, `mermaid.test.ts`, `layout.test.ts` and the sweep's `faults()` (count per `groupOf(payload, revealed)`).
- [ ] **Step 2: Run** `npm test` -> new tests FAIL.
- [ ] **Step 3: Implement.** `groupOf(payload, revealed)`: one pass over `payload.edges` (skip kind `EVENT`) marking a box open when one end is revealed and the other is a hidden leaf of it; plus every box with a revealed member; boundary boxes always. `shownAs`, `condensed` counts, `tapCondensed`'s `inside`, `focusOf`'s "is a group", `hoverText`'s `inside`/label/`called` all go through it. `program` gets no `boundary` key and no `called` line. Update comments that say every box is drawn.
- [ ] **Step 4: Run** `npm run build:viewer && npm test` -> PASS.
- [ ] **Step 5: Commit** `Condensed: everything in program; a method box only when open`.

### Task 2: Legend, browser tests, docs, bundle

**Files:**
- Modify: `app/src/main/resources/viewer/theme.ts` (legend `program` row; `GROUP_OUTLINE` comment), `app/src/test/js/unit/legend.test.ts`
- Modify: `app/src/test/js/browser/*.spec.ts`, `browser/page.ts` where they count nodes/lines or tap a box that now starts inside `program`
- Modify: `docs/decisions/viewer.md`, `CLAUDE.md` viewer bullet, `docs/if-written-again.md` if it names the per-call-site opening
- Rebuild: `app/src/main/resources/viewer/reactflow.bundle.js`

**Interfaces:**
- Consumes: Task 1's `PROGRAM` and grouping.

- [ ] **Step 1:** `legend.test.ts` expects the `program` row first under "Method boxes"; run -> FAIL.
- [ ] **Step 2:** Implement the row (fill `GROUP_FILL`, outline `GROUP_OUTLINE`, meaning from Global Constraints); adapt browser specs to the new opening and click results (read the new counts off the running page; don't loosen assertions to ranges).
- [ ] **Step 3:** `npm run build:viewer`.
- [ ] **Step 4:** Gradle (constraint command) -> BUILD SUCCESSFUL; `npm test` -> pass; `npm run test:browser` -> pass.
- [ ] **Step 5: Commit** `Viewer: program box back in the legend, tests and docs`.

### Task 3: Fineract counts (main agent)

- [ ] The engine is unchanged, so reuse the four Fineract JSONs from the method-boxes run: count `GROUP` nodes from `condensed()` in the opening and after one click on the event; report.
