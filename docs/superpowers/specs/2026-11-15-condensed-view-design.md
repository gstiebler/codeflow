# Condensed view — design

Status: approved (user request 2026-11-15; calls marked *call* are the controller's).
Plan: `docs/superpowers/plans/2026-11-15-condensed-view.md`.

## Intent
A second way to look at the `--html` graph, beside the current boxed view. No method boxes: the
graph starts condensed into a handful of nodes and opens up where the user clicks. Where a node came
from is shown on hover, not by nesting.

## What the user sees
- **A view switch** on the existing `--html` page: `Boxes` (today's view, unchanged, default) and
  `Condensed` (*call*: same page, same payload; no new CLI flag).
- **Initially** (condensed):
  - one node per **boundary method**, holding every node drawn inside any inlined block of that
    method, across all its call sites (params, `call <name>`, `result`, return, the `boundaries`
    token written there); labelled with the method's name, `boundaries`-purple outline (*call*);
  - one node, **everything else**, holding every other leaf (*call*: labelled `program`);
  - edges between those groups; an edge between two members of the same group is not drawn.
- **Clicking opens the graph up**; nothing already shown changes:
  - an individual node → its direct neighbours (in and out) are taken out of their groups and shown
    individually, with the edges between them and it;
  - a group → its *frontier* comes out: members with an edge to a node currently shown outside the
    group. If it has no frontier, all its members come out (*call*: a click always does something);
  - an edge between groups or between a group and a node → the endpoints of the underlying edges it
    stands for come out.
  - A group whose last member came out disappears. A `Reset` button returns to the initial view (*call*).
- **Hover** (node or edge): the method it was created in (`Class#method`), and the source line(s)
  with their text (`Fee.java:42  total = base + tax;`). For a group: its member count and the
  methods/line ranges of its members, capped (*call*: first 10, then `+N more`). For an edge: the
  line of its target node (*call*: where the value arrives — approximate for tricky cases, as allowed);
  a condensed edge lists the distinct lines of the edges it stands for, capped the same way.

## Rules
- **Edges between display nodes.** Each payload edge `u → v` is drawn between `shown(u)` and
  `shown(v)`, where `shown(x)` is `x` if revealed, else its group. Dropped when both land on the same
  display node. Several payload edges onto one display pair are one drawn edge per kind, with a
  count when more than one (*call*: kinds kept apart so ORDER/TRUE/FALSE colours still mean something).
- **Groups are computed, never guessed:** a leaf belongs to a boundary group iff its nearest
  enclosing box is a block of a boundary method; the payload says which boxes those are and which
  method they are (see below). Everything else is `program`.
- **Pure model, as today** (`docs/decisions/viewer.md`): `condensed(payload, revealed) → View` and
  `tapCondensed(payload, revealed, target) → revealed`, where `revealed` is the only state. The
  renderer draws; ELK lays out a flat graph (no compound nodes). ELK options stay strings.
- Leaves look as in the boxed view (type colours, `**call**` lead, edge colours/dashes).

## Payload additions (JsonExporter is the authority; `types.ts` mirrors it)
- On a box (METHOD) node: `method` — the qualified method (`pkg.Class#name(ParamTypes)`), and
  `boundary: true` when it is a boundary method's block.
- `lines`: `{ "<file>": { "<line>": "<text>" } }` holding only the lines some node's `source`
  points at (*call*: keeps the page small; trimmed text).
- Nothing else changes; the boxed view ignores the new fields.

## Not changed
- The boxed view, Mermaid, GraphML, `--summaries`, the graph itself. Goldens: only `graph.json`
  (gitignored) gains fields; `snapshot.md` byte-identical.

## Test
- Model unit tests (hand-written payloads): initial view = one node per boundary method + `program`,
  no intra-group edge; each click rule; edge dedupe per kind with counts; group disappears when
  emptied; reset; hover text for a node, a group, an edge. Each checked against a wrong version.
- Corpus sweeps over every fixture's `graph.json`: every payload leaf is in exactly one display node
  at every step of a click-everything walk; no drawn edge joins a display node to itself; the walk
  ends with every leaf individual and every payload edge drawn once.
- Exporter test: `method`, `boundary`, `lines` on the `boundary` fixtures; `lines` text matches the file.
- Browser test: switch to Condensed, see N+1 nodes on `boundary`, click `program`, see more nodes;
  hover shows `App#entrypoint` and a line; pair every negative with a positive.
