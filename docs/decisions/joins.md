# Locals, branches and joins

Moved verbatim from CLAUDE.md, which now keeps only the rules. This is the reasoning behind them.

## A local resolves to its definition, and a branch joins with a phi

A use of a local is not an instruction. `Lowering` keeps a map from the declaration javac resolved
to the value that variable currently holds, so `base + bonus` lowers to `binOp + 0 3` — naming the
parameter and the multiply, not naming a variable twice and leaving "which write was that" to be
settled by whoever draws the graph. Parameters are definitions too (`Param`), which is what makes
the resolution total: every use in a body resolves to an instruction.

`visitIf` and `visitSwitch` are where the map forks. Both branches are lowered from the same definitions, and at the
join each variable the two paths disagree about gets a `Phi` taking the value from each. That box is
why `c = b` after `if (…) { b = 13; }` reaches both 13 and whatever `b` held before, where a single
mutable slot per variable gave it only the branch walked last.
`bothPathsOfABranchReachAUseAfterIt` is the assertion; `if1/snapshot.md` is what it looked like without
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
in `MermaidExporter` and the viewer's `theme.ts` so one graph does not change meaning between the two
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

## A name with no value: which kind decides

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

