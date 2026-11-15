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

