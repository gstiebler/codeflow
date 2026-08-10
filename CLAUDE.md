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

`scripts/delombok.sh <src> [out]` is the one preprocessing step, for a Lombok-annotated corpus.
codeflow parses with `-proc:none` and no classpath, so a `@Getter` accessor, a
`@RequiredArgsConstructor` constructor and `@Slf4j`'s `log` field do not exist in the attributed tree
and every use of one is drawn opaque; `delombok` writes them out as ordinary source, which is
codeflow's input contract with nothing added. Running Lombok *inside* codeflow instead would need a
jar on the processor path, `--add-opens` on the analysing JVM, and would let a processor found in the
analysed corpus execute during what the reader thinks is a read. The trade is that every `file:line`
codeflow prints afterwards names the delomboked copy. `docs/externals.md` measures both halves.

**Two JDK numbers, and they are not interchangeable.** codeflow parses with the *running* JDK's
javac (`ToolProvider.getSystemJavaCompiler()`), so the JDK is an input to the output and not just to
the build. `app/build.gradle` therefore compiles to bytecode **21** — `jvmToolchain(21)`, because
Kotlin 1.9 cannot target 25 and fails with "Inconsistent JVM-target compatibility" — and runs
`test` and `run` on **25** through a `javaLauncher` override (`def runtimeJdk = 25`). Bytecode 21
runs on 25 unchanged, so the two are free to differ.

Runtime 25 is not a preference. On javac 21 the whole test suite passes and real input does not:
attributing a `yield` whose type javac never worked out trips an internal assertion in `Attr`
(`Attr$1.visitYield`), and every root in Fineract's largest module produced zero bytes. javac 25
does not have that bug, and every golden is byte-identical across the two — which is what says
this is a fix and not a rewrite. What the tests assert has to be what a reader gets, so the suite
runs on the javac the tool is pointed at.

The installed CLI (`./gradlew installDist`, then `./app/build/install/app/bin/app <dir>`) cannot be
pinned from the build — its start script takes whatever `JAVA_HOME` names — so set a 25 yourself:
`JAVA_HOME=$HOME/.sdkman/candidates/java/25.0.4-tem` (or `$(/usr/libexec/java_home -v 25)`). Older
than 21 fails outright with `UnsupportedClassVersionError`; 21 through 24 run but carry the javac
bug above, which is the worse failure because it is silent about being one.

## Architecture

`AstReader.process` parses, attributes, then:

1. `AstProcessor` — records each method under the `ExecutableElement` it declares, and each class
   under its own. A method with no body is skipped: there is nothing to inline. Which implementation
   a call to an abstract or interface method reaches is decided at run time by the receiver's class,
   and that class is now *asked* rather than guessed — see "Dispatch is on the object" below. A
   receiver that names no object still takes the opaque `EXTERNAL` path.
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

**Mermaid needs the same rule, and for a reason that is easy to miss.** Which subgraph a node
belongs to is not decided by where it is declared: Mermaid works it out from every statement naming
the node, and the *deeper* claim wins. So an edge written inside a callee's subgraph pulls its target
in, and `MermaidExporter` used to write each edge in the block of its **source** — which for a
callee's RETURN feeding the caller's variable is the callee. `int b = classify(a, 10)` drew `b`
*inside the classify box*, and `funcCall`'s `y` inside `methodA`. Every arrow was correct and the
value sat in the wrong method, with nothing on the page to suggest it: a box here means a method, so
that is a diagram confidently and readably wrong about where a value lives.

`MermaidExporter.placeEdges` writes each edge in the innermost block enclosing **both** endpoints.
Naming a node from an *enclosing* block is harmless and is left alone — that is where an argument
edge is written, and the declaration below still wins — so only the edges pointing outwards move,
and only as far as they must. `assertNoNodeIsMentionedBelowItsOwnBlock` is the guard, swept over
every fixture.

Moving an edge moves its **link index**, which is the trap next door: `linkStyle` numbers links
globally across the whole flowchart in declaration order, so anything that changes where an edge is
written renumbers every edge after it. A stale index is silent — it paints a condition's grey onto
an unrelated arrow and leaves the real one black, and the document still renders.
`assertLinkStylesAddressTheEdgesTheyMean` derives the mapping from the finished document rather than
from `Links.count`, so it fails if the counter and the writing ever disagree.

### The interactive viewer

`HtmlExporter` substitutes the vendored libraries, `viewer.mjs`, and the JSON payload into
`template.html`. It is substitution only — all of the behaviour lives in `viewer.mjs`, which is
where changes go. The libraries are committed under `app/src/main/resources/viewer/`; `npm` only
records where they came from.

The viewer is two files and the boundary is the point. `model.mjs` is every decision about what is
on screen — `opening`, `tap`, `screen`, and the functions they rest on — as pure functions of
`(payload, revealed)`, with no DOM and no Cytoscape, so `node --test` imports it directly.
`viewer.mjs` is the renderer: it builds the graph, and on every click calls `tap` and writes what
`screen` returns. It decides nothing. Before the split, three decisions lived only inside a browser
— the opening set, read out of `cy.nodes('[type = "METHOD"]').isOrphan()`; the click, decided by
`node.descendants()`; and the render loop — so the half of the viewer that chooses what a reader
sees was covered only by whatever a Playwright test happened to click.

They are not linked by an import. The page is one self-contained file opened from `file://`, where
an inlined `<script type="module">` has nothing to resolve `./model.mjs` against, and `HtmlExporter`
is substitution only so it cannot strip an import either. So `model.mjs` is substituted into the
same module script directly above `viewer.mjs` and its exports are free identifiers there — which
is why `viewer.mjs` must never be loaded on its own.

`screen(payload, revealed)` returns the whole view as data: which leaves are showing, which of those
are names standing in for closed methods, the hidden-neighbour counts, and every node and edge of
the payload with a `display` and a `visible`. Every node, not only the visible ones — nothing is
ever removed from the graph, so a node that just left the screen needs `'none'` written onto it as
much as an arrival needs `'element'`.

`display` is `null` for every `METHOD` node rather than a string. That is the "never set `display`
on a `METHOD` node" rule expressed as data: as an `if` in the middle of the render loop it was a
rule no test could reach, and the failure it guards — a box whose only visible node is a grandchild
being hidden, leaving that grandchild nowhere to live — is reachable by clicking.

Cytoscape.js **compound nodes** are the method boundaries: a node's `parent` is its block, which is
what `subgraph` was doing in Mermaid. Layout is ELK `layered` with `elk.hierarchyHandling:
INCLUDE_CHILDREN` — without that it lays each container out independently and the boxes overlap.

The page opens with the entry method's own leaf children revealed, plus the *name* of each method it
calls and nothing else. This is not cosmetic: a callee's body is inlined at *every* call site, so
node count grows with call sites rather than source size, and showing everything at once is the wall
the viewer exists to avoid. What the name of a closed method is drawn as, and why it has to be drawn
at all, is "A closed method is drawn as its own result" below.

Visibility is derived, and that is the one rule to respect: **never set `display` on a `METHOD`
node.** A `Set` of revealed *leaf* ids is the only state. Cytoscape works out the rest — an edge
hides when either endpoint does, and a box hides when every descendant does, transitively. Setting
`display:none` on a box breaks the transitive case: a box whose only visible node is a grandchild
would be hidden, and the grandchild would have nowhere to live. That case is reachable by clicking
and is guarded by the browser test *draws a box whose only revealed nodes are grandchildren* —
clicking `X1` in the `funcCall` fixture reveals nodes inside the nested `methodC` boxes and none of
`methodB`'s own, so `methodB` is on screen through grandchildren alone.

Nothing is ever removed from the graph, so `cy.nodes().length` is always the payload's node count.

**A hidden neighbour is announced, or the reveal lies by omission.** Cytoscape drops an edge when
either endpoint goes, so a node with six hidden neighbours renders *identically* to a genuine source
or sink — a value arriving from somewhere invisible drawn as a value arriving from nowhere, which is
the silently-incomplete diagram arriving through the one door progressive reveal opens. So
`hiddenDegree(edges, revealed)` counts, per revealed node, the edges whose other end is off screen,
split by direction, and `badgeLabel` renders them into the node's caption: `total ↑2 ↓3`, `amount
↓3`, and nothing at all when the node is fully surrounded. An edge counts at a node only when
exactly one of its endpoints is revealed — both on screen is nothing missing, both off belongs to
neither — so the two cases are one test.

Counting *reachable* nodes instead is the tempting alternative and does not work: the graph is close
to connected and the walk is undirected, so every node would report approximately the payload's
size, adjacent nodes would count the same hundreds twice, and it is a walk per node per `apply()`.
`docs/superpowers/specs/2026-08-08-hidden-neighbour-counts-design.md` has that argument and the one
against ranking by the click's own harvest.

The badge lives in `data('badge')` and **`data('label')` stays the plain name**, because the
annotation is a property of the current view rather than of the value — every existing browser
assertion looks a node up by label, and so will the next one. The browser test reads
`n.style('label')` rather than the data for the matching reason: the badge being right in the data
while the stylesheet still points at `label` is a page that draws none of this, and reading the data
would call that green.

`neighbourhood(edges, startId, depth)` is the click behaviour: an undirected ball of radius
`REVEAL_DEPTH`. It is breadth-first on purpose — the walk is bounded, so a node first reached by a
long path would be recorded at the wrong distance and pruned early. Undirected on purpose too: for
`c = a + b`, clicking `a` shows `b`, because an operator drawn with one operand missing is worse
than one more node.

Clicks union into the revealed set and never subtract. Only a box click on an *open* box (which
removes its leaf *descendants* — a box holds boxes) or `R` takes anything away.

#### A closed method is drawn as its own result

**Dataflow is a forest, so following edges is not enough to navigate by.** Every fixture in the
suite is disconnected, and a call that passes no value connects nothing at all: `member`'s `main`
does `app.func1()` — no argument, no result — so not one edge crosses from `main` into `func1`. That
page opened on four nodes, clicking every one of them changed nothing, and 19 of the fixture's 23
leaves could not be put on screen by any sequence of clicks. Swept over all 63 fixtures, **none**
could be fully reached from any single starting node; the median best case was 71%. The opening view
worked only because it is built from *containment*, and containment was the relation nothing else
used. Choosing a different root would not have helped: the missing relation was the call structure,
not the starting point.

`withStubs(nodes, revealed)` makes it navigable. A box whose parent is open is offered as its
**RETURN node**, which every box has exactly one of — the method's name, or `<init>` for a
constructor. That node is the method's result, so a method you have not opened is drawn as the one
value it produces. Clicking that name reveals the method's own leaves and the names of the methods
it calls, and still walks the name's own edges like any other leaf. Folding puts the stub back
rather than removing the box, so there is a way in again.

It has to be a real leaf. **Cytoscape will not draw a compound node with no visible children**,
whatever `display` that parent is given — a spike confirmed `visible: false` and `0×0` on a box
forced to `element` — so an empty box cannot be a click target and the stub is the only thing that
puts a closed method on the page. This is the same fact the "never set `display` on a `METHOD` node"
rule comes from, used the other way round.

Two rules hold the recursion down, and each is one line with a test behind it. `openBoxes` decides
which boxes are open, and a box whose only revealed leaf is its own name **does not count as open**:
if it did, offering one callee's name would open that callee, which would offer its callees' names,
and one pass would unfold the whole call tree — which is also why one pass suffices, since no name
can produce another. And only a **closed** box is offered a name: an open one already has its RETURN
among the leaves the click revealed, and a box opened by following dataflow instead should show what
the walk reached and nothing more. That second rule is what keeps the off-by-one guard in *clicking
a node reveals its neighbourhood three hops out* meaningful — `methodA`'s RETURN is exactly four hops
from `x`, and offering names for open boxes would have put that label on screen for an unrelated
reason and made the assertion untestable.

**That first rule governs the derivation, and a click is not a derivation.** The two read alike and
were one sentence, and merging them is what left `member` unopenable. Its callee's name is a
`RETURN` node for a method that passes and returns nothing, so it has no edges at all, and the
neighbourhood walk returned it to itself: the page opened on 5 of 23 leaves with all five inert, and
the only way in was the thin box border around a node that looked like the obvious thing to press.
Clicking a name now opens its method, and cannot cascade, because a click happens once — the
newly-open method offers only the names directly inside it, each needing a press of its own.

The callees' names arrive with the click rather than being left to the next derivation, and that is
load-bearing rather than an optimisation: a box is drawn by its contents, so a method whose own
leaves are just its own name — one that only calls other methods — would otherwise open onto
nothing it could be drawn from. `deepField` is that shape. The sweep is what found it.

`openBoxes` is one exported function and not three spellings, because `withStubs` deciding what to
offer, `screen` deciding which of those are names rather than bodies, and the invariants sweep all
ask the same question. Three answers to "is this box open" is three chances for the viewer to
disagree with itself about what the reader is looking at — and `screen` in particular cannot use
"showing but not revealed", since a click on a name puts it in `revealed` while leaving the box it
stands for shut, and it is still a name.

`showing` is derived inside `screen()` on every call and never stored, so folding a box cannot
strand a stub that was added when it opened.

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

The rule holds at a **bare name** too, and that is where it was missing. A name javac could not find
— a statically imported constant, an enum constant in a `case` label whose enum is one module away —
comes back as a `ClassSymbol`, kind `CLASS`, and `visitIdentifier` believed it and went looking for a
local of that name. There is none, so the run died, and one `import static` was enough to produce
zero bytes for the whole corpus. So a name whose element is not `isVariable` is `Opaque`, the same
answer a call on an error-typed receiver gets. Pointing codeflow at one module of a multi-module
build is the *normal* way to point it at anything, so this is the common case rather than an exotic
one. `externalConstant` and `externalEnumSwitch` are the fixtures.

It holds at a **member** too, for the same reason and in the same words: javac reports a member it
could not find by handing back a `ClassSymbol` named `Owner.member`, kind `CLASS`, typed `ERROR`. A
class whose supertype is outside the corpus has *every* inherited field come back that way, so
`this.currency` in a subclass of a class one module over reached `unassigned` as a name javac had
resolved to something that is not a field — and that gate fails loudly, so the run died. The field is
real and one module away, which is a limit of the sources, so `visitMemberSelect` makes it `Opaque`.
The receiver flows in when there is one (`other.code`) and does not when there is not (`this.code`):
the object a field was read from is the only thing on the page saying where the value came from, and
dropping it would also leave the receiver reaching nothing. `inheritedField` is the fixture and
`aFieldInheritedFromOutsideTheSourcesIsExternalRatherThanAFailure` the assertion.

One name resolves the other way round, and it is the only place a *name* is trusted over an element:
a class whose supertype is outside the corpus has its signature attributed and its body not, so a
parameter's declaration resolves to a `PARAMETER` and every read of it resolves to nothing — two keys
for one variable in one method. `Lowering.use` falls back to the name **only when the read is the
unresolved side**, and only to a definition the same body emitted. A name javac *did* resolve to a
variable with nothing reaching it is still the analysis having lost it, and still fails loudly. Every
exception class whose base exception lives elsewhere has this shape;
`aParameterOfAnUnattributedClassStillResolvesToItsDeclaration` is the assertion.

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

### Dispatch is on the object, not on the declared type

javac resolves a call against the receiver's **declared** type, which is a correct answer to a
different question: `Base b = new Sub(); b.f(7)` comes back as `Base.f`. Inlining that drew the
superclass body with nothing on the page saying a choice had been made — the silently wrong graph,
in the one case no fixture covered.

`Frame.resolve` asks the receiver instead. `MemPos.type` is the class the object was constructed as
(`construct` takes it from the constructor's enclosing element, which is present even when the class
declares no constructor), and `GlobalContext.implementation` walks that class and its superclasses
for the method javac says overrides the declared one. This is only possible because the graph is
already context-sensitive: inlining is per call site and `Frame.invoke` binds each argument's
`Set<MemPos>` onto the callee's parameter, so a receiver is a concrete set here even when it is a
parameter several frames down.

**Only when the objects agree on one implementation.** Several is a real answer that needs a box
saying "one of these", which does not exist yet; picking one of them would be the guess this exists
to stop. So several takes `EXTERNAL`, and so does a receiver whose objects carry no type — a loop
element, a caught exception, an object from outside the corpus. A receiver with no tracked object at
all falls back to what javac resolved, which is what keeps every existing fixture still.

`Receiver.Super` exists for this and only this. `this.step()` must dispatch and `super.step()` must
not, or a base class calling `super` inlines the subclass override and recurses into itself — and
`isBeingInlined` would catch it and draw an opaque box, so the failure would read as a limit of the
sources. `overriddenSuper` and `templateMethod` are the two halves of that assertion. `static` and
`private` methods are not dispatched either, since neither can be overridden.

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

A *name*, though, is not one object: a variable points at a **set** of them. `if (c) p = i1; else p
= i2;` leaves `p` standing for either, so `Frame.Value.objects`, `Frame.owner`, `holderOf` and
`GlobalContext.objectsOf` are all sets, and `Frame.phi` unions what each path left behind. Holding
one position per variable could only answer with an arm, and which arm was whichever the walk
reached last — §1's wound moved into the alias model, where nothing on the diagram shows it
happened. A read through several holders looks the field up on each and merges them with the same
box a phi uses (`GraphBuilder.addJoin`); through one it still produces no box at all, which is what
keeps a value traceable from the write that set it to the read that uses it. A *write* through
several is a box on each, all taking the same value, since there is one write in the source and two
fields it could land in. `aFieldReadThroughEitherOfTwoObjectsFindsBoth` is the assertion.

A ternary and a `switch` expression are the same choice written as an expression, so they union too
— but only over `Select.alternatives`, not over every input. Which inputs the value can *be* is
something only the lowering knows: `c ? a : b` is never `c`, and `new Holder[]{a, b}` is an array
and neither of the objects in it. Unioning every input would file one object's fields under
another's. `aFieldReadThroughEitherArmOfATernaryFindsBoth` is the assertion.

A `switch` **expression** is that union plus everything the statement form needs, because an arm is a
block: it forks `definitions` per arm and joins at the bottom exactly as `visitIf` does, since arms
are mutually exclusive and one writing `note = 41` must not be what the next one reads. Its value can
arrive two ways — `case 1 -> base` produces one directly, and a block arm produces one per `yield`,
which `visitYield` pushes onto the arm the `yields` stack names. A stack, because an arm may hold a
`switch` expression of its own. An arm that **throws** contributes to neither the value nor the join,
and `completesNormally` is what says so. There is no fall-through to allow for: every arm of an
expression switch has to complete abruptly, so a `yield` is the only way out of the bottom of one.
None of this existed until Fineract asked for it — block arms were drawn as `UNMODELLED`, there was
no fork, and no fixture used `switch` as an expression at all, so the whole shape was unasserted.
`everyArmOfASwitchExpressionReachesItsResult`, `anArmThatThrowsIsNotAValueTheSwitchCanProduce` and
`oneArmOfASwitchExpressionDoesNotSeeWhatAnotherWrote` are the three assertions.

One edge is deliberately still open, and it is a *missing* possibility rather than an invented one:
a phi whose back edge has not been drawn yet cannot contribute objects, so one created inside a loop
and assigned to a variable declared above it is not in the set.

### A local resolves to its definition, and a branch joins with a phi

A use of a local is not an instruction. `Lowering` keeps a map from the declaration javac resolved
to the value that variable currently holds, so `base + bonus` lowers to `binOp + 0 3` — naming the
parameter and the multiply, not naming a variable twice and leaving "which write was that" to be
settled by whoever draws the graph. Parameters are definitions too (`Param`), which is what makes
the resolution total: every use in a body resolves to an instruction.

`visitIf` and `visitSwitch` are where the map forks. Both branches are lowered from the same definitions, and at the
join each variable the two paths disagree about gets a `Phi` taking the value from each. That box is
why `c = b` after `if (…) { b = 13; }` reaches both 13 and whatever `b` held before, where a single
mutable slot per variable gave it only the branch walked last.
`bothPathsOfABranchReachAUseAfterIt` is the assertion; `if1/truth.md` is what it looked like without
one.

**What chose is an input too.** A classic phi carries no condition: its operands are matched
positionally against a CFG's incoming edges, and the test lives in the block that branches. There is
no such graph here, so nothing recorded the association at all — the `==` of an `if` was drawn with
both operands flowing in and *no edge leaving it*, the value the whole branch turns on rendered as
though nothing consumed it, while the same choice written as `?:` had its condition flowing into the
`Select`. Two shapes for one program. `Phi.gate` (`ir.Gate`) closes that: the deciding value is an
input. SSA calls this a *gated* phi; codeflow's ternary node always was one.
`theConditionOfAnIfReachesEachValueItDecides` is the assertion.

**A gated join is two boxes.** The choosing takes the paths and the gate and is captioned with the
construct — `if`, `switch` — and what comes out of it is the *variable*, which is what a use below
the branch reads. That is the shape `?:` has already: `int g = c ? a : b` draws the `ternary` box
feeding `g`, so the choosing and the naming were never one node, and drawing the statement form as
a single box captioned `if` made two pictures of one program in the other direction. It also left
the variable's name off the page at the one point the reader most needs it — the join is the only
place `a` means something neither assignment to `a` says on its own. So `if1` draws
`== -->|if| if --> a --> d`, and the choosing is a `BIN_OP` like the `ternary` it is a spelling of.

Three rules the gate must not break. It is in `inputs` but **not** in `Phi.paths`, and the object
union runs over `paths` — a variable is never the thing that chose it, and unioning the inputs would
make `switch (name)` over a `String` point the joined variable at the selector's object. It is the
same discipline `Select.alternatives` enforces, in the other half of the model. The choosing is
keyed by construct *and* variable (`labelId("$label ${insn.name}", …)`) while captioned with the
construct alone (`GraphNode.Base(caption = …)`), since the key is `(position, label)` and one `if`
deciding two variables would otherwise key both boxes `if` at one position and merge them — the
derive-an-id-from-attributes hazard, arriving by the back door. And a pending back edge lands on
the choosing when there is one, because that is where the rest of the paths went.

Only an `if` and a `switch` statement are gated, because only they have the deciding value already
lowered when the join happens. A loop's condition is computed *from* its header phi and lowered
after it; an enhanced `for` has no condition at all; and which `throw` reached a handler is control
flow rather than any value. Those joins fall back to the variable's name, and the fixtures
`forLoop`, `enhancedFor`, `varargs`, `tryCatch` and `catchParameter` are the check that they stayed
that way. A `switch` is gated by its **selector**, not by each arm's `==`, so those comparisons still
have no outgoing edge — coarser than the truth, but naming them would need the case labels threaded
through to say which comparison belongs to which path.

A branch that cannot fall out of its own bottom contributes nothing to the join —
`completesNormally` decides, and errs towards yes, since merging a value that cannot arrive is the
lesser wrong. It is also why the arm names are built alongside the path list rather than assumed:
`if (x == null) return 0;` has one path, and it is the false one.

**A guarded `return` joins at the exit, since it is not at the join.** That branch leaves the
method, so it contributes nothing below the `if` — and for a guard clause that is the *whole* branch,
so no phi is built, and the comparison the method turns on had no edge leaving it at all. The same
wound, in the one place a variable's join could never look. `Lowering.finish` closes it: a
value-carrying `return` is recorded as an `Exit` rather than emitted, an enclosing `if` whose branch
cannot complete claims the exits that branch left, and after the body they fold newest-first into one
`Select` per guard — captioned `if`, the guarded value on one arm, everything below it on the other,
gated by the comparison. So `earlyReturn` draws `> -->|if| if`, and `recursion` draws the base case
and the recursive case as the two arms of `n == 0` rather than as two arrows arriving at `fact`.

It is a `Select` and not a `Phi` because a guarded exit is the same choice `return c ? a : b` writes
as an expression, and the result is not a variable — the RETURN node the method already has is what
the choosing feeds, so there is no second box to name. The fold reuses the ternary's drawing
unchanged.

Two things it deliberately does not do. A `return` two branches deep is claimed by the **innermost**
`if` alone, so the guard named is the one nearest the exit rather than the conjunction of every
condition enclosing it — coarser than the truth, and the alternative is an `and` node the source
never wrote. And the fold needs a value for the path that fell through, so it runs only when at most
one exit went unclaimed: several means the method leaves from places nothing here can order — every
arm of a `switch` returning, or a `try` and its handler each returning — and those stay as they were,
one `Return` each arriving unlabelled, which says "one of these" and stops there honestly. `if2` is
the fixture for both halves that do fold, `theGuardOfAnEarlyReturnReachesTheValueItDecides` for the
guard clause and `bothArmsOfAnIfThatLeavesTheMethodStillJoin` for the case where no path falls
through and the older exit is what the younger one is gated against.

**An edge says what it means, not just where it goes.** A gated join takes three things that are not
interchangeable — the value if the test held, the value if it did not, and the test — and three
identical arrows say only "one of these reached it", which is most of what the gate was added to
stop. So an edge is a `GraphNode.Edge` carrying an `EdgeKind`: `TRUE`, `FALSE`, `CONDITION`, or
`FLOW`, which is nearly every edge and renders unchanged. `Gate.arms` and `Select.arms` are where the
names come from, and they are a `Map<Val, String>` rather than a list parallel to the paths because
`join` collapses the reaching values with `distinct` — a value both branches leave behind gets no
arm at all, which is the honest answer. Only a `?:` and an `if` have arms: a two-case `switch`
expression also has two alternatives and a condition, and calling those true and false would be a
label the source never wrote.

All four exporters render the kind, and the colours (`#2e7d32`, `#c62828`, `#6a6a6a`) are repeated
in `MermaidExporter` and `viewer.mjs` so one graph does not change meaning between the two
renderings. The Mermaid side has a trap: `linkStyle` indexes links **globally across the whole
flowchart** in declaration order, so the index of an edge is only settled once every nested block
has been walked — hence `Links`, which counts during the recursion and emits the styles after it.

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

`receiverOf` asks it too, and has to: a receiver that names a type is `Receiver.TypeName`, and the
check for that asks javac what the tree resolved to — but a *primitive* type name has no `Element` to
answer with, so `boolean.class` fell past it and was evaluated as a value, and a type produces none,
so the run died. `enum JavaType { BOOLEAN(boolean.class), … }` is where it was found, and a table
mapping primitives to their wrappers is ordinary code. The kind is the only thing that can answer
there, which makes it the companion check to the one in `scan`. `classLiteral` is the fixture, and it
holds `String.class` beside `boolean.class` because the two have to come out the same.

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

- **Golden files** (`app/src/test/resources/<fixture>/truth.md`) — 64 of them. They certify
  *unchanged*, not *correct*. `ternary/truth.md` was once written from a buggy run and passed
  happily while encoding a graph with a branch missing. Treat a green golden file as evidence of
  nothing.
- **Behaviour tests** using `edgeLabels`, which reduces the graph to `(sourceLabel, targetLabel)`
  pairs and ignores ids. These are the assertions that can fail on a graph that has never been
  correct. **New behaviour needs one of these**, not just a regenerated snapshot.
- **Suite-wide invariants** run on every fixture: `assertNoSelfEdges`, `assertNoUnknownOperators`,
  `assertNoDuplicateNodeIds`, `assertNoNodeIsMentionedBelowItsOwnBlock`,
  `assertLinkStylesAddressTheEdgesTheyMean`. These read the rendered Mermaid — the last two check
  what Mermaid will *make* of it, which is not the same as what the graph says; the three in
  `InvariantsTest.kt` below read the graph against the IR.

The golden test also writes each fixture's graph as the interactive page, to
`app/src/test/resources/<fixture>/graph.html`, gitignored and rewritten on every run — `truth.md` is
the diagram as *text*, which is what a snapshot can compare, and the viewer is what the tool is
actually pointed at. Nothing asserts on it (`AppTest.writePage`), same as `ir.txt` below: it is there
to be opened. Each page inlines its own copy of the vendored libraries, so it is about two megabytes,
which is why they are not committed.

Snapshots are only written when missing or under `UPDATE_SNAPSHOTS=1`, so a regression cannot
overwrite its own expectation. When a change does move snapshots, verify them *structurally* rather
than reading diffs: normalise old (`git show HEAD:<path>`) and new to sorted multisets of
`label:TYPE` nodes and `label:TYPE -> label:TYPE` edges with ids stripped, and diff those. Anything
left over is a real change and needs explaining.

`app/build.gradle` sets `maxHeapSize = '2g'` on the test task because two suites sweep every fixture
directory, so a javac task per fixture is live at once and Gradle's 512m default ran out. It failed as
the *worker* dying after every test had already reported passing, which reads as infrastructure rather
than as anything about the suite — worth knowing before chasing it again after adding fixtures.

Three suites sit alongside `AppTest` and assert on something a rendered document cannot show:

- `LoweringTest.kt` — the instruction list itself, as text, for one method or swept over every
  fixture. What a method *means*, before anything has decided how to draw it. The sweep also writes
  each fixture's whole IR to `app/src/test/resources/<fixture>/ir.txt`, gitignored and rewritten on
  every run: nothing asserts on it, it is there so that `App.java`, `ir.txt` and `truth.md` can be
  read side by side as source, meaning and diagram for one fixture. A stale one would be worse than
  none, which is why it is not a snapshot.
- `IrGraphBuilderTest.kt` — the graph as `label:TYPE` nodes and edges, for the few cases where the
  node *type* is the claim. While both builders existed this was the port's differential harness:
  every fixture built both ways and compared as multisets, with each disagreement asserted by name.
  The comparison went with the tree walker; what is left is the behaviour it found.
- `InvariantsTest.kt` — §9's three properties, swept over every fixture directory: every literal is
  drawn once per drawing of its method and reaches something; every box drawn for an instruction that
  consumes a value shows it arriving; every instruction draws a box (or, for a call, opens a block)
  and every box sits at a position some instruction this run read occupies. These need no expected
  output and no fixture author to have anticipated the failure, which is why they hold on a corpus
  nobody has read. They compare the graph against **the IR it was drawn from**, joined on the
  position both sides carry, so the harness builds `IrGraphBuilder` directly and keeps it: `bodyOf`
  is memoised, so the instruction list it hands back afterwards is the one that was drawn.

  Two things about that join. `ProcessorContext.location` records the *start* offset only, so both
  `+` of `a + b + c` carry one position — which is why the checks ask whether a box exists for an
  instruction and never how many sit at a position. The exception is a literal, the one instruction
  whose mapping onto boxes is exactly one, where the count is the assertion and is what catches a
  body drawn twice. `drawnLabel` is a `when` over the sealed `Insn` for the same reason `Frame.draw`
  is one, and the four kinds that draw no box of their own each say why on the spot.

  Each of the three was confirmed against a *wrong* implementation rather than an absent one:
  drawing a literal twice, dropping the assignment edge into a written variable, dropping a unary
  operator's operand edge, letting a unary operator vanish into its operand, and giving a box the
  enclosing method's position instead of its own. The middle three fail one test each, which is what
  says the three are not restating each other.

`app/src/test/resources/codemap` and `ls` are named by no test of their own — `InvariantsTest`'s
sweep is the only thing that reaches them; `codemap/truth.md` is stale and still in the pre-serial id
format.

### The viewer's tests

Split by what they can actually catch:

- `app/src/test/js/unit/` (`npm test`) — the pure functions, imported straight from `model.mjs`.
  `neighbourhood` is tested here: the depth bound, that a node reachable both ways is recorded at
  the *short* distance so nodes past it stay in range, and that it terminates on a cycle.

  The corpus sweeps live here too, over the `graph.json` the golden suite writes beside each
  fixture (gitignored, rewritten every run, like `ir.txt`). `invariants.test.mjs` asserts that a box
  offered to the reader has something showing inside it, that no `METHOD` node is given a display,
  and that the badges' hidden counts add up to the edges actually crossing the screen edge.
  `reachability.test.mjs` asserts the one that matters: from the opening view, clicking what is on
  screen to a fixpoint reaches every leaf of every fixture. That property was swept by hand once,
  when stubs were added, and was false again by the time anything re-checked it — eight fixtures
  short, `member` worst at 5 of 23.

  `corpus.mjs` fails when it finds fewer than 60 payloads rather than sweeping an empty list — a
  checkout that has not run `./gradlew test` has none, and a sweep over nothing passes every
  property it is given. Same trap as a negative browser assertion, one layer up.
- `app/src/test/js/browser/` (`npm run test:browser`) — Playwright against pages built from real
  fixtures. `global-setup.mjs` runs `gradlew run --args="<fixture> --html"` per fixture, with an
  **absolute** path: the `run` task's working directory is `app/`, so a repo-relative one resolves
  to `app/app/...` and `Files.walk` throws. Two are built, and which question each can answer
  matters — `member` is the fixture where following dataflow finds nothing, and `funcCall` is the
  only one nested deeply enough to tell a stub from an open box, since `member`'s `getMemberX` has
  nothing in it *but* its RETURN node and the two look identical there. A depth test written against
  that box passed against an implementation that opened one level too far.

Two traps, both of which produced a green run on a broken page:

- The browser publishes a global for every element `id`. The container is `#graph`, **not** `#cy`,
  because with `id="cy"` a check for `window.cy` finds the div and passes before the graph exists.
  The guard is `typeof window.cy?.nodes === 'function'` for the same reason.
- A negative assertion (`not.toContain`, `count === 0`) passes trivially when the feature does
  nothing at all. Each one is paired with a positive assertion that fails in that case. Confirm a
  new test fails against a *wrong* implementation, not just an absent one — revealing every node is
  the mutation to try.
