# Attributed, and asked rather than guessed

Moved verbatim from CLAUDE.md, which now keeps only the rules. This is the reasoning behind them.

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

