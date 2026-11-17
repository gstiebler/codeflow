# Calls and dispatch

Moved verbatim from CLAUDE.md, which now keeps only the rules. This is the reasoning behind them.

## Calls are inlined per call site

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

## Dispatch is on the object, not on the declared type

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


## Boundaries: methods whose bodies are not read

Some methods live outside what a reader wants to see - another team's package, a framework, a
gateway - even when their source is in the directory. `--boundary <Class>` (repeatable) names such a
class, matched as `--from` matches its class part: simple or qualified name, on the `Element`, never
on a file name. A name that matches no class fails, listing the candidates, because a misspelt
boundary would quietly read the very body it was meant to hide.

Every method declared on that class (constructors and nested classes are not included) is given one
**assumed implementation** in place of its body:

```java
T f(p0, p1, …) { T result = call f(p0, p1, …); return result; }   // void: the call, no return
```

`call f` is the instruction an unresolved call already is - a `Call` with no target, on no object -
so the builder draws it `EXTERNAL` and the summarizer reads it as an opaque call
(`ext:call f@…`), with no new instruction. A call to a boundary is drawn
the same way every time, `args → params → call f → result → RETURN f → caller`, which says what
went in without claiming anything about what happened inside.

**Lowering replaces the body**, rather than the builder skipping the call, because what a method
*means* is answered once per method and in Lowering. Everything after it - inlining per call site,
dispatch, `--summaries` - sees an ordinary method and needs no case of its own; a skip in the builder
would have had to be repeated in the summarizer, and two copies of that rule are two chances to
disagree. `boundary` is the fixture; `aBoundaryBodyIsNotRead` checks the body's literals are absent
with the flag and present without it.

**A boundary may be an interface.** A service usually reaches what is outside it through an injected,
interface-typed field (`loanAssembler.assemble(id)`): nobody `new`ed the receiver, so dispatch takes
javac's static answer, the interface method - which has no body and was never registered, so the call
was a bare `EXTERNAL` and `--boundary` changed nothing. So an abstract or interface method declared
directly on a boundary class is registered too (`GlobalContext.markBoundaries`, once the boundaries
are known, since `--boundary` is matched against the registered classes) and lowered as the assumed
implementation, which never reads a body. Bodyless methods of every other class stay unregistered.

**No dispatch past a boundary.** A call javac resolves to a method declared on a boundary class *is*
that boundary method, whatever objects the receiver holds (`Dispatch.boundary`, first in
`Dispatch.resolve`; the summarizer asks it too before recording a `dispatch` gap). A boundary is the
declared API, not one implementation, and its bodies are not read anyway: dispatching would draw
`StoreImpl.save`'s body where the receiver happens to be a known `new StoreImpl()` and the boundary
where it is not - one call site meaning two things depending on how much the analysis could see.
Matched on the declaring class, as `isBoundary` is, so a method inherited from a supertype that is not
itself a boundary is not one. Fixture `boundaryInterface`: an interface `Store`, its one
implementation, and two roots, one through an injected field and one through `new StoreImpl()`.

Roots and boundaries for a real corpus are a list someone edits, so a command-line argument `@path`
is replaced by the arguments in that file (javac's convention: each line split on whitespace, blank
lines and `#` lines skipped, not recursive). A missing file fails, naming it.

**Boundary calls are chained in the order they ran.** Boundaries usually have side effects, so each
assumed implementation also reads an order token before its call and writes the call's value back
into it after: `boundaries → call f → boundaries → call g → …`. The token is one more input after
the parameters, and void boundaries thread it too. It is a static field in all but source: a holder
of its own in `Heap` (`boundaryOrderHolder`, listed in `staticHolders`), read and written by
`ReadBoundaryOrder` / `WriteBoundaryOrder` through the journaled write a static uses. So the static
rules give it, with no case of its own: a start with nothing flowing in; one chain across inlined
callees; a gated phi where `if` arms leave it different; no back edge in loops; and `SHARED` edges
between roots. The token is recognised by that holder, never by its name, so a field the sources call
`boundaries` stays their own. The summarizer ignores it, and `--summaries` is unchanged. **The
token's two edges at each call are `ORDER`**, not `FLOW` - `boundaries → call f` and
`call f → boundaries`, nothing else: drawn as flow, every argument of a boundary call *reached* every
later one through the token. The kind is decided where the edge is made, from the instruction - the
builder's `Run.orderReads` marks a `ReadBoundaryOrder`'s value, and a call taking one draws that input
as `ORDER`; `WriteBoundaryOrder` writes with `ORDER` - never from the box's name. `call f → return`
stays `FLOW`, and the token's gated join and `SHARED` edges keep their kinds: between two `ORDER`
edges they carry no data anywhere. Drawn dashed (`-. order .->`, purple `#7b1fa2`) in Mermaid and
the viewer. The tests' `reaches()` walks follow data only; `TestFixtures.ordered` follows the chain.
Fixture: `boundaryOrder`.

**`--pure <Class>` is a boundary out of the order.** Not every collaborator has side effects: in
Fineract `command.dateFormat()`, finders and calculators sat in the chain beside `save`, hiding the
order that matters. A `--pure` class is a boundary in every other way - not read, one `call f` box,
no dispatch past it - but `assumeBoundary` emits no `ReadBoundaryOrder` / `WriteBoundaryOrder` for
it (`GlobalContext.isPure`). Per class, like `--boundary`: put a class there only when none of its
methods writes *or throws* - a validator's throw is an effect whose order matters. A class named by
both flags fails. Test: `aPureCallIsABoundaryCallOutOfTheOrder`.

**At an `if` the token splits.** Fanning one token out as ORDER into the first call of each arm said
the calls ran in order and not that only one arm runs - the two arms' calls looked like siblings,
both after `f`. So where an `if`'s arms take the token, one `if` box (`BIN_OP`, `addSelection`) takes
it - ORDER from `boundaries`, beside the condition as `CONDITION` - and hands it on as `TRUE` into the
then-arm's first token read and `FALSE` into the else-arm's. An arm that never takes it hands it
straight to the token's gated join (`if -->|false| if` for an `if` without `else`); an `if` whose arms
never take it draws no split at all, so the split appears exactly when the token's join does. A
nested `if` first in an arm takes the token on the enclosing split's edge for that arm and splits it
again on its own condition. `FieldFork` carries the `if`'s gate and arm names; the builder draws the
split lazily (`Split`, `IrGraphBuilder.orderToken`) on the first token read in an arm that nothing in
that arm has written yet - found by walking the open forks and asking the journal whether the
token's *holder* was written since each mark, never by a label. Only the token: ordinary values
reaching into an arm stay ungated, and `switch`, `?:` and loops still fan out. `reaches()` follows a
split's `TRUE`/`FALSE`, so the condition's operands reach each arm's first boundary call, which is
the same control dependence the gated join already draws. Fixtures: `boundary`, `boundaryBranch`.

**How it looks.** Every token box - start, writes, the gated join's value - is `NodeType.BOUNDARIES`,
filled in the ORDER purple (`classDef BOUNDARIES fill:#7B1FA240`, emitted only when one is drawn, so
documents without `--boundary` are unchanged; `PALETTE.BOUNDARIES` in the viewer). The gate itself
stays `BIN_OP`. A token is labelled `boundaries <root>` - the root's simple name, the same in every
inlined callee; the label only, its identity is untouched. A boundary call is `EXTERNAL`, labelled
`call f`, and carries a structured `lead = "call"` (`Call.lead`, set only by `assumeBoundary`, never
read from the text) that is drawn bold: a Mermaid markdown string `` "`**call** f`" `` (it renders
`<strong>` with HTML labels on or off, where `<b>` shows literally in Mermaid 10 without them; `_`
escaped so it is not italic), `<strong>` in the viewer (`captionParts`), plain in GraphML. An
outside call that merely is named `call` has no lead and stays plain. Several roots each get their
own chain, named after them and tied only by `SHARED` edges: fixture `boundaryRoots`.

**Each root has an entry event.** An entry point is something that happens - a request, a job,
`main` - so every root gets one `NodeType.EVENT` node, `event <root simple name>`, created first in
the root's block (the root's lowest serial; the builder's, not an instruction's, so Lowering,
`--summaries` and `SummaryParityTest` are unchanged). Once the root is built, and before `build(roots)`
adds `SHARED` edges, `IrGraphBuilder.startFromEvent` gives it an `EdgeKind.EVENT` edge to every node
under the root - its block and every inlined one - that has no incoming edge: literals, parameters,
`this`, the token's start, unwritten field reads, `UNMODELLED` nodes, a void method's `RETURN`. "No
incoming edge" is structural and judged within the entry point: a read that another root's `SHARED`
edge also reaches keeps its event edge, since that root may not have run. The kind is not `FLOW`,
so `reaches()` stays data-only, and not `ORDER`, which means "ran before". Teal: `classDef EVENT
fill:#00897B40` (always - every root has one), `-. event .->` with `#00897b`; `PALETTE.EVENT` and
the same stroke in the viewer. Spec: `docs/superpowers/specs/2026-11-15-entry-event-design.md`.
`InvariantsTest` holds that each root has exactly its one event and every other node has something
arriving.
