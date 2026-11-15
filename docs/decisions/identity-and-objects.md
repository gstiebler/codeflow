# Identity and objects

Moved verbatim from CLAUDE.md, which now keeps only the rules. This is the reasoning behind them.

## Identity vs. lookup — do not conflate these again

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

## MemPos is object identity

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

