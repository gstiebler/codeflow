# Adding support for a Java construct

Moved verbatim from CLAUDE.md, which now keeps only the rules. This is the reasoning behind them.

# Adding support for a Java construct

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

Casts are modelled since 2026-11-22 (`Lowering.visitTypeCast`, fixture `cast`), and they split by
what the cast does. A **reference** cast `(Square) shape` hands back the operand's own `Val`: it is
the same object, and anything else leaves `((Square) shape).edges` reading a field of no object and
`square.sides()` with nothing to dispatch on — an `UnOp` drops the objects a `Val` carries. A
**primitive** cast `(int) ratio` converts (`(int) 7.9` is 7), so it is a `UnOp` labelled `to int`,
or the graph would say the variable holds the double. The split is syntax (`PrimitiveTypeTree`), so
it needs no symbol lookup. The cast's type is not narrowed into dispatch: a `(Square)` on an object
of unknown type does not make it a `Square`, because a cast that would fail at run time is exactly
what a reader is checking for. With casts in, every value-producing `Tree.Kind` is modelled; the
gap left is the record deconstruction pattern (`instanceof P(int x)`, `case P(int x)`), which the
`unsupported` fixture now holds.

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

