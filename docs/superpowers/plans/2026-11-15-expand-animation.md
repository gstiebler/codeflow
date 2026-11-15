# Expand animation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** after a click that opens up the graph, the user can match nodes before and after: stable layout, animated transition, new things highlighted.

**Architecture:** layout seeded from the previous drawing (`layout.ts`); a pure `diffViews` helper; the renderer tweens positions and marks new nodes. The model (`model.ts`, `condensed.ts`) is untouched.

**Tech Stack:** TypeScript, React Flow, elkjs, node --test, Playwright.

**Spec:** `docs/superpowers/specs/2026-11-15-expand-animation-design.md`

## Global Constraints
- Starts after the condensed view is merged (same renderer files).
- ELK options are strings. Colours from `theme.ts`. Model functions stay pure; positions are renderer state.
- `window.drawn` fires after the animation ends; a test hook sets duration 0.
- `prefers-reduced-motion: reduce` → no motion, highlight kept.
- Bundle committed and rebuilt; `npm run check:bundle` green; goldens unchanged.

## Review Focus
- A click that removes a display node (emptied condensed group) and adds many → removed fade out, no node left stuck at opacity 0.
- Two clicks faster than the animation → the second starts from the current (mid-flight) positions, no jump back.
- Switching Boxes ↔ Condensed → no animation across views (*call*: a plain redraw).
- A huge graph (Fineract) → the animation is skipped above a node count (*call*: 2,000 drawn nodes).
- Nested boxes in Boxes view → children animate with their box, not doubly offset.

---

### Task 1: Stable layout, measured

**Files:** Modify `app/src/main/resources/viewer/layout.ts`; Create `app/src/test/js/unit/layout-stability.test.ts` (+ a measuring script under `scripts/` only if it is reused).

**Interfaces — Produces:** `layout(view, previous?: Map<Id, {x, y}>)` — same result type as today; `previous` orders nodes/edges and, if chosen, seeds interactive positions.

- [ ] Measure on every fixture `graph.json` (click-everything walk, both views): mean displacement of kept nodes and order inversions per click, today's layout.
- [ ] Try model-order and interactive options; keep the one that reduces both most; record the numbers in the report and in `docs/decisions/viewer.md`.
- [ ] Unit test: small payload, one-neighbour reveal keeps every old node's relative order (fails against today's layout — show it).
- [ ] Green, rebuild bundle, commit.

### Task 2: Diff and animation

**Files:** Create `app/src/main/resources/viewer/transition.ts` (`diffViews`, tween helper); modify `reactflow.tsx`, `theme.ts` (highlight colour); Test: `app/src/test/js/unit/transition.test.ts`, browser tests.

**Interfaces — Produces:** `diffViews(prev: Drawn, next: Drawn, clicked?: Id): {kept: Id[], added: Id[], removed: Id[], start: Map<Id, {x, y}>}`.

- [ ] Unit tests for `diffViews` (kept/added/removed by id; added start = clicked node's old position; no clicked → their own position).
- [ ] Implement tween (~350 ms ease-out, requestAnimationFrame or CSS transform transitions), fade in/out, viewport anchored on the clicked node, refit only if something new is off screen, reduced-motion path, size cutoff, `window.drawn` after the end, duration hook.
- [ ] Highlight ring on added nodes/edges (~1.5 s fade) and a "last clicked" outline.
- [ ] Browser tests per the spec (clicked node moves < 40 px; highlight class appears then goes; reduced motion → no transition; `drawn` after end). Pair negatives with positives.
- [ ] `npm test`, `check:bundle`, `test:browser` green. Commit.

### Task 3: Docs

- [ ] `docs/decisions/viewer.md`: "Expanding without losing your place" — stability options chosen and the numbers, why the model knows nothing of motion, reduced motion, cutoff. Commit.
