# Boundary look — design (short)

Status: approved (user request 2026-11-15; calls marked *call* are the controller's).
Builds on `2026-11-15-boundary-functions-design.md`, `2026-11-15-boundary-order-design.md`,
`2026-11-15-order-edge-kind-design.md`.

## Intent
Make boundary calls and their order chain easy to spot and read:
1. the `boundaries` token nodes get a colour of their own;
2. `call<name>` becomes `call <name>`, with **call** in bold;
3. the token nodes are labelled `boundaries <entry point name>`.

## 1. Token colour
- New `NodeType.BOUNDARIES` for every token node: the chain start, each write after a call, and the
  join where `if` arms meet (*call*: a type, not a flag, because colour is per type in both
  renderings). The gated `if` node of that join stays `BIN_OP`.
- Colour (*call*, purple to match ORDER edges `#7b1fa2`): Mermaid `classDef BOUNDARIES fill:#7B1FA240`,
  viewer `PALETTE.BOUNDARIES: 'rgba(123, 31, 162, 0.25)'`; `types.ts` `NodeType` gains it. They must match.
- Everywhere code treats `VARIABLE` specially (viewer model/reveal, invariants, GraphML/JSON, test
  helpers), `BOUNDARIES` gets the same treatment unless there is a reason not to — list each place
  and the decision in the report. Rebuild the bundle.

## 2. `call <name>` with bold `call`
- The boundary call's label becomes `call <name>` (a space): `call boundaryfunc1`. Plain text in the
  label, so search, JSON and summaries read it as written (*call*: summaries ext atoms become
  `ext:call boundaryfunc1@…`; only boundary fixtures' summary outputs may move).
- Bold is drawn from a structured fact, never by matching the text `"call "`: the node carries the
  part to embolden (e.g. a `lead = "call"` on the node / JSON payload, set because Lowering marked
  the `Call` as a boundary's assumed operation).
  - Mermaid: a markdown-string label, `n3["`**call** boundaryfunc1`"]`, if the Mermaid version
    the page and docs use renders it; otherwise `<b>call</b> boundaryfunc1`. Say which, and why.
    Check escaping in `getNodeStr` still holds for the rest of the label.
  - Viewer: render `lead` in a `<strong>` before the rest of the label.
  - GraphML: plain label (*call*).
- Node type stays `EXTERNAL`.

## 3. `boundaries <entry point name>`
- Label = `boundaries ` + the simple name of the root method being built (`boundaries entrypoint`).
  Inlined callees' token nodes carry their root's name. Several roots: each chain its own name; the
  `SHARED` edges between them stay. Overloads / same name in two classes share a label (*call*, as asked).
- Label only: the token's identity (`JNodeId` name `BOUNDARY_ORDER`, its holder) is unchanged, so
  cross-root `SHARED` matching and joins keep working.

## Not changed
- Without `--boundary`: every golden byte-identical.
- Edge kinds, the chain's shape, Lowering's instruction order.

## Test
- Behaviour: in `boundary/` every token node is `BOUNDARIES` and labelled `boundaries entrypoint`,
  no other node is `BOUNDARIES`; each boundary call node is labelled `call boundaryfunc<N>` and has
  `lead = "call"`; no other node has a lead (an unresolved non-boundary call stays plain).
  `boundaryOrder/`: an inlined callee's token is labelled with the root's name; two roots → two names.
- `boundaryRoots/` (`--from App#first --from App#second --boundary Store`): each root's chain is
  labelled after it, each root has its own call boxes, each root's arguments reach only its own
  calls, the chains are tied only by `SHARED` edges (no `ORDER` across roots), no body literal.
- Each check fails against a wrong version (lead set from the text prefix; label from the current
  frame's method instead of the root).
- Viewer: a unit test that a `BOUNDARIES` node gets its colour and a node with a lead renders it in
  `<strong>`; browser test if one already covers node labels.
- Golden diff: only `boundary/`, `boundaryOrder/` (and boundary summary outputs) move; structural
  diff, every leftover explained.
- Docs: replace `call<name>` with `call <name>` in `CLAUDE.md`, `docs/decisions/calls-and-dispatch.md`
  and the paused `2026-11-15-outside-calls-are-boundaries-design.md`; add `BOUNDARIES` where node
  types are listed.
