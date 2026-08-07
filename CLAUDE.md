# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

codeflow reads Java source and emits a Mermaid dataflow graph: which value reaches which, through
which operators and calls. It is meant to be pointed at code nobody has read yet and be believed,
so **a silently wrong graph is worse than a failure** — a wrong diagram is readable, plausible, and
gives no sign anything is missing. Most of the design decisions below follow from that.

## Commands

```shell
./gradlew build                      # compile + test
./gradlew test --rerun-tasks         # tests, ignoring up-to-date checks
./gradlew test --tests '*ternary*'   # one test
UPDATE_SNAPSHOTS=1 ./gradlew test    # rewrite the golden files (see Testing)
./gradlew run --args="path/to/java/dir"
./gradlew run --args="path/to/java/dir --html"   # interactive page, to stdout

npm test                             # the viewer's pure functions, under node --test
npm run test:browser                 # the exported page, in a real browser
```

The JS suites need `npm install` once. `npm test` globs the files itself
(`app/src/test/js/unit/*.test.mjs`) because `node --test <dir>` tries to import the directory and
dies before running anything.

The toolchain is pinned to **JDK 21** (`app/build.gradle`). The installed CLI
(`./gradlew installDist`, then `./app/build/install/app/bin/app <dir>`) therefore needs a 21
runtime — if the shell's `java` is older it fails with `UnsupportedClassVersionError`, so set
`JAVA_HOME=$(/usr/libexec/java_home -v 21)`. The pin matters because codeflow parses with the
*running* JDK's javac (`ToolProvider.getSystemJavaCompiler()`), which makes the JDK an input to the
output, not just to the build.

## Architecture

`AstReader.process` parses, attributes, then makes two passes over the compilation units:

1. `AstProcessor` — records each method under the `ExecutableElement` it declares. A method with no
   body is skipped: which implementation a call to an abstract or interface method reaches is
   decided at run time by the receiver's class, and picking one would be a guess drawn as fact, so
   the call takes the opaque `EXTERNAL` path instead.
2. `AstBlockProcessor.invokeMethod` starting from `main` — walks statements and builds the graph.

Which `main` is a decision, not a detail: on a corpus with several, the choice is the whole diagram.
`GlobalContext.mainMethods` sorts by path so the same input gives the same answer twice, and
`AstReader` prints the one it took and the ones it did not to stderr. Silence there reads as "this
is the codebase" when it is one of four.

An exporter then renders the root `GraphBuilderBlock` and its `calledMethods` recursively. There are
four, chosen by flag, and all four walk the same tree — a construct is supported once
`AstBlockProcessor` models it, not once an exporter mentions it:

| Flag | Exporter | For |
|---|---|---|
| *(none)* | `MermaidExporter` | the default document |
| `--graphml` | `GraphmlExporter` | desktop editors (yEd, Cytoscape Desktop) |
| `--json` | `JsonExporter` | the viewer's payload, and what the tests assert on |
| `--html` | `HtmlExporter` | one self-contained interactive page |

Every exporter writes to **stdout**, so nothing else may. `logback.xml` pins its appender to
`System.err` for exactly this reason: a `ConsoleAppender` with no `<target>` defaults to stdout, and
debug lines interleaved into the document make it unparseable with no hint as to why.

`GraphmlExporter` nests a `<graph>` inside each block's `<node>` but declares **every edge at the
root**, since GraphML requires an edge to sit in a graph enclosing both endpoints and only the root
always qualifies. Plain GraphML carries no coordinates, so yEd opens it as a pile at the origin
until you run Layout → Hierarchical — that is the format, not a bug.

### The interactive viewer

`HtmlExporter` substitutes the vendored libraries, `viewer.mjs`, and the JSON payload into
`template.html`. It is substitution only — all of the behaviour lives in `viewer.mjs`, which is
where changes go. The libraries are committed under `app/src/main/resources/viewer/`; `npm` only
records where they came from.

Cytoscape.js **compound nodes** are the method boundaries: a node's `parent` is its block, which is
what `subgraph` was doing in Mermaid. Layout is ELK `layered` with `elk.hierarchyHandling:
INCLUDE_CHILDREN` — without that it lays each container out independently and the boxes overlap.

The page opens with the entry method's own leaf children revealed and nothing else — every callee
box is in the graph, drawn as nothing because none of its contents are showing. This is not
cosmetic: a callee's body is inlined at *every* call site, so node count grows with call sites
rather than source size, and showing everything at once is the wall the viewer exists to avoid.

Visibility is derived, and that is the one rule to respect: **never set `display` on a `METHOD`
node.** A `Set` of revealed *leaf* ids is the only state. Cytoscape works out the rest — an edge
hides when either endpoint does, and a box hides when every descendant does, transitively. Setting
`display:none` on a box breaks the transitive case: a box whose only visible node is a grandchild
would be hidden, and the grandchild would have nowhere to live. That case is reachable by clicking
and is guarded by the browser test *draws a box whose only revealed nodes are grandchildren* —
clicking `X1` in the `funcCall` fixture reveals nodes inside the nested `methodC` boxes and none of
`methodB`'s own, so `methodB` is on screen through grandchildren alone.

Nothing is ever removed from the graph, so `cy.nodes().length` is always the payload's node count.

`neighbourhood(edges, startId, depth)` is the click behaviour: an undirected ball of radius
`REVEAL_DEPTH`. It is breadth-first on purpose — the walk is bounded, so a node first reached by a
long path would be recorded at the wrong distance and pruned early. Undirected on purpose too: for
`c = a + b`, clicking `a` shows `b`, because an operator drawn with one operand missing is worse
than one more node.

Clicks union into the revealed set and never subtract. Only a box click (which removes its leaf
*descendants* — a box holds boxes) or `R` takes anything away.

### Attributed, and asked rather than guessed

`AstReader` calls `task.analyze()`, so javac's symbol table decides what every name means. Nothing
here matches type or method names as strings; a name is looked up by the `Element` it resolved to.

Attribution works on a bare directory with no classpath — this is the property the whole approach
rests on. `analyze()` returns without throwing on input that does not compile, errors go to the
`DiagnosticCollector`, and **a file full of errors does not blind the others**. What javac cannot
work out it *marks*: the type comes back `TypeKind.ERROR`, and a call on an error-typed receiver
yields an `Element` of kind `CLASS` where a `METHOD` was asked for.

Hence the one rule: **an `Element` is believed only when its kind is the kind expected at that
site** — `symbols.element(tree, ElementKind.METHOD)`, not `symbols.element(tree)`. Anything else is
treated as outside the analysed sources and takes the opaque `EXTERNAL` path. Taking the wrong-kind
element at face value would resolve a call to a class, which is the silently-wrong graph again.

`Symbols` (`java/Symbols.kt`) is how the answers get to the processors. `Trees.getElement` needs a
`TreePath`, but every processor is a `TreeScanner` holding a bare `Tree` and `invokeMethod`
re-enters a callee's body with no path at all. Since `analyze()` annotates the *same* tree objects
`parse()` returned, one `TreePathScanner` pass records everything into an `IdentityHashMap` up
front — identity, because two structurally equal expressions at two call sites are two references.

Attribution also **adds to the AST**: a class declaring no constructor gains one, and a constructor
not starting with `super(...)`/`this(...)` gains a `super()`. These carry real source positions, so
position cannot detect them — `Symbols.isWrittenInSource` asks `Elements.getOrigin`. They are
dropped because codeflow is a tool for reading source: graphing a constructor nobody wrote draws an
empty box on every `new` of such a class. The inserted `super()` resolves to a constructor outside
the sources (`java.lang.Object`) and contributes nothing.

`AstReader` prints `codeflow: N of M references unresolved` to **stderr** — stdout is the Mermaid
document. A count near zero on real input means the counter is measuring the wrong thing.

### Calls are inlined per call site

`visitMethodInvocation` builds a nested `GraphBuilderBlock` and recurses into the callee's body for
*every* call site, rather than summarising a method once. `PosStack` — a stack of `file:pos` — is
what keeps the same variable in two different invocations apart. There is no depth limit and no
in-progress set, so a recursive method has nothing stopping it. A method with no body to inline
(outside the analysed sources) becomes a single opaque `EXTERNAL` node instead: arguments and
receiver flow in, the result flows out.

Two questions get asked of the same call site — what value it produced, and which object that value
*is* — so `AstBlockProcessor` caches each `new X(...)` and each invocation in `evaluated`, keyed by
identity. Without the cache the callee is inlined twice and every box in it is drawn twice over.
The object comes from the callee's own `return` (`GraphBuilderBlock.returnedMemPos`); giving the
result a fresh empty `MemPos` instead is what made `Money.of(...).getAmount()` resolve to nothing,
and a factory followed by a getter is most of a real codebase.

An argument past the last declared parameter binds to the last one, which is what varargs means.
Any other count mismatch is the analysis having gone wrong and says so.

### Identity vs. lookup — do not conflate these again

Two different questions, deliberately answered by two different things:

| Question | Answered by | Granularity |
|---|---|---|
| "Which box on the diagram?" | `GraphNode.serial` | Per **occurrence** — `y = 1; y = y + 1` is two boxes |
| "Which variable is this?" | `GraphNodeId` / `JNodeId` | Per **variable** — every read of `x` must find the same one |

`serial` comes from a counter owned by the root `GraphBuilderBlock` (`nextSerial()`), handed out at
creation, per-run so snapshots stay deterministic. `JNodeId`'s key is `(element, memPos)` — *which
declaration*, in *which object*, since one field declaration lives at a different address in every
instance. It deliberately leaves the source position *out* so a read finds its declaration.

**Never derive a rendered id by hashing attributes.** That is what this used to do, and two
unrelated nodes whose hashes agreed were drawn as one box carrying every edge of both — a diagram
asserting flows that do not exist, with nothing to notice. `assertNoDuplicateNodeIds` guards this.

### MemPos is object identity

A `MemPos` stands for one object instance and owns the nodes for its fields. It is how
`this.field`, an implicit-`this` field read, and a field written in a constructor and read in
another method all find each other — the block-parent chain does not span sibling methods, so
`visitIdentifier` and `visitMemberSelect` consult the owning `MemPos` first. `MemPos` has no
`equals`, so two instances are two objects, which is what identity should mean here.

### A name with no value: field or local decides

When a read finds no node, `unassigned` splits on what javac says the name is. A **field** becomes a
value with nothing flowing into it — a field nothing has assigned yet holds its default, and reading
one is ordinary Java, so that is what the diagram should say. So does an **enum constant**, whose
declaration *is* the value. A **local or parameter** cannot be read before it is written, so finding
none means the analysis lost it, and that still fails loudly with a file and a line.

Do not widen this. Turning the local case into a value too would draw every name codeflow has lost
as one arriving from nowhere, which is indistinguishable from a real one — the silent wrongness the
gate exists to prevent, with the loud failure removed. `aLocalWithNoValueStillFails` guards it.

## Adding support for a Java construct

`AstBlockProcessor.scan` is a gate: any `ExpressionTree` whose kind is not in
`MODELLED_EXPRESSIONS` throws a `GraphException` naming the kind and `file:line:col`. This exists
because `TreeScanner`'s default — scan the children, return one of their results — is a *fabricated
edge* for an expression: `!flag` comes back as the node for `flag`, so the operator vanishes and
the graph claims something the code does not do. Two real bugs (the dropped ternary branch, vanished
unary operators) came from exactly that.

So, to add a construct: write the visitor, then add its `Tree.Kind` to `MODELLED_EXPRESSIONS`.
Never widen that set without a visitor behind it.

**The gate covers expressions only.** A *statement* codeflow does not model is not caught here: it
declares nothing, and the failure surfaces further down as a read of a name with no node, blaming a
line that is not the one at fault. The enhanced `for` and the `catch` parameter were both found
that way, so a construct that binds a name needs a visitor even when it produces no value —
`visitEnhancedForLoop`, `visitCatch`, `bindPattern`. A *pattern* on a `switch` used as a statement
is the one still missing, which is what the `unboundLocal` fixture is: `visitSwitch` models the
statement itself, but a `case String text ->` label binds nothing.

A statement can also fail the other way round — reading a value nobody notices is missing. `switch`
used as a statement had no visitor at all, so its selector was scanned, given a node, and left with
no edge out of it: the value deciding the whole branch drawn as unused. Nothing declared a name, so
nothing failed. The same shape hid **anything a class declares outside a method body** — field
initializers, instance initializer blocks, an enum constant's constructor arguments — all of which
now run from `runInstanceInitializers` and `enumConstant`. `runInstanceInitializers` is reached from
two places, because the case that matters most has no constructor to hang it on: a class declaring
none still runs its field initializers on every `new`, so `constructorNode` runs them in the
caller's block rather than drawing a box for a constructor nobody wrote.

Two related rules:

- Use `evaluate(tree, ctx)` — not `tree.accept(...)` — for anything that needs a *value*. It routes
  through `scan`, so the gate cannot be bypassed, and it fails loudly when an expression produces
  nothing.
- Put `ctx.location(tree)` in `GraphException` messages. The first question about any failure is
  which line of which file.

Operator labels go through `binaryOperatorLabel` / `unaryOperatorLabel` / `compoundAssignmentLabel`,
which map symbols that are also Mermaid syntax to words (`/` → `div`, `|` → `bitOr`, `&` → `bitAnd`,
and `?:` → `ternary`). A raw symbol corrupts the diagram rather than just looking odd.

## Testing

`AppTest.kt` has three kinds of assertion, and the mix is deliberate:

- **Golden files** (`app/src/test/resources/<fixture>/truth.md`) — 46 of them. They certify
  *unchanged*, not *correct*. `ternary/truth.md` was once written from a buggy run and passed
  happily while encoding a graph with a branch missing. Treat a green golden file as evidence of
  nothing.
- **Behaviour tests** using `edgeLabels`, which reduces the graph to `(sourceLabel, targetLabel)`
  pairs and ignores ids. These are the assertions that can fail on a graph that has never been
  correct. **New behaviour needs one of these**, not just a regenerated snapshot.
- **Suite-wide invariants** run on every fixture: `assertNoSelfEdges`, `assertNoUnknownOperators`,
  `assertNoDuplicateNodeIds`.

Snapshots are only written when missing or under `UPDATE_SNAPSHOTS=1`, so a regression cannot
overwrite its own expectation. When a change does move snapshots, verify them *structurally* rather
than reading diffs: normalise old (`git show HEAD:<path>`) and new to sorted multisets of
`label:TYPE` nodes and `label:TYPE -> label:TYPE` edges with ids stripped, and diff those. Anything
left over is a real change and needs explaining.

`app/src/test/resources/codemap` and `ls` have no test referencing them; `codemap/truth.md` is stale
and still in the pre-serial id format.

### The viewer's tests

Split by what they can actually catch:

- `app/src/test/js/unit/` (`npm test`) — the pure functions, imported straight from `viewer.mjs`.
  `neighbourhood` is tested here: the depth bound, that a node reachable both ways is recorded at
  the *short* distance so nodes past it stay in range, and that it terminates on a cycle.
- `app/src/test/js/browser/` (`npm run test:browser`) — Playwright against a page built from a real
  fixture. `global-setup.mjs` runs `gradlew run --args="<fixture> --html"` first, with an
  **absolute** fixture path: the `run` task's working directory is `app/`, so a repo-relative one
  resolves to `app/app/...` and `Files.walk` throws.

Two traps, both of which produced a green run on a broken page:

- The browser publishes a global for every element `id`. The container is `#graph`, **not** `#cy`,
  because with `id="cy"` a check for `window.cy` finds the div and passes before the graph exists.
  The guard is `typeof window.cy?.nodes === 'function'` for the same reason.
- A negative assertion (`not.toContain`, `count === 0`) passes trivially when the feature does
  nothing at all. Each one is paired with a positive assertion that fails in that case. Confirm a
  new test fails against a *wrong* implementation, not just an absent one — revealing every node is
  the mutation to try.
