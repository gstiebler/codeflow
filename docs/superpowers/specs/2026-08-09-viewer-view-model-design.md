# The viewer's view model, separated from Cytoscape

## The problem

Which nodes the viewer puts on screen is decided in three places that only exist inside a browser:

- `opening()` — the initial revealed set, read out of `cy.nodes('[type = "METHOD"]').isOrphan()` and
  `.children()`.
- the `cy.on('tap')` body — open-or-fold, decided by `node.descendants().filter(revealed.has)`.
- `apply()` — which ids get `display: element`, and the rule that a `METHOD` node never gets one.

`node --test` cannot reach any of them. So the viewer's pure half (`neighbourhood`, `hiddenDegree`,
`badgeLabel`, `ownLeaves`, `withStubs`) is well covered, and the half that decides what the reader
actually sees is covered only by whatever a Playwright test happens to click.

`member` is what that costs. The page opens on 5 of its 23 leaves and **every one of the five is a
dead click**:

```
OPEN        -> showing 5 of 23: main args App app func1
  click main -> 5   click args -> 5   click App -> 5
  click app  -> 5   click func1 -> 5
tap box b5  -> 23   tap box b20 -> 23        (only a box moves it)
```

The payload is complete — 26 nodes, 23 leaves, 3 boxes, matching `truth.md`. Nothing is lost in
export. The cause is a consequence of the stub mechanism rather than something it missed: `func1`'s
stub is a `RETURN` node with **no edges**, because `app.func1()` passes and returns nothing — the
very case stubs were added for. `neighbourhood(edges, n6, 3)` returns `{n6}`, so the click is inert.
The only thing that opens the body is the box `b5`, which contains exactly one child — the stub,
filling it — so the real target is the thin border around a node that looks like the obvious thing
to press and is not.

A stub carries the method's name, sits where the method is, and does nothing. That reads as "there
is nothing more here", which is the silently-incomplete diagram this viewer exists to prevent.

The commit that introduced stubs (d9c12ff) says it swept all 63 fixtures and found no fixture fully
reachable from any single node, with a median best case of 71%. That sweep was done by hand and
nothing re-checks it. `member` is the proof that it needs to: the claim was true when written and is
false now.

## What changes

Two things, and the second is only possible because of the first.

1. Everything that decides what is on screen becomes a pure function in a new `model.mjs`, and
   `init()` becomes an adapter that writes the result into Cytoscape.
2. A stub click opens its box, so the visible thing is the clickable thing.

## The split

The page is one self-contained file with no server, so an inlined `<script type="module">` cannot
`import` a sibling — there is nothing to resolve `./model.mjs` against on `file://`. And
`HtmlExporter` is deliberately substitution only, so stripping an import line with a regex is not
available either. The way through is concatenation into one module scope:

```
app/src/main/resources/viewer/
  model.mjs    NEW - pure. No DOM, no cytoscape, no imports. Every decision about what
               is on screen. This is what `node --test` imports.
  viewer.mjs   the renderer. init() and the Cytoscape stylesheet. Uses model.mjs's
               exports as free identifiers: it is only ever loaded concatenated after
               model.mjs, never standalone.
```

`template.html` gains one slot and `HtmlExporter` one `.replace`, still pure substitution:

```html
<script type="module">
/*__MODEL__*/
/*__VIEWER__*/
init(/*__PAYLOAD__*/);
</script>
```

`export` inside an inline module script is legal and simply unused, so `model.mjs` is inlined
unmodified. `viewer.mjs` needs a header comment saying it is never loaded on its own, since its
free identifiers would otherwise read as a mistake.

Moving into `model.mjs` unchanged: `REVEAL_DEPTH`, `neighbourhood`, `hiddenDegree`, `badgeLabel`,
`ownLeaves`, `withStubs`. The three existing unit test files change their import path and nothing
else.

## The three functions that become pure

```js
opening(payload)            -> Set<id>   // the parentless box, and its own non-box children
tap(payload, revealed, id)  -> Set<id>   // the whole click decision
screen(payload, revealed)   -> {…}       // the whole view, as data
```

`tap` is where the behaviour change lives, and it is one function so that "what does clicking this
do" has one answer to test:

| Clicked | Result |
|---|---|
| a box with a revealed leaf descendant | fold: drop every leaf descendant |
| a box with none | open: add its own leaves |
| **a stub** | **open the box it stands for: add that box's own leaves** |
| any other leaf | union `neighbourhood(edges, id, REVEAL_DEPTH)` |

The rule that a stub does not open its box inside `withStubs` is **unchanged**, and the two are not
the same rule. That one governs the *derivation*: if a visible stub counted as opening its box, one
pass would offer that box's callees' stubs, and those boxes' callees' stubs, and the whole call tree
would unfold at open — the wall the viewer exists to avoid. A *click* cannot cascade, because
opening `b5` offers only `b20`'s stub, which needs another click. One sentence in d9c12ff covers
both; they need to be two.

## What `screen` returns

```js
{
  showing,   // Set<id>  - leaves on screen: revealed, plus one stub per offered callee
  stubs,     // Set<id>  - which of `showing` are stubs rather than genuinely revealed
  hidden,    // Map<id, {in, out}>  - what badgeLabel already consumes
  nodes: [{ id, label, type, parent, badge, display }],
  edges: [{ source, target, kind, visible }],
}
```

**`display` is `null` for every `METHOD` node.** Today "never set `display` on a box" is an
`if (isBox(node)) continue;` in the middle of a loop — a rule no test can reach, guarding the
failure CLAUDE.md names: a box whose only visible node is a grandchild would be hidden, and the
grandchild would have nowhere to live. As data it is an assertion one line long, and the renderer's
whole obligation becomes `if (n.display === null) continue;`.

**`nodes` and `edges` describe the entire payload, not just what is visible.** Nothing is ever
removed from the Cytoscape graph, so each pass must write `'none'` onto the nodes that just left as
well as `'element'` onto the arrivals; a filtered list would leave stale nodes lit. Both arrays stay
in payload order, so a test can compare them directly.

**`edges[].visible` makes the inner/outer split explicit.** An edge is visible exactly when both
endpoints are showing; `hidden` counts the ones with exactly one endpoint showing, split by
direction. Both come from the one pass over the edges that `hiddenDegree` already does, rather than
the renderer inferring visibility a second time.

**`stubs` is exposed rather than kept internal.** `tap` needs it — "is the thing I clicked a stub?"
is what routes the click — and a test wants to say "`func1` is on screen *as a stub*, not opened",
which `showing` alone cannot distinguish.

`badge` stays a separate field from `label`, exactly as now: the annotation is a property of the
current view rather than of the value, and every existing browser assertion looks a node up by its
plain name.

## Testing

Three layers, split by what each can catch.

**Unit (`npm test`, hand-written payloads).** The three existing files change their import and are
otherwise untouched. New files cover `opening`, `tap` and `screen`. This is where the `member`
regression is pinned as a named behaviour test rather than a regenerated snapshot.

**Corpus sweep (`npm test`, real payloads).** Four properties over all 63 fixtures, needing no
expected output and no fixture author to have anticipated the failure — the argument
`InvariantsTest.kt` makes on the Kotlin side:

| | Property | Catches |
|---|---|---|
| P1 | From `opening`, tapping showing nodes to a fixpoint puts **every** leaf on screen | `member` today: 5 of 23, fixpoint reached immediately |
| P2 | Every box with an open parent has ≥1 showing leaf descendant | a box Cytoscape would refuse to draw |
| P3 | No `METHOD` node carries a `display` | the grandchild-has-nowhere-to-live case, at the decision |
| P4 | Σ per-node hidden counts = edges with exactly one endpoint showing | per-node and global disagreeing |

P1 is the one that matters: it is d9c12ff's hand sweep turned into something that re-runs, and it
fails today.

The payloads come from `AppTest.writePage`, which gains one line: write `graph.json` beside
`graph.html`, gitignored and rewritten every run, same as `ir.txt`. `JsonExporter` already produces
it. The sweep needs a guard — **finding fewer than 60 payloads must fail the run** with "run
./gradlew test first", not pass with an empty loop. Same trap as a negative browser assertion: a
sweep over zero fixtures is green and means nothing.

**Browser (`npm run test:browser`).** Unchanged in kind and largely in content. It stops being the
only place reveal logic is checked and goes back to checking the rendering: that a badge reaches
`n.style('label')`, that a box with only grandchildren visible still draws.

**Mutations to confirm the tests against**, since an absent implementation is not enough: revert the
stub click to inert (P1 fails on `member`, and on the strength of that 71% median, on others); set
`display` on boxes (P3); filter `screen`'s arrays to the visible only (a stale-node browser
assertion); make `hiddenDegree` count edges with both endpoints hidden (P4).

## What this fixes

`member` goes 5 → 23 in a **single** click on `func1`: opening `b5` reveals its 18 own leaves and
offers `getMemberX`'s stub, which is the 23rd.

## Not in scope

No renderer swap and no React Flow — Cytoscape gives derived visibility (an edge hides when either
endpoint does, a box when every descendant does, transitively) and a compound-node model that
`JsonExporter`'s `parent` is already shaped to; re-implementing those by hand is the opposite of
what this change is for. No change to any exporter beyond writing `graph.json`. No change to the
payload format. `truth.md` goldens are untouched: nothing here alters the graph, only which part of
it is on screen.
