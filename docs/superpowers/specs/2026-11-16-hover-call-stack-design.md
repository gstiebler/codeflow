# A leaf's hover shows its call stack — design (short)

Status: approved (user request 2026-11-16, "For the nodes tooltip? Is it possible to add something
like a "callstack", and not just the code line?"). Builds on `docs/decisions/viewer.md` (hover).

## Intent
In the condensed view a leaf's tooltip says the method it was drawn in and its line. With bodies
inlined per call site, the same line is drawn once per call path (a JsonCommand read reached through
eight `command.xxxNamed` calls), and the tooltip cannot tell them apart. A stack — which calls led
here — is what tells the reader *which* of the copies they are looking at.

## Approaches
- **Payload carries each box's call site; the viewer walks `parent`** (chosen). The builder already
  has it (`GraphBuilderBlock.openedAt`, the call's `path:line:col`); the boxes already nest by call.
  One field, no new structure.
- Export each node's `PosStack`: duplicates what the box nesting already says, on every node.
- Viewer only, walking `parent` with the boxes' declaration lines: names the methods but not the
  lines that called them, which is the half that tells copies apart.

## Changes
1. **JsonExporter:** every box except a root gets `"called": "<path:line:col>"` — where it was called
   from (`openedAt`). A root has none (nothing called it). The call line's text joins `lines`.
2. **types.ts:** `called?: string` on `PayloadNode`, METHOD only.
3. **`hoverText` for a leaf** becomes a stack trace, innermost first, one frame per line, each in the
   format an edge entry already uses: `Class#method  File.java:N  text`. Frame 0 is the leaf's box and
   the leaf's line; each next frame is the enclosing box and the line its child box was `called`
   from, up to the root. A box with no `called` (an older payload) ends the walk. A leaf with no box:
   its line alone. Capped at `HOVER_CAP` then `+N more`, like the rest. Groups and edges unchanged.

## Not changed
Mermaid/GraphML/goldens, the boxed view, model, layout, condensed grouping.

## Test
- AppTest: in a fixture where a method is called from two lines, each block's `called` is its own
  call line, the root has no `called`, and `lines` holds the call lines' text.
- condensed.test.ts: a leaf two boxes deep hovers as three frames (fails against today's two-line
  method + line); an orphan hovers as its line; a payload without `called` stops at the first box.
- `./gradlew test`, `npm test`, `npm run test:browser` green; bundle rebuilt.
- Docs: `viewer.md` hover paragraph.
