# Progressive reveal in the viewer

Replaces the viewer's fold-and-dim interaction with one that starts nearly empty and grows only
where asked. Clicking a node reveals its neighbourhood; what is not revealed is *absent*, not
greyed.

## Why

The viewer inlines a callee's body at every call site, so node count grows with call sites rather
than source size. `funcCall` — a four-method toy — is 39 nodes and 5 method boxes. Real input is
worse, and the two mechanisms that were supposed to absorb that do not:

- **Dimming** keeps every node on screen and in the layout. Opacity 0.15 across 34 of 39 nodes is
  still 34 nodes' worth of clutter and 34 nodes' worth of ELK.
- **Method folding** is the wrong granularity. It hides by *where a value was declared*, but the
  question the tool answers is *where a value went*, and a value crosses method boundaries freely.

So the unit of reveal becomes the dataflow neighbourhood, not the method body.

## The model

One piece of state:

```js
const revealed = new Set();   // leaf node ids only — never a METHOD box
```

Everything else is derived, and derived by Cytoscape rather than by us. This was verified against a
real page, not assumed:

| Thing | Decided by | Verified |
|---|---|---|
| Leaf node visible | `revealed.has(id)` | the only `display` we set |
| Edge visible | Cytoscape | `x->a` reported `visible:false` with `a` hidden and the edge untouched |
| Box visible | Cytoscape | `methodA` reported `visible:false` with `display:element`, all children hidden |
| Box visible for a *grandchild* | Cytoscape | revealing only `methodC`'s leaves left `methodB` and `main` visible |

**The rule that makes this work: never set `display` on a `METHOD` node.** Leave boxes at
`element` forever. A box whose descendants are all hidden disappears on its own; one with a visible
descendant three levels down reappears on its own. Setting `display:none` on a box breaks the
second case — the box stays hidden and its visible grandchildren have nowhere to live.

ELK respects this. Hiding 35 of 39 nodes and re-running the layout packed the remaining 4 into
133×322px rather than leaving holes where the hidden ones had been.

## The interaction

| Gesture | Effect |
|---|---|
| Load | `revealed` = the entry method's own direct leaf children |
| Click a leaf node | `revealed ∪= neighbourhood(id, 3)` |
| Click a method box | `revealed −= every leaf descendant of that box` |
| Press `R` | `revealed` = the load state |

Reveals accumulate. Nothing disappears except through a box click or a reset — this is the whole
point, and it is why the reveal is a set union rather than a replacement.

### neighbourhood

```js
/**
 * Every node within `depth` edges of `startId`, following edges in either direction.
 *
 * Undirected on purpose. `c = a + b` clicked at `a` must show `b`: the old directional trace
 * excluded it, which meant reading an operator whose second operand was invisible.
 *
 * `seen` is what makes this terminate — a loop feeding a variable back into itself is a cycle,
 * and following one forever hangs the page with nothing on screen to explain why.
 */
export function neighbourhood(edges, startId, depth)
```

Breadth-first, returns a `Set` containing `startId`. Depth is a parameter rather than a constant so
the boundary is testable; the viewer passes `REVEAL_DEPTH = 3`.

Measured on `funcCall`: depth 3 from `x` reveals 6 of 34 leaf nodes — `x`, `5`, `a`, `+`, `b`, `c`.

### Applying it

```js
function apply() {
  for (const node of cy.nodes()) {
    if (node.data('type') === 'METHOD') continue;      // boxes derive; see the rule above
    node.style('display', revealed.has(node.id()) ? 'element' : 'none');
  }
  cy.layout(LAYOUT).run();
}
```

A box click reads `box.descendants()` and deletes the non-`METHOD` ones from `revealed`. The box
then vanishes along with its contents, because nothing visible is left inside it.

## What this removes

- **`traceFrom`** and its unit tests. Nothing calls it once dimming is gone. `neighbourhood` is not
  a rename of it: `traceFrom` is an unbounded closure in each direction separately, `neighbourhood`
  is a bounded undirected ball.
- **`.dimmed` / `.traced`** styles and the three trace browser tests.
- **`cytoscape-expand-collapse`**, the vendored library, its meta-edge style rule, and the
  meta-edge test. Its collapse *removes* children from the graph while this design *hides* them;
  running both means `revealed` and the graph disagree about what exists. Dropping it also retires
  the "collapsing removes children" and meta-edge traps from `CLAUDE.md`.

`HtmlExporter` loses one substitution token (`__EXPAND_COLLAPSE__`) and `template.html` one
`<script>` tag.

## Testing

Split the same way as today, by what each layer can actually catch.

**Unit (`npm test`)** — `neighbourhood` imported straight from `viewer.mjs`:

- depth 0 returns only the start node
- depth 1 returns immediate neighbours in *both* directions
- the depth boundary excludes a node exactly one hop too far — the assertion that fails if the
  bound is off by one
- terminates on a cycle
- an isolated node returns just itself

**Browser (`npm run test:browser`)** — against a page built from `funcCall`:

- opens showing the entry method's own body and nothing from any callee
- clicking a node reveals its neighbourhood, and a specific node 4 hops away stays hidden
- a second click on a different node *adds* — nodes from the first click are still visible
- clicking a method box hides its contents and the box with them
- reset returns to exactly the opening set
- a box is visible when only a grandchild is revealed

Every negative assertion ("stays hidden", "box gone") is paired with a positive one in the same
test, because a page that reveals nothing at all satisfies every negative assertion trivially. This
already produced two false greens in this viewer's history. Each new test must be checked against a
*wrong* implementation — revealing everything is the mutation to try — not merely an absent one.

## Known trade-offs

- **No frontier cue.** A visible node gives no sign whether it has hidden neighbours, so the
  opening screen is a set of boxes with few visible edges between them and nothing indicating where
  clicking pays off. Accepted deliberately for simplicity; a dashed border on nodes with hidden
  neighbours is the cheap fix if it proves annoying in use.
- **A folded box leaves no trace.** It disappears entirely rather than collapsing to a stub, so
  re-opening it means clicking a node near it, or resetting. This is what keeps box visibility
  derived and adds no fold state.
- **Layout runs on every click** and ELK is not fast. Acceptable while the visible set is small,
  which is the entire premise; if it bites, the fix is to lay out only the newly revealed nodes.

## Out of scope

`app/src/test/resources/codemap` crashes the tool outright —
`MethodTree.getBody()` is null for a bodyless method and `AstBlockProcessor` dereferences it. Found
while measuring fixture sizes for this design. Unrelated to the viewer and left alone.
