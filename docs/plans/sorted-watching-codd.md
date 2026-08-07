# Give an `if`'s join the condition that decided it

## Context

`if1/App.java` lowers to two phis, and the diagram draws neither of them as a choice:

```
5: binOp == 3 4      <- the condition
10: phi a 2 9
11: phi b 7 3
```

Instruction 5 is evaluated and then dropped. On the page, `n7[==]` has both operands flowing *in*
and **no outgoing edge at all** — the value that decides the entire branch is drawn as unused, and
the two joins (`n12[a]`, `n13[b]`) are drawn as ordinary writes of `a` and `b`, indistinguishable
from the two real writes above them that share those labels.

This is inconsistent with how codeflow already draws the *same choice written as an expression*.
`ternary/truth.md` connects the condition to the selection, and
`AppTest.ternaryConnectsBothBranchesAndCondition` asserts it. So `c = cond ? a : b` and
`if (cond) c = a; else c = b;` — the same program — come out as two different shapes, one of which
shows the reader why either value would be taken and one of which does not.

The fix is to make the join *gated*: the condition becomes an input to it, exactly as it already is
for a ternary. In SSA terms this is the move from φ to γ (gated SSA), and codeflow's ternary node
is already a γ. The intended outcome for `if1`:

```
n7[==]  -->|if|    n12[if]      the condition
n4[a]   -->|true|  n12[if]      a = 5, the value if the branch is taken
n11[a]  -->|false| n12[if]      a = 17
n12[if] --> n15[d]
```

Two decisions were taken with the user up front:

- **The join box is labelled `if`**, not the variable's name. This matches the existing convention
  for `ternary` and `switch` boxes, which are labelled by the construct rather than by any variable,
  and it stops `if1` drawing three boxes all called `a`.
- **`if` and the `switch` statement gain a gate; loops and `try` do not.** Both of those have their
  decider already lowered before the join, so it is one change applied twice. A loop's condition is
  lowered *after* its header phi and is computed *from* it, so gating it would add a second kind of
  forward reference and draw a cycle back into the phi; an enhanced `for` has no condition; and for
  a `try`, which `throw` reached the handler is control flow, not a value that exists anywhere.

The second half of the change is the user's follow-up: the true and false inputs should be
distinguishable, so edges gain a kind and the kind is rendered.

---

## Part 1 — the gate

### `ir/Insn.kt`

Add the gate, one nullable field on `Phi`:

```kotlin
/** What decided which path arrived, when that is a value the source computed. */
class Gate(val label: String, val value: Val, val arms: Map<Val, String> = emptyMap())
```

`arms` maps a path's value to `"true"` / `"false"`. It is a map rather than a list parallel to the
paths because `Lowering.join` collapses the reaching values with `.distinct()`, which loses any
positional correspondence; keying by the value cannot drift.

`Phi` gains `val gate: Gate? = null` and splits what is currently one list:

```kotlin
val paths: List<Val> get() = arrived                      // the values, for the object union
override val inputs get() = paths + listOfNotNull(gate?.value)
override fun render() = "phi $name" + paths.joinToString("") { " $it" } +
        (gate?.let { " ? ${it.value}" } ?: "")
```

The condition is in `inputs` so that any pass walking operands generically sees it — but **the
object union must run over `paths` only**. This is the same discipline `Select.alternatives`
already enforces and for the same reason: `switch (name)` over a `String` would otherwise make the
join point at the selector's object, filing one object's fields under another's name.

### `ir/Lowering.kt`

`join` takes the gate description and builds a per-phi `Gate`:

```kotlin
private fun join(
    paths: List<Map<Any, Definition>>,
    source: String,
    gateLabel: String? = null,
    gate: Val? = null,
    armNames: List<String> = emptyList()
)
```

Inside the `values.size > 1` branch, build `arms` by walking `paths` with their index: a value that
reaches the join on **exactly one** path takes that path's name from `armNames`; a value arriving on
more than one, or arriving where `armNames` is empty, takes none. That keeps the labelling honest
without threading case labels through the `switch`.

Call sites:

| Site | Gate |
|---|---|
| `visitIf` (line ~367) | `join(..., "if", condition, listOf("true", "false"))` — `condition` is the `Val` from line 354, which is currently evaluated and discarded |
| `visitSwitch` final join (~682) | `join(..., "switch", selector)` — no arm names |
| `visitSwitch` fall-through join (~660) | `join(..., "switch", selector)` — whether an arm was entered directly or fallen into is decided by the same selector |
| `visitTry` (~826, ~830) | unchanged |
| `loop` (~759) | unchanged — constructs `Phi` directly |

Note `visitIf` passes `listOf("true", "false")` positionally against `listOfNotNull(fromThen,
fromElse)`. When only one branch reaches the join the list is short by one, so the arm names must be
built alongside the path list rather than assumed — `if (x == null) return 0;` has one path, and it
is the true one only when `fromThen` survived.

### `graph/GraphNode.kt`

`Base` currently derives its caption from the lookup key (`val label get() = id.label`). The gated
join needs those separated — it is *keyed* by the variable it is for and *captioned* with the
construct. `labelId(label, insn)` builds `GraphNodeId(stack.push(insn.source), label)`, so keying
two phis at one `if` under the label `"if"` would give them the same key, which is the
derive-an-id-from-attributes hazard the codebase is explicitly arranged against.

```kotlin
class Base(val id: GraphNodeId, val source: String, caption: String? = null) {
    val label: String = caption ?: id.label
}
```

### `ir/IrGraphBuilder.kt`

In `phi`:

- partition `insn.paths` (not `insn.inputs`) for the back-edge split, unchanged otherwise
- id stays `labelId(insn.name, insn)`; pass `insn.gate?.label` as the new `caption`
- the gate's node is an extra input, always already produced
- objects union over the arrived **paths** only

---

## Part 2 — edge kinds and colour

### The model

`GraphNode` holds a bare `ArrayList<GraphNode>` of targets today. Give the edge a kind:

```kotlin
enum class EdgeKind { FLOW, TRUE, FALSE, CONDITION }
class Edge(val target: GraphNode, val kind: EdgeKind)

fun addEdge(node: GraphNode, kind: EdgeKind = EdgeKind.FLOW)
```

`edgesIterator()` now yields `Edge`. Three call sites follow (`MermaidExporter.kt:46`,
`JsonExporter.kt:76`, `GraphmlExporter.kt:87`) — each just reads `.target` instead of the node.

`GraphBuilder.addJoin` takes `inputs: List<Pair<GraphNode, EdgeKind>>`; the alias call site in
`IrGraphBuilder.read` maps its nodes to `it to EdgeKind.FLOW`.

### The ternary too

Consistency is the point of the whole change, so `Select` gains `val condition: Val? = null`, set at
the two emission sites that have one (`ternary`, `switch` expression) and left null for `array` and
lambdas. `addSelection` then marks the condition edge `CONDITION`, and for a ternary marks
`alternatives[0]`/`[1]` as `TRUE`/`FALSE`. A `switch` expression's branches stay `FLOW`, same rule
as the phi.

### Rendering

| Exporter | Change |
|---|---|
| `MermaidExporter` | `A -->|true| B` for non-`FLOW` edges, plus a `linkStyle <n> stroke:...` line per coloured edge. Needs a counter threaded through `processMethod`, since Mermaid's `linkStyle` index is global and counted in declaration order; emit the collected lines just before the `classDef` block |
| `JsonExporter` | one more key: `"kind": "TRUE"` |
| `viewer.mjs` | `payload.edges.map((e) => ({ data: e }))` already passes every key through to Cytoscape, so this is style selectors only: `edge[kind="TRUE"]`, `[kind="FALSE"]`, `[kind="CONDITION"]` |
| `GraphmlExporter` | a `<key for="edge">` declaration and a `<data>` element |
| `HtmlExporter` | nothing — it is substitution over the JSON and the viewer |

Colours: `TRUE` `#2e7d32`, `FALSE` `#c62828`, `CONDITION` `#6a6a6a` dashed.

### The test regexes must be widened first

`AppTest` parses edges with `... --> n\d+\[`, in three places: `edgeLabels` (:133), `reaches` (:146)
and `assertNoSelfEdges` (:191). A Mermaid edge written `-->|true|` stops matching all three, which
would break `ternaryConnectsBothBranchesAndCondition` the moment the ternary's condition edge gains
a label. Change `-->` to `-->(?:\|[^|]*\|)?` in each.

---

## Tests

Behaviour tests first and red before either part is implemented — a regenerated golden proves
nothing here.

New, in `AppTest.kt`:

- `theConditionOfAnIfReachesEachValueItDecides` — on `if1`, via `edgeLabels`: `"==" to "if"` is
  present, and `"if" to "c"` and `"if" to "d"` are too. Red today because no node is labelled `if`.
- `theSelectorOfASwitchReachesTheJoinItDecides` — on `switchStatement`: `"selector" to "switch"`.
- `theTwoPathsOfAnIfAreDrawnAsDifferentEdges` (Part 2) — on `if1`, exactly one `TRUE` and one
  `FALSE` edge into each `if` node. Pair it with a positive count assertion so it cannot pass by the
  feature doing nothing.

Updated:

- `LoweringTest.aUseAfterABranchNamesTheValueFromEachPath` — `"10: phi a 2 9"` becomes
  `"10: phi a 2 9 ? 5"`, and `"11: phi b 7 3"` likewise. `aLoopHeaderTakesTheValueTheBodyLeavesBehind`
  is unchanged, which is the check that loops were left alone.
- `everyFixtureLowersAndEveryValueIsProducedBeforeItIsUsed` already skips `Phi` entirely, so the
  gate needs no exception added.

Existing behaviour tests are all positive `assertTrue(reaches(...))`, so an added edge cannot make
one pass falsely.

Goldens that should move: **`if1`, `aliasBranch`, `switchStatement`** in Part 1, plus `ternary` and
`switchExpression` in Part 2. Of the 8 fixtures containing a phi, `catchParameter` and `tryCatch`
are `try`, and `enhancedFor`, `forLoop` and `varargs` are loops — all five must be untouched, and if
any of them moves, loops or `try` were gated by mistake.

## Docs

Three places currently assert the opposite of what this makes true, and all three are load-bearing
prose:

- `GraphBuilder.addJoin` — "what decides between its inputs is not a value on the diagram at all".
  Now true only of the alias case.
- `Insn.Select` — "a phi is a variable at a place where two paths meet, and what decided between
  them is the branch above rather than any value".
- `CLAUDE.md`, "A local resolves to its definition, and a branch joins with a phi" — needs the gate,
  the `if` caption, and the explicit statement that loops and `try` are ungated and why.

Also note the residue in `docs/if-written-again.md` §1: the `switch`'s per-arm `==` nodes still have
no outgoing edge, since the join is gated by the selector rather than by each arm's comparison.

## Verification

```shell
./gradlew test --tests '*aUseAfterABranch*'                 # red first, then green
./gradlew test --tests '*ConditionOfAnIf*'
./gradlew build                                             # all 142 + the invariants
git status --short -- '*truth.md'                           # exactly the 3 (then 5) expected
npm test && npm run test:browser
./gradlew run --args="app/src/test/resources/if1 --html" > /tmp/if1.html  # eyeball the colours
```

For each moved golden, verify structurally rather than by reading the diff: normalise
`git show HEAD:<path>` and the new file to sorted multisets of `label:TYPE` nodes and
`label:TYPE -> label:TYPE` edges with ids stripped, and diff those. The expected delta per `if`
join is one node caption changing to `if` and one new incoming edge from the condition; anything
else is a real change and needs explaining.
