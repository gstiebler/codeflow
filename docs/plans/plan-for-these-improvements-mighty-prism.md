# Node identity, a pinned toolchain, and dead code

## Context

codeflow parses Java with `com.sun.source` and emits a Mermaid dataflow graph. Its purpose is to
be pointed at code nobody has read yet and be believed, so a graph that is silently wrong is
worse than one that fails.

The assessment this plan comes from recommended items 1 and 6 as the first move — make an
unmodelled AST kind fail loudly, and put `file:line:col` on the failure. That is already done, in
`39e5beb`: `AstBlockProcessor.scan` rejects any expression kind outside `MODELLED_EXPRESSIONS`,
`ProcessorContext.location` exists, and `visitUnary`, `visitCompoundAssignment` and
`visitSwitchExpression` were implemented along with the `unary` and `unsupported` fixtures.

What remains from Tier 1 is **item 2: there are two incompatible notions of node identity, and
both are hashes.** This is the last mechanism in the codebase that can turn a correct analysis
into a wrong picture without anything failing, so it is the subject of this plan. Alongside it go
the two hygiene items that cost nothing and remove real variables from the next debugging
session: pinning the JDK, and deleting code that is never reached.

Deliberately **not** here: item 3 (resolution by simple name), items 4 and 5 (swallowed
exceptions, recursion guard). All real, all worth doing, all separate. One decision about item 5 is
already settled and recorded so it is not re-litigated later: when a method calls itself directly
or through a cycle, the recursive call becomes a single opaque `EXTERNAL` node — arguments in,
result out — the same treatment a call outside the analysed sources gets. It is honest about what
happened (a call whose body was not inlined further) and it keeps recursive code analysable.

---

## The problem in item 2

`GraphNodeId` serves two different jobs and derives both from a hash:

| Job | Computed by | Meaning wanted |
|---|---|---|
| Lookup key — "which variable is this?" | `getIntId()`, overridden in `JNodeId` as hash(`name`, `memPos`) | Per **variable**: a read of `x` must find the `x` declared elsewhere, so the position is deliberately absent |
| Rendered identity — "which box on the diagram?" | `getExtId()`, never overridden, hash(`label`, `stack`) | Per **occurrence**: `y = 1; y = y + 1;` should be two boxes |

The two roles genuinely differ, so making them agree is not the fix. The fix is to stop asking one
value to do both, and to stop deriving either from a hashed number:

- Because rendered identity is `hash(label, stack)` truncated into a `Long`, **two unrelated nodes
  can render as the same Mermaid id and silently merge into one box.** The `getPosId` bug fixed in
  `7b8b250` was one instance. `assertNoSelfEdges` catches only the sub-case where the merge shows
  up as a self-loop; a merge between two nodes that never reference each other is invisible.
- Because `equals` compares `getIntId()` values rather than components, and returns true for *any*
  `GraphNodeId` whose number matches, a `JNodeId` and a plain `GraphNodeId` can compare equal by
  coincidence. They are mixed in the same maps: `GraphBuilderBlock.nodeIdToVariable`,
  `MemPos.referencedNodes` and `GlobalContext.idToMemPos` all take both.

Identity assigned by hashing attributes means a collision is always possible and always silent.
Identity assigned at creation cannot collide at all.

---

## Approach

Three commits, in this order. The ordering matters: step 1 must prove the JDK is not a variable
*before* step 3 intentionally rewrites every snapshot, or the two sources of churn are entangled
and neither can be explained.

### Commit 1 — pin the toolchain (item 8)

Nothing in the build says which JDK to use. CI pins 17; locally `java -version` is 17 while
`/usr/libexec/java_home` resolves to 21 and five JDKs are installed. Whichever one Gradle happens
to start on is the one that parses the Java under analysis, and codeflow reads that JDK's javac
trees directly through `ToolProvider.getSystemJavaCompiler()` — so the JDK is an input to the
output, not just to the build.

- `app/build.gradle` — add a `kotlin { jvmToolchain(21) }` block. 21 matches the intent already
  recorded in the plugin comment ("21 … the current LTS and therefore the default JDK for most new
  clones") and is available locally as JBR 21.0.5, which Gradle's toolchain auto-detection finds
  under `~/Library/Java/JavaVirtualMachines`.
- `.github/workflows/gradle.yml` — `java-version: '17'` → `'21'`.
- While here: `useKotlinTest('1.8.10')` against Kotlin plugin `1.9.25` is an unintended mismatch;
  align it to `1.9.25`.

**This commit must not change a single byte of any `truth.md`.** If it does, the JDK was changing
the parse, which is exactly the thing worth knowing before touching anything else — investigate
rather than accept.

### Commit 2 — delete what is never reached (item 9)

No behaviour change; snapshots must again be byte-identical.

- `GraphNode.kt` — remove `class Assignment`. It is never constructed and has no branch in
  `createNode`'s `when`, so it could not be constructed through the normal path anyway.
- `GlobalContext.addMethod` — drop the unused `posId: Long` parameter; update the single caller,
  `AstProcessor.kt:33`, which currently computes `ctx.getPosId(node)` for nothing.
- `GlobalContext.createMemPos` — drop the unused `graphBuilder: GraphBuilderBlock` parameter;
  update `AstBlockProcessor.kt:101` and `AstMemPosProcessor.kt:27`.
- `AstBlockProcessor.visitExpressionStatement` — an override whose whole body is `super`. Delete.

`Graph.parentGBB`, also currently unused, is **not** deleted — commit 3 gives it a job.
`visitMemberReference`, named in the assessment, is already gone.

### Commit 3 — one identity per node, assigned at creation (item 2)

**Rendered identity becomes a serial from a counter.** The counter lives on the root
`GraphBuilderBlock` and is reached through the block tree, so it is per-`AstReader.process` run and
therefore deterministic — a JVM-global counter would make snapshots depend on test execution order.

`GraphBuilder.kt` (`GraphBuilderBlock`):

```kotlin
private var serialCounter = 0
/** Serials come from the root block, so every node in one exported document has a distinct one. */
fun nextSerial(): Int = parent?.nextSerial() ?: serialCounter++
val serial = nextSerial()   // replaces localId, the subgraph's own id
```

Declaration order is the one footgun here: Kotlin runs property initialisers top to bottom, and
`returnNode` and `parameterNodes` create nodes, so `serialCounter` must be declared above them.
`val graph = Graph(this)` is safe anywhere, since it only stores the reference.

`Graph.kt` — `createGraphNode` passes `parentGBB.nextSerial()` into the node.
`getNodesSortedByExtId()` becomes `getNodes()` returning insertion order, which is creation order:
deterministic, and more readable in a snapshot than hash order.

`GraphNode.kt` — `abstract class GraphNode(base: Base, val serial: Int)`, threaded through
`createNode` and the ten subclasses. A constructor parameter rather than a `var` set afterwards,
so a node cannot exist without one. It does not go on `Base`, because `Base` is built at the call
sites in `AstBlockProcessor` before the graph ever sees it.

**Lookup keys compare components instead of a hashed number.**

`GraphNodeId.kt`:

```kotlin
/** The components identity is decided on. Compared directly, never folded into a number. */
protected open fun key(): List<Any?> = listOf(label, stack)
override fun hashCode() = key().hashCode()
override fun equals(other: Any?) = other is GraphNodeId && other.key() == key()
```

`JNodeId.kt` overrides `key()` as `listOf(name.toString(), memPos)` — still without the stack, and
now with a comment saying why: a read of `x` has to find the `x` declared elsewhere, and the
rendered identity of each occurrence is the serial, not this. `name.toString()` rather than
`name.hashCode()`: javac `Name`s are interned per compilation `Context` so the behaviour is the
same today, and the text does not depend on that staying true. `MemPos` has no `equals`, so it
compares by identity — which is what is wanted, and what the old `memPos.hashCode()` already did.

A consequence worth noting: `listOf(label, stack)` can never equal `listOf(name, memPos)`, so the
accidental cross-type equality between a `GraphNodeId` and a `JNodeId` becomes structurally
impossible rather than merely unlikely.

`getExtId()` and `getIntId()` are deleted from `GraphNodeId` and `JNodeId`. (`IdentifierId` and
`MethodId` keep their own `getIntId()` — those are item 3's problem, not this one.)

`MermaidExporter.kt` — `getNodeStr` emits `n${node.serial}[...]`, `processMethod` emits
`subgraph b${method.serial}[...]` and iterates `method.graph.getNodes()`. The `n`/`b` prefixes keep
node and subgraph ids from ever being confused for each other in the Mermaid source, and make the
snapshots greppable.

`AppTest.kt` — the three regexes that match ids (`edgeLabels`, `assertNoSelfEdges`) go from
`-?\d+` to `n\d+`. Add one new suite-wide invariant next to the existing two:

```kotlin
/**
 * Two nodes rendered under one id are drawn as a single box, so the diagram claims a value flows
 * somewhere it does not. This is what identity-by-hash could always do and identity-by-counter
 * cannot; the assertion is here to notice if a derived id is ever reintroduced.
 */
private fun assertNoDuplicateNodeIds(testDir: String, graph: List<String>)
```

It reads only the declaration lines (`^\s*n\d+\[...]:::TYPE$`, no `-->`), since the exporter
legitimately repeats a node's string on every edge line.

---

## Files

| File | Change |
|---|---|
| `app/build.gradle` | toolchain 21, kotlin-test 1.9.25 |
| `.github/workflows/gradle.yml` | JDK 17 → 21 |
| `app/src/main/kotlin/codeflow/graph/GraphNodeId.kt` | component `key()`, drop both hash ids |
| `app/src/main/kotlin/codeflow/java/ids/JNodeId.kt` | `key()` override |
| `app/src/main/kotlin/codeflow/graph/GraphNode.kt` | `serial` constructor param; drop `Assignment` |
| `app/src/main/kotlin/codeflow/graph/Graph.kt` | assign serials; `getNodes()` in creation order |
| `app/src/main/kotlin/codeflow/graph/GraphBuilder.kt` | serial generator; `localId` → `serial` |
| `app/src/main/kotlin/codeflow/MermaidExporter.kt` | render `n`/`b` serial ids |
| `app/src/main/kotlin/codeflow/java/processors/GlobalContext.kt` | drop unused params |
| `app/src/main/kotlin/.../AstProcessor.kt`, `AstBlockProcessor.kt`, `AstMemPosProcessor.kt` | call-site updates |
| `app/src/test/kotlin/codeflow/AppTest.kt` | id regexes; `assertNoDuplicateNodeIds` |
| `app/src/test/resources/*/truth.md` | rewritten by commit 3 only |

---

## Verification

**Commits 1 and 2** — `./gradlew test --rerun-tasks`, 19 green, and `git diff --stat` shows no
`truth.md` touched. That is the whole check: either commit changing a snapshot means it did
something it was not supposed to.

**Commit 3** — every snapshot changes, so passing tests prove nothing on their own. The ids and the
line order are meant to change; the *structure* is not.

Compare old against new as a normalized multiset, per fixture, with a throwaway script in the
scratchpad that reads the old file from `git show HEAD:app/src/test/resources/<fixture>/truth.md`:

- edge lines → `srcLabel:srcTYPE -> dstLabel:dstTYPE`
- declaration lines → `label:TYPE`
- strip ids (bare digits in the old format, `n<serial>` in the new), sort, `diff`

A fixture whose two multisets are identical is verified. A fixture that differs is a place where
the old hash was merging two distinct nodes into one box — **read it, confirm the two nodes really
are distinct in the source, and record which fixture and which nodes in the commit message.** Do
not regenerate and move on: `ternary/truth.md` was written from a buggy run and passed happily
while encoding a graph with a branch missing, and is the standing proof that a green golden file is
not evidence of correctness.

It is a legitimate outcome for no fixture to differ — the fixtures are small, and a 64-bit-ish hash
needs volume to collide. In that case the real input is where to look.

**End to end** — the Fineract file the earlier session used is still at
`/private/tmp/claude-501/-Users-guistiebler-Documents-Projetos-fineract/b2dc6db1-dd22-41d0-b53e-f2e3e7ac0870/scratchpad/real-fineract`,
with its 1,181-line output alongside as `real-fineract-graph.md`.

```
./gradlew installDist
./app/build/install/app/bin/app <that dir> > /tmp/.../after.md
```

Confirm it still completes, then apply the same normalizer to `real-fineract-graph.md` and the new
output. Two things to check in the result:

1. The guarded-vs-unguarded division asymmetry is still visible: the loop-body division reaches
   `periodAmortization` through a `ternary` fed by `==`, and the post-loop one reaches it through
   `divide` with nothing on the path. This is the finding the tool exists to produce; it has to
   survive the change.
2. Whether the node count went **up**. On input this size a rendered-id collision is plausible, and
   an increase is the direct evidence that boxes which should have been separate were being merged.
   Worth naming in the commit message if it happens.
