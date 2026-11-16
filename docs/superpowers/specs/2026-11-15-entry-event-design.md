# Entry event — design (short)

Status: approved (user request 2026-11-15; calls marked *call* are the controller's).
Builds on `2026-11-15-boundary-look-design.md`, `2026-11-15-condensed-view-design.md`.

## Intent
Each entry point is something that *happens*: a request, a job, `main`. Give every root an event
node that starts it, so the graph shows what the entry point sets going: one edge from the event to
every node of that root that nothing else feeds. And the condensed view opens on these events too,
next to the boundary methods.

## The graph
- **One `EVENT` node per root**, created first in the root's block, so it has the root's lowest
  serial and sits at the top of its box (*call*). Label `event <root simple name>`: `event entrypoint`
  (*call*: same naming as `boundaries <root>`). New `NodeType.EVENT`.
- **Edges:** after the root is built (in `IrGraphBuilder.build(root, …)`, before the cross-root
  `SHARED` edges are added in `build(roots)`), one edge from the event to every node drawn under
  that root — its block and every inlined block below it — that has no incoming edge. Literals,
  the root's parameters, `this`, the `boundaries` chain start, unwritten field/static reads and
  `UNMODELLED` nodes all qualify; METHOD boxes are not nodes and do not.
  - A read that later gets a `SHARED` edge from another root still gets its event edge (*call*:
    "no incoming edge" is judged within the entry point; the other root may not have run).
  - The event itself is excluded. Decided structurally (incoming-edge count), never by label or type.
- **New `EdgeKind.EVENT("event")`** (*call*: not data, so `reaches()` stays data-only and every
  existing data assertion keeps meaning what it meant; not ORDER, which means "ran before").
- **Look** (*call*, teal, distinct from boundary purple):
  - Mermaid `classDef EVENT fill:#00897B40`; EVENT edges `-. event .->` with linkStyle `#00897b`,
    the same mechanism as ORDER.
  - Viewer `PALETTE.EVENT: 'rgba(0, 137, 123, 0.25)'`, `edgeStyle('EVENT')` teal and dashed;
    `types.ts` gains `EVENT` in both unions. They must match the Kotlin hex (unit test, as for BOUNDARIES).
  - GraphML/JSON write type and kind generically.
- **Not changed:** Lowering, instructions, `--summaries` (the event is the builder's, not an
  instruction; the Summarizer never sees it), `SummaryParityTest` results.

## The condensed view
- Initially: one node per **boundary method**, one per **entry point event**, and `program`.
- The event is a leaf (*call*), revealed from the start: `condensedOpening(payload)` = the ids of
  the payload's `EVENT` leaves; Reset returns to that. Its edges into `program` / boundary groups are
  drawn with the usual per-kind dedupe and counts. Clicking it reveals its neighbours (what it
  starts), by the existing leaf rule. Hover: the root's method and line, as for any leaf.
- Boxes view: the event is a leaf in the root's box; nothing else changes.

## Goldens
Every `snapshot.md` moves (one node per root, its edges, the classDef and linkStyles; serials in
each root shift by one). Structural diff against `HEAD`: the only leftovers allowed are the EVENT
node(s), EVENT edges, the classDef/linkStyle lines, and id renumbering. Explain any other.

## Test
- Behaviour (AppTest): on `boundary/` the root has exactly one EVENT node, `event entrypoint`; every
  non-event node with no incoming non-EVENT edge has exactly one EVENT edge in, from it; no node with
  another incoming edge has one; the `boundaries` chain start is among the targets. `boundaryRoots/`:
  two events, each with edges only into its own root's nodes; a SHARED-fed read still has its event edge.
- Invariant (InvariantsTest, every fixture): every node of a root except its event has an incoming
  edge; each root has exactly one EVENT node.
- Each fails against a wrong version (event edges to every node; event edges computed after SHARED;
  one event for all roots) — say which.
- Viewer unit: condensed opening on a payload with two events = boundary groups + two events +
  `program`; Reset → same; EVENT colour/edge style match. Corpus sweep stays green (each leaf in
  exactly one display node at every step).
- Browser: Condensed on `boundary` opens on 5 nodes (3 boundary methods + `event entrypoint` +
  `program`) — verify the real count; clicking the event reveals more nodes. Pair negatives with positives.
- Docs: `docs/decisions/joins.md` / `calls-and-dispatch.md` edge-kind list, `viewer.md` condensed
  section, `CLAUDE.md` EdgeKind list gains `EVENT`.

## Tasks
1. Graph: NodeType/EdgeKind EVENT, builder, Mermaid look, behaviour + invariant tests, goldens + structural diff. Commit.
2. Viewer: types, theme, `condensedOpening(payload)`, unit + browser tests, bundle rebuilt. Commit.
3. Docs. Commit.
