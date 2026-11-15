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
T f(p0, p1, …) { T result = call<f>(p0, p1, …); return result; }   // void: the call, no return
```

`call<f>` is the instruction an unresolved call already is - a `Call` with no target, on no object -
so the builder draws it `EXTERNAL` and the summarizer reads it as an opaque call
(`ext:call<f>@…`), with no new instruction, node type or viewer change. A call to a boundary is drawn
the same way every time, `args → params → call<f> → result → RETURN f → caller`, which says what
went in without claiming anything about what happened inside.

**Lowering replaces the body**, rather than the builder skipping the call, because what a method
*means* is answered once per method and in Lowering. Everything after it - inlining per call site,
dispatch, `--summaries` - sees an ordinary method and needs no case of its own; a skip in the builder
would have had to be repeated in the summarizer, and two copies of that rule are two chances to
disagree. `boundary` is the fixture; `aBoundaryBodyIsNotRead` checks the body's literals are absent
with the flag and present without it.

**Boundary calls are chained in the order they ran.** Boundaries usually have side effects, so each
assumed implementation also reads an order token before its call and writes the call's value back
into it after: `boundaries → call<f> → boundaries → call<g> → …`. The token is one more input after
the parameters, and void boundaries thread it too. It is a static field in all but source: a holder
of its own in `Heap` (`boundaryOrderHolder`, listed in `staticHolders`), read and written by
`ReadBoundaryOrder` / `WriteBoundaryOrder` through the journaled write a static uses. So the static
rules give it, with no case of its own: a start with nothing flowing in; one chain across inlined
callees; a gated phi where `if` arms leave it different; no back edge in loops; and `SHARED` edges
between roots. The token is recognised by that holder, never by its name, so a field the sources call
`boundaries` stays their own. The summarizer ignores it, and `--summaries` is unchanged. **The
token's two edges at each call are `ORDER`**, not `FLOW` - `boundaries → call<f>` and
`call<f> → boundaries`, nothing else: drawn as flow, every argument of a boundary call *reached* every
later one through the token. The kind is decided where the edge is made, from the instruction - the
builder's `Run.orderReads` marks a `ReadBoundaryOrder`'s value, and a call taking one draws that input
as `ORDER`; `WriteBoundaryOrder` writes with `ORDER` - never from the box's name. `call<f> → result`
stays `FLOW`, and the token's gated join and `SHARED` edges keep their kinds: between two `ORDER`
edges they carry no data anywhere. Drawn dashed (`-. order .->`, purple `#7b1fa2`) in Mermaid and
the viewer. The tests' `reaches()` walks follow data only; `TestFixtures.ordered` follows the chain.
Fixture: `boundaryOrder`.
