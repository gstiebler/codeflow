# What porting codemap's test coverage found

Companion to `port-codemap-test-coverage.md`, which is the plan. This is what actually came out of
working it: what was wrong, how it was found, and what each fix moved. Kept because two of the five
bugs here were invisible to the assertions that were supposed to cover them, and that is worth
remembering the next time a green suite is offered as evidence.

Method: map each of codemap's 50 C++ fixtures to its Java equivalent, run it through the built CLI,
and read the graph rather than the exit code. Every finding below was reproduced that way before
anything was changed.

## The build did not run on the JDK it pinned

`app/build.gradle` pinned `jvmToolchain(21)` and the workflow set up JDK 21, but the wrapper pinned
Gradle 8.0, which cannot *run* on Java 21 — Groovy fails to compile the settings file with
`Unsupported class file major version 65`. Gradle 8.5 was the first release with Java 21 support.

CI had been red on `main` since d604cb8, failing in eight seconds before a single test ran. Fixed by
raising the wrapper to 8.10.2. Nothing else here could be verified until it was.

## Six bugs, all silent

### 1. A static field was not one variable

`Counter.total` logged `Variable not found: JNodeId=(name: 'Counter')` — swallowed, stderr only,
graph still printed — and degraded to an opaque `EXTERNAL` node. Two separate reads collapsed onto
one box, and a write between them reached neither. A bare `total` inside `Counter` worked, so the
two spellings of one variable produced two unconnected halves of a graph.

The cause is structural: a static belongs to its class, not to any instance, so there was no `MemPos`
to hang it on, and the block-parent chain cannot stand in — a method that writes a static is a
*sibling* of the one that reads it, not an ancestor. Classes now get a memory position of their own,
which makes a static the same kind of thing as an instance field, found the same way.

Only for fields these sources declare. `System.out` is a static field too, and resolves just as
confidently; tracking it would draw a variable standing for an object we know nothing about.
Telling those apart needed a question nothing could ask before — every question was about a *tree*,
and a tree is by construction from these sources — so `Symbols.isDeclaredInSources` was added,
recording which elements the compilation units actually declare.

From codemap's `static_member` and `global_var`.

### 2. `x = x + 1` read the value it was about to write

Found while fixing the above: `total = total + by` stayed broken for its own reason.

The assignment target was created before the right-hand side was evaluated, which registered it as
the current value of the variable under the same key, so the `x` in the expression found the node
about to be written rather than the one holding the old value. The graph drew a cycle — `x -> + -> x`
on one box — orphaned the previous value, and cut the literal behind it off from everything
downstream.

`x += 1` was always correct, because a compound assignment evaluates the variable first. So the two
spellings of one statement drew different graphs, and only the less common one was right.

Not from codemap — its `for_loop` fixture uses the compound form.

### 3. A call with no written receiver ran on nothing

An unqualified `record()` inside a method of `Gauge` is `this.record()`, and `super.m()` runs on the
object the caller is running on. Only the receiver *written at the call site* was consulted, and
neither form writes one, so both were inlined with no owner: every field the callee wrote landed
somewhere nobody could look up, and every field it read came back as a fresh value with nothing
flowing in.

One call level deep hid the first half, because `gauge.record()` names its receiver. Two levels did
not: `gauge.sample()` calling `record()` left the caller's later read of `gauge.reading` on the
value from before the call.

A receiver that is *present but untracked* still resolves to nothing, deliberately. Falling back to
the enclosing instance there would file the callee's fields on the caller's object — a different
object drawn as the same one.

From codemap's `deep_method`, whose golden carries the write through to the caller's read.

### 4. Field initializers and initializer blocks never run

Not yet fixed. `class Outer { Inner in = new Inner(); int n = 5; Outer() { n = n + 1; } }` leaves
`n = n + 1` reading an `n` with nothing flowing in, and `o.in.v` comes out as a bare `EXTERNAL` dead
end — the object was never constructed, so the chain dies there. Instance blocks (`{ y = 7; }`) and
static blocks (`static { x = 42; }`) are skipped the same way. Enum constants with constructor
arguments are the same bug wearing a different hat: the enum constructor never runs, so `MyEnum.FIRST`
goes `EXTERNAL` and a getter returns a field with nothing behind it.

From codemap's `constructor_chain` member-init lists and its `enum`.

### 5. `switch` as a statement is not modelled

Not yet fixed, and worse than it looks. The selector's read is *invisible*: `b` in `switch (b)` has
zero outgoing edges, where codemap's `switch.dot` has `b -> ==` three times plus fall-through from
case 2 into case 3. `visitSwitchExpression` exists; there is no `visitSwitch`, and a statement is not
covered by the `MODELLED_EXPRESSIONS` gate, so it fails the way CLAUDE.md warns statements fail —
somewhere else, later, blaming a line that is not at fault.

### 6. Nothing joins at a branch

Not yet fixed, and the largest of these by some way.

codemap emits an explicit `If` merge node — `shape=invtriangle` — at every join, fed by the condition
and both candidate values. In `nested_if.dot`, `int c = a` reads the merge output, so `c` traces back
to all of `3`, `7`, `9`, `cond1` and `cond2`.

codeflow keeps the last write and drops the rest, with nothing to indicate it. `if1/truth.md` records
it in the repository already: `c` gets only `b = 13`, never the pre-if `b`. The same holds for
`switch`, for `try`/`catch`/`finally` — `b` gets only the `finally` value, and the `try` and `catch`
writes dangle with no edge out — and for object aliasing, where `if (…) p = i1; else p = i2; int a =
p.m;` reaches only `i2`'s field.

This is a design change rather than a repair, which is why it is last: it moves every branching
golden, and it needs an answer for what a merge means for a variable written in only one branch —
the pre-branch value is one of the two things flowing in, and codemap's answer is to make that
explicit rather than to pick.

From codemap's `nested_if`, `switch`, `if_pointer` and `if_member`.

## Two lessons about the assertions

**A behaviour test can pass on a graph that is wrong.** `anInheritedMethodIsInlinedHoweverItIsReached`
asserted `offset -> +`, and that edge was drawn either way: with the bug, `super.shift` read a *fresh*
`offset` with nothing flowing into it, which still has an edge to the operator. Same picture, asserting
a value that never arrives. An edge assertion says two labels are adjacent; it says nothing about
where the value came from. The fix was to add a `reaches()` assertion from the literal that is
supposed to arrive — reachability walks ids, so it cannot be satisfied by a lookalike node.

**The structural snapshot check is blind to identity.** CLAUDE.md's procedure for a moved golden —
normalise both to sorted multisets of `label:TYPE` nodes and `label:TYPE -> label:TYPE` edges, diff
those — reported all forty goldens moved by fix 2 as *structurally identical*, correctly: only the
serials shifted. But it could not have seen fix 2 at all. The cyclic graph and the correct one carry
the same two label-stripped edges, `x:VARIABLE -> +:BIN_OP` and `+:BIN_OP -> x:VARIABLE`; what
changed is *which* `x` node each end lands on. The two checks are complementary, and neither is
sufficient: the multiset diff catches nodes and edges appearing or vanishing, and only an id-walking
assertion catches an edge being rewired between same-labelled nodes.

By contrast, fix 3 moved exactly one golden and the multiset diff named it in one line —
`-node offset:VARIABLE`, two nodes becoming one — which is the case that procedure is good at.

## Deliberately not ported

**Polymorphic dispatch** — `polymorphism`, `polymorphism2`, `polymorphism3`, codemap's headline
fixtures and the whole of its README. codemap tracks the object through the pointer and dispatches to
the concrete class. codeflow refuses to guess, and `abstractMethod` is an existing test asserting the
opaque `EXTERNAL` path. Worth noting that the machinery is already there: at `Handler h = new
HandlerA(); h.process(40)`, `h` is an `OBJ_VARIABLE` carrying `HandlerA`'s `MemPos`, so the concrete
class *is* known at that call site. Whether to use it is a reversal of a stated decision, not a gap,
and belongs in its own plan with its own argument.

**No Java equivalent, or already covered** — `destructor`, `operator_overload`, `typedef`, `pointer`,
`reference`, `namespace`, `char_str`. `header_only_func` is `abstractMethod`.

## Already correct, now asserted

Six constructs codemap pins down that codeflow got right and nothing tested: early and multiple
returns converging on the RETURN node, a field declared two classes up the chain, an inherited method
reached three ways, a write through an alias in a nested block, a value carried through a generic
holder, and a callee in a subdirectory in another package. These landed first, on purpose — they are
the regression net for the fixes above, which move the same code.

## Not worth taking

codemap's test harness. `TestsUtil.baseTest` diffs a generated `.dot` against a committed one
line-for-line, which certifies unchanged rather than correct — the same weakness CLAUDE.md already
names about our own goldens, with none of the behaviour assertions alongside. `GraphCompare` does
id-insensitive structural matching and would have been the interesting half, but nothing calls it.

One idea is worth taking: codemap tags every node with `startinglines=<line>` and asserts it in the
goldens. codeflow carries no source position into any exporter, so the viewer cannot answer "which
line is this?".
