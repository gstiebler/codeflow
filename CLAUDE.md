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
./gradlew run --args="path/to/java/dir --from Report#total"   # root it somewhere other than main

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

`AstReader.process` parses, attributes, then:

1. `AstProcessor` — records each method under the `ExecutableElement` it declares, and each class
   under its own. A method with no body is skipped: which implementation a call to an abstract or
   interface method reaches is decided at run time by the receiver's class, and picking one would be
   a guess drawn as fact, so the call takes the opaque `EXTERNAL` path instead.
2. `ir.Lowering` — javac trees to instructions, **once per method**. Names resolved, overloads
   selected, primitives decided; no `MemPos`, no ids, no edges.
3. `ir.IrGraphBuilder` — instructions to graph, **once per call site**, starting from the entry
   point.

The split between 2 and 3 is the one to preserve. What a name means is a question about the source
and has one answer per method; which box a value is drawn as is a question about a *call site*,
since a callee is inlined at every one. Answering both in one walk is what the tree walker did, and
it is why four satellite scanners existed to re-walk the same tree asking a different question —
and why one of them had to call back into the builder behind a memo, so that *asking* what object a
call returned did not *inline* the callee a second time. `Frame` in `IrGraphBuilder` is one
invocation: it holds the block, the object the method runs on, and the value each instruction
produced, which is both answers at once.

Which method is the root is a decision, not a detail: the diagram is whatever that one method
reaches, so on a corpus with several candidates the choice is the whole diagram.
`AstReader.selectEntry` makes it. `--from Class#method` names any method the sources declare —
without it the root is the first `main` by source path. `GlobalContext.sourceMethods` sorts by path
so the same input gives the same answer twice, and `AstReader` prints the one it took and the ones
it did not to stderr. Silence there reads as "this is the codebase" when it is one of four.

`main` is a default rather than a requirement because most Java has none: a service, a controller or
a library is entered from a caller outside the corpus, and refusing to start anywhere else excluded
most of the tool's subject matter. There is still exactly one root per run — `docs/if-written-again.md`
§7 wants every public method to be a root, which waits on method summaries, since without them each
root re-inlines everything it reaches at every call site.

An exporter then renders the root `GraphBuilderBlock` and its `calledMethods` recursively. There are
four, chosen by flag, and all four walk the same tree — a construct is supported once `Lowering`
and `IrGraphBuilder` model it, not once an exporter mentions it:

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

A `Call` opens a nested `GraphBuilderBlock` and a nested `Frame`, and reads the callee's
instructions again, rather than summarising a method once. `PosStack` — a stack of `file:pos` — is
what keeps the same variable in two different invocations apart. There is no depth limit; what stops
a recursive method is `Frame.isBeingInlined`, which walks up the parent frames comparing the
declaration javac resolved. A method with no body to inline (outside the analysed sources, or found
that way) becomes a single opaque `EXTERNAL` node instead: arguments and receiver flow in, the result
flows out.

Two questions get asked of the same call site — what value it produced, and which object that value
*is*. `Frame.Value` is both, so reading the instruction once answers both; the tree walker had to
memoise each `new X(...)` and each invocation to stop the second question inlining the callee a
second time. The object comes from the callee's own `return` (`GraphBuilderBlock.returnedMemPos`);
giving the result a fresh empty `MemPos` instead is what made `Money.of(...).getAmount()` resolve to
nothing, and a factory followed by a getter is most of a real codebase.

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
`Frame.read` and `Frame.write` consult the owning `MemPos` before the block — and they are about
fields only, since locals are resolved by then. `MemPos` has no
`equals`, so two instances are two objects, which is what identity should mean here.

### A local resolves to its definition, and a branch joins with a phi

A use of a local is not an instruction. `Lowering` keeps a map from the declaration javac resolved
to the value that variable currently holds, so `base + bonus` lowers to `binOp + 0 3` — naming the
parameter and the multiply, not naming a variable twice and leaving "which write was that" to be
settled by whoever draws the graph. Parameters are definitions too (`Param`), which is what makes
the resolution total: every use in a body resolves to an instruction.

`visitIf` and `visitSwitch` are where the map forks. Both branches are lowered from the same definitions, and at the
join each variable the two paths disagree about gets a `Phi` taking the value from each — drawn as
one box carrying the variable's name. That box is why `c = b` after `if (…) { b = 13; }` reaches
both 13 and whatever `b` held before, where a single mutable slot per variable gave it only the
branch walked last. `bothPathsOfABranchReachAUseAfterIt` is the assertion; `if1/truth.md` is what it
looked like without one.

A branch that cannot fall out of its own bottom contributes nothing to the join —
`completesNormally` decides, and errs towards yes, since merging a value that cannot arrive is the
lesser wrong.

A loop is the same idea with the phi at the *header*. `Lowering.loop` emits one for every variable
the loop assigns before the body is lowered, so a use inside the body names one instruction whichever
iteration produced the value, and the value the body leaves behind is added to that phi afterwards
(`Phi.addPath`). It is also what the variable holds after the loop, since a loop is left from its
header. That back edge is the **one place an instruction names a value produced later in the list** —
which is why a `Val` is an index rather than a reference, why `Frame.execute` drains `Run.backEdges`
after the run, and why the forward-reference sweep in `LoweringTest` names `Phi` as its exception.

A `switch` statement is the same join with one path per arm, plus two things an `if` does not have:
falling out of the bottom of an arm is a path into the next one (so `case 3:` below a `break`-less
`case 2:` starts from the two joined), and with no `default` the values from above the `switch` reach
the bottom unchanged. `everyArmOfASwitchStatementReachesAUseBelowIt` is the assertion.

A `try` is the same join from the other direction: a handler runs because the `try` did *not*
finish, so both reach the line below, and a handler starts from the definitions before the `try`
joined with the ones after it, since a throw can land there from anywhere inside. `finally` is
lowered after the join, because it runs on every path.
`aTryAndItsHandlerBothReachAUseAfterThem` is the assertion.

A phi nothing reads is left on the diagram rather than pruned. It says the variable held one of
these values at that point, which is true; dropping it would need a dead-value pass and a renumbering
of every `Val`, and there is no evidence yet from real input that the noise is worth that.

`i++` is a write as well as an operator: after it the variable holds what the operator produced.
Without that the counter a loop condition tests came from nowhere, and `counter++; int after =
counter;` drew `after` taking the value from before the increment.

### A name with no value: which kind decides

When a *field* read finds no node, `unassigned` splits on what javac says the name is. A **field** becomes a
value with nothing flowing into it — a field nothing has assigned yet holds its default, and reading
one is ordinary Java, so that is what the diagram should say. So does an **enum constant**, whose
declaration *is* the value. Anything else is the analysis having lost the
name, and that still fails loudly with a file and a line.

A **local** never reaches there at all: a use resolves to its definition while the tree is still in
hand, so `Lowering` is where a local with nothing reaching it fails, and the position in the message
is the read itself. Do not widen either gate. Turning the failure into a value would draw every name
codeflow has lost as one arriving from nowhere, which is indistinguishable from a real one — the
silent wrongness the gate exists to prevent, with the loud failure removed.
`aLocalWithNoReachingDefinitionFailsWhereItIsRead` and `aLocalWithNoValueStillFails` guard it.

## Adding support for a Java construct

`Lowering`'s `scan` is a gate: any `ExpressionTree` whose kind is not in `MODELLED_EXPRESSIONS`
becomes an `Unmodelled` instruction labelled with the kind and carrying `file:line:col`, drawn as an
`UNMODELLED` node with its operands flowing in and its value flowing out. This exists because
`TreeScanner`'s default — scan the children, return one of their results — is a *fabricated edge*
for an expression: `!flag` comes back as the node for `flag`, so the operator vanishes and the
graph claims something the code does not do. Two real bugs (the dropped ternary branch, vanished
unary operators) came from exactly that.

It used to throw, and the cost was out of proportion to the gap: one `(int)` cast on a reachable
path produced **zero bytes of output for the entire corpus**. The principle is unchanged — a gap
must never be drawn as a flow — but codeflow already had an honest rendering for "something here I
cannot see inside", and a cast is not more dangerous than `java.util`. `UNMODELLED` is its own node
type rather than `EXTERNAL` because the two say different things: `EXTERNAL` is a limit of the
*sources*, this is a limit of *codeflow*, and what it hides is code sitting in the corpus that the
diagram is not showing. Each one is also reported on stderr (`codeflow: N constructs not modelled`,
deduplicated, since a method is inlined once per call site) and makes the process exit non-zero —
the document still goes to stdout in full.

The failure that stays hard is the one that really is the analysis having lost something: a local
read with no reaching definition (`unassigned`). See "A name with no value" above.

`TYPE_KINDS` is the companion set to `MODELLED_EXPRESSIONS`. javac makes `PrimitiveTypeTree` and
friends subclasses of its expression type, so `is ExpressionTree` says yes to the `int` of
`(int) x` and it arrives at the gate looking like a value; drawn as one it is a node on the diagram
that nothing in the program corresponds to. Types produce no value and are skipped.

So, to add a construct: write the visitor in `Lowering`, emit an instruction, then add its
`Tree.Kind` to `MODELLED_EXPRESSIONS`. Never widen that set without a visitor behind it. A new
*instruction* needs a branch in `Frame.draw`, which is a `when` over the sealed `Insn` — so
forgetting one is a compile error rather than a missing box.

**The gate covers expressions only.** A *statement* codeflow does not model is not caught here: it
declares nothing, and the failure surfaces further down as a read of a name with no node, blaming a
line that is not the one at fault. The enhanced `for` and the `catch` parameter were both found
that way, so a construct that binds a name needs a visitor even when it produces no value —
`visitEnhancedForLoop`, `visitCatch`, `bindPattern`, `caseLabel`. All of them emit a `Bind`, which
carries an `Identity` saying which object the name stands for: the value's own, for a pattern that
names what it matched; a fresh one, for a loop element or a caught exception; none, for a lambda
parameter a caller not visible from here fills in. Getting that wrong files one object's fields
under another object's name, and the diagram that comes out is complete, readable and about the
wrong thing.

`MODELLED_STATEMENTS` is the statement half of the gate and currently catches nothing — it lists
every statement kind Java has. It stays as a tripwire for the next kind added to the language, and
because scanning through an unknown *statement* is not the fabricated edge that scanning through an
unknown expression is.

A statement can also fail the other way round — reading a value nobody notices is missing. `switch`
used as a statement had no visitor at all, so its selector was scanned, given a node, and left with
no edge out of it: the value deciding the whole branch drawn as unused. Nothing declared a name, so
nothing failed. The same shape hid **anything a class declares outside a method body** — field
initializers, instance initializer blocks, an enum constant's constructor arguments — all of which
now come from `Lowering.lowerInitializers` and `Lowering.lowerEnumConstant`. The initializers run
from two places in `Frame`, because the case that matters most has no constructor to hang them on: a
class declaring none still runs its field initializers on every `new`, so `construct` runs them in
the caller's block rather than drawing a box for a constructor nobody wrote. When there *is* a
constructor they run in its block, unless it starts with `this(...)` — the constructor delegated to
runs them, and running them at both ends of the chain draws every initializer twice.

Two related rules:

- Use `evaluate(tree, ctx)` — not `tree.accept(...)` — for anything that needs a *value*. It routes
  through `scan`, so the gate cannot be bypassed, and it fails loudly when an expression produces
  nothing.
- Put `ctx.location(tree)` on every instruction — `Insn` takes `source` as a required constructor
  parameter, and `GraphNode.Base` takes one too, so neither can exist without a position. The first
  question about any failure, and about any box on a diagram, is which line of which file. Lowering
  is per method and each method has its own `ProcessorContext`, so this no longer has the trap it
  used to: the tree walker inlined a callee with the *caller's* context in hand, and asking that one
  for a line number gave a real position naming the wrong file.

Operator labels go through `binaryOperatorLabel` / `unaryOperatorLabel` / `compoundAssignmentLabel`,
which map symbols that are also Mermaid syntax to words (`/` → `div`, `|` → `bitOr`, `&` → `bitAnd`,
and `?:` → `ternary`). A raw symbol corrupts the diagram rather than just looking odd.

## Testing

`AppTest.kt` has three kinds of assertion, and the mix is deliberate:

- **Golden files** (`app/src/test/resources/<fixture>/truth.md`) — 50 of them. They certify
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

Two suites sit alongside `AppTest` and assert on something a rendered document cannot show:

- `LoweringTest.kt` — the instruction list itself, as text, for one method or swept over every
  fixture. What a method *means*, before anything has decided how to draw it.
- `IrGraphBuilderTest.kt` — the graph as `label:TYPE` nodes and edges, for the few cases where the
  node *type* is the claim. While both builders existed this was the port's differential harness:
  every fixture built both ways and compared as multisets, with each disagreement asserted by name.
  The comparison went with the tree walker; what is left is the behaviour it found.

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
