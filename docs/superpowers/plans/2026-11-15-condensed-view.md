# Condensed view Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** a second, condensed view on the `--html` page: no method boxes, boundary methods and "program" as merged nodes, click to open up, hover for method and source lines.

**Architecture:** JsonExporter adds `method`/`boundary` on boxes and a `lines` table; a new pure model module (`condensed.ts`) derives the view from `(payload, revealed)`; the renderer gains a view switch, a flat ELK layout, hover tooltips and click handlers. The boxed view is untouched.

**Tech Stack:** Kotlin (JsonExporter), TypeScript + React Flow + elkjs (viewer), node --test, Playwright.

**Spec:** `docs/superpowers/specs/2026-11-15-condensed-view-design.md`

## Global Constraints
- JDK 25 for Gradle. Viewer bundle is committed: rebuild with `scripts/build-viewer.mjs`; `npm run check:bundle` green.
- `model.ts`-style split: every visibility decision is a pure function of `(payload, revealed)`; `revealed` is the only state.
- ELK options are strings. No compound nodes in the condensed layout.
- `snapshot.md` goldens byte-identical; the boxed view's behaviour and tests unchanged.
- Groups come from the payload's `boundary`/`method` on boxes, never from label text.
- Colours come from `theme.ts` only.

## Review Focus
- A leaf with no enclosing box (entry method's direct children have the entry box as parent; check there is no orphan) → belongs to `program`, never dropped.
- A boundary method called from several roots/call sites → one group, not one per call site.
- A recursive or deeply inlined boundary block nested inside another boundary's block → nearest enclosing box decides.
- Several payload edges of different kinds between the same pair of display nodes → one drawn edge per kind, counts correct.
- Very large graphs (Fineract `graph.json`): the initial view must stay N+1 nodes and render; no per-click O(n²) walk.

---

### Task 1: Payload — `method`, `boundary`, `lines`

**Files:** Modify `app/src/main/kotlin/codeflow/JsonExporter.kt` (or wherever the payload is written), `app/src/main/resources/viewer/types.ts`; Test: the exporter's test (grep `JsonExporter` under `app/src/test/kotlin`).

**Interfaces — Produces:**
- `PayloadNode.method?: string` (on METHOD boxes: `pkg.Class#name(ParamTypes)`), `PayloadNode.boundary?: boolean` (true only on boundary blocks).
- `Payload.lines?: Record<string, Record<string, string>>` — file → line number → trimmed text, only lines referenced by some node's `source`.

- [ ] Test: on the `boundary` fixture JSON, the `boundaryfunc1` box has `boundary: true` and `method` `boundary.Boundary#boundaryfunc1(int)`; the `entrypoint` box has no `boundary`; every node's `source` line has an entry in `lines` whose text equals that line of the file (trimmed); no line in `lines` is unreferenced.
- [ ] Run it, see it fail; implement (the builder knows a block's `Method` and `GlobalContext.isBoundary`); run, pass.
- [ ] `types.ts` mirrors the fields (optional). `./gradlew test` and `npm test` green. Commit.

### Task 2: Condensed model (`condensed.ts`)

**Files:** Create `app/src/main/resources/viewer/condensed.ts`; Test: `app/src/test/js/unit/condensed.test.ts` (+ sweep in the existing corpus sweep file style).

**Interfaces — Consumes:** Task 1's payload fields. **Produces:**
- `groupOf(payload): Map<Id, GroupId>` — leaf → `boundary:<method>` or `program`.
- `condensed(payload, revealed: Set<Id>): CondensedView` with `nodes: {id, label, type | 'GROUP', members?: number, boundary?: boolean}[]` and `edges: {source, target, kind, count}[]`.
- `tapCondensed(payload, revealed, target: {node: Id} | {edge: [Id, Id, EdgeKind]}): Set<Id>` — the click rules of the spec.
- `hoverText(payload, view, target): string[]` — lines of the tooltip per the spec (method, `file:line  text`; groups/edges capped at 10, `+N more`).

- [ ] Tests (hand-written payloads, exact values from the spec): initial = one node per boundary method + `program`; no intra-group edge; node click reveals neighbours only; group click reveals frontier, else all; edge click reveals underlying endpoints; per-kind dedupe with counts; emptied group gone; hover for node/group/edge with the caps.
- [ ] Sweep over every fixture `graph.json`: each leaf in exactly one display node at every step of a click-everything walk; no self-edge; final state = every leaf individual, every payload edge drawn once.
- [ ] Each test checked against a wrong version (say which). Implement, green, commit.

### Task 3: Renderer — switch, layout, hover, clicks

**Files:** Modify `app/src/main/resources/viewer/reactflow.tsx`, `layout.ts` (flat layout function), `theme.ts` (group style: `boundaries` purple outline for boundary groups); rebuild bundle; Test: browser tests (grep `test:browser` config).

**Interfaces — Consumes:** Task 2's functions; `edgeStyle()` and `PALETTE` from `theme.ts`.

- [ ] Browser test: page opens in Boxes (unchanged); switch to Condensed on `boundary` → 4 nodes (3 boundary methods + `program`); click `program` → more nodes, the 3 groups still there; hover a leaf → tooltip contains `App#entrypoint` and `App.java:`; Reset → 4 nodes. Pair each negative with a positive; wait on `window.drawn`.
- [ ] Implement: view switch (state lives beside `revealed`, boxed state untouched), flat ELK layout with string options, tooltip on node/edge hover, click → `tapCondensed`. Leaves keep type colours and the bold `call` lead.
- [ ] `npm test`, `npm run check:bundle`, `npm run test:browser` green. Commit.

### Task 4: Docs

**Files:** `docs/decisions/viewer.md` (section "The condensed view": groups, click rules, edge dedupe, hover approximation), `CLAUDE.md` (one line under the viewer rule).

- [ ] Write; commit.
