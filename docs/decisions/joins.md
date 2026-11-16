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
`FLOW`, which is nearly every edge and renders unchanged. Three more are not about joins: `SHARED`
(a static leaving one root for another), `ORDER` (the boundary-order token into and out of a
boundary call - see `calls-and-dispatch.md`) and `EVENT` (a root's event starting a node nothing
else feeds - see below), all dashed, since none is a value within one run. `Gate.arms` and `Select.arms` are where the
names come from, and they are a `Map<Val, String>` rather than a list parallel to the paths because
`join` collapses the reaching values with `distinct` — a value both branches leave behind gets no
arm at all, which is the honest answer. Only a `?:` and an `if` have arms: a two-case `switch`
expression also has two alternatives and a condition, and calling those true and false would be a
label the source never wrote.

All four exporters render the kind, and the colours (`#2e7d32`, `#c62828`, `#6a6a6a`, `ORDER`'s
`#7b1fa2` and `EVENT`'s `#00897b`) are repeated
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


## Fields join too, and the builder does it

A local is joined by `Lowering`, which knows by name which variables two paths disagree about. A
field cannot be: `this.x` and `other.x` are the same place or not depending on which objects the
receivers are *at this call site*, and only `IrGraphBuilder` knows that. So the builder used to walk
the flat instruction list and let the last write win - `if (c) this.x = a; else this.x = b; return
this.x;` drew `a -> x` as a dead end and only `b -> x -> RETURN`. Silently wrong, in the
direction this project is arranged against.

`Lowering` marks the paths and the builder does the joining. Three instructions, none of them in
`MODELLED_EXPRESSIONS` (they are not expressions) and none drawing a box of their own:

- `FieldFork` opens a fork. The builder pushes a mark on its field-write journal.
- `FieldArm(fork)` ends one path: the writes since the mark are kept as that arm's, then **undone**,
  so the next path starts from the state the fork began with.
- `FieldJoin(fork, arms, withEntry, gate, armNames, closes)` merges the named arms, plus the state
  from before the fork if `withEntry`.

`Lowering` emits them where its `definitions` forks: `if` (arms true/false, no entry, gated by the
condition, an absent `else` is an empty arm), a `switch` statement (one fall-through join before
each arm reached by falling, ungated; the bottom join takes the arms that leave the `switch` and has
the entry when there is no `default`; an arm that falls into the next is not one of them), a `switch`
expression (no fall-through, no entry), a `try` with handlers (each handler is entered through a
join of the before and after states, and the end joins every path, ungated), and a loop (one arm
plus the entry - zero iterations - ungated, `do` included). A `try` with only a `finally` is one
path and emits nothing. The end of a construct is `closes`: it pops the fork, and the merged state is
written *after* that so an enclosing fork journals it as its own arm's write.

**Only field writes are journaled, and only while a fork is open.** Locals and `Bind` also pass
through `record`, but they already have a phi, and journaling them would draw the join twice. Outside
a fork the journal is off, so straight-line code costs nothing. The state is one map per run, so a
callee's forks open and close inside its caller's and nest with no extra bookkeeping.

**Merging draws like `Frame.phi`.** One distinct value: no box, the field just holds it. Several: a
gated join draws the choosing (`addSelection`, keyed `"<gate label> <field>"`, captioned with the
gate label) taking each value with TRUE/FALSE where exactly one arm brought it, and the gate value
as a CONDITION; the field's own box (`addJoin`) comes out of it, over the union of the objects. An
ungated join (`try`, loop, fall-through) is FLOW edges into the variable. An arm that did not write
contributes the entry value; **with no entry value and a single writing arm the arm's value is
carried across** - a field nothing wrote holds its default, which the diagram does not draw at
every join.

**The boundary-order token also splits at an `if`, the join's mirror.** The gated join takes what
each arm left on `TRUE`/`FALSE` and the condition as `CONDITION`; the split, drawn at the fork, takes
the token and the condition and hands the token *into* each arm on `TRUE`/`FALSE`. It is drawn only
for the token and only for `if` (the `FieldFork` carries the gate and arm names for it), lazily on
the first read of the token in an arm that has not written it, so an `if` whose arms never touch the
token draws neither split nor join. At the join, an arm that did not take the token arrives with the
split's output on its own edge instead of the entry value; a fork without a split inside an arm (a
loop, `try`, `switch`) takes what the enclosing split hands that arm, so one arm can take the token
twice - at its first call and where the inner fork's join meets the path past it. See
`calls-and-dispatch.md`.

Fields join over **all** arms, including those that return, throw or `break`. That is deliberate and
over-approximate: the object outlives the method, so what a guard clause wrote is what a caller
reads afterwards, and dropping such an arm is the same silent wrongness the other way. Locals do
the opposite (`completesNormally`) because a local dies with the frame.

Owner-less fields (a receiver nothing can track) are looked up through the block-parent chain, not
the object's fields. Their key carries the block, a write in another block (a nested callee) is
dropped at the arm's end, and `readFrom` only falls back to the chain for them: otherwise an *undone*
owned write would still be found through it. `GraphBuilderBlock.rebindVariable` puts a variable back.

Known gaps, deliberately not closed:

- **No back edge for fields in a loop body.** A local gets a header phi taking what the body left
  for the next iteration; a field read at the top of the body sees only the state before the loop,
  so `x = x + 1` in a loop does not flow into itself. The join *after* the loop is right.
- **`?:`, `&&` and `||` are not forked.** Their arms are expressions and a field write inside one is
  rare; each write lands in sequence, last wins.
- **A write through a receiver with several holders is a strong update on each.** `(c ? a : b).x =
  v` overwrites `x` on both objects rather than possibly on either.

**What it costs on real input.** On the Fineract smoke corpus the document grew 497 MB → 742 MB,
nodes +28%, edges 2.2x, and the run took about twice as long. Most of the edges come from one method. `Memo.checkDependencyChangedAndUpdate`
does `if (!isSame) dependencyHashCodes = new int[...]`. Every inlined call allocates a new array,
and the join keeps the old array as well as the new one, so after k calls on one `Memo` the field
can be any of k arrays. Each `dependencyHashCodes.length` read is then one box per array, which is
quadratic: about 1.5M `length → length` edges. That is the correct answer under per-call-site
allocation (an earlier array really can still be there), and the old strong update hid it.
Naming objects by allocation site, or drawing each body once (plan B), is what would shrink it.

`bothArmsOfAFieldWriteReachAReadBelowTheIf` and the tests beside it in `AppTest` are the assertions
(fixtures `fieldIf`, `fieldSwitch`, `fieldTry`, `fieldLoop`); `LoweringTest` pins the markers.
