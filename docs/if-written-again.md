# If codeflow were written again

Not a plan and not a list of bugs. This is the set of decisions that would be made differently
starting from an empty directory, with the reasoning and the evidence for each, so that the ones
worth acting on can be lifted out and the ones that are merely taste can be ignored.

Every claim below was reproduced against the built CLI at `8d1c947` before it was written down.

## What the rewrite has to keep

These are load-bearing, and a rewrite that lost them would be worse than what exists:

- **Ask javac, do not match strings.** `task.analyze()`, then a symbol believed only when its kind is
  the kind the call site expected. Everything about overloads, shadowing and same-named methods
  falls out of this for free, and nothing else gets it.
- **Silence is the failure mode to design against.** The `MODELLED_EXPRESSIONS` gate, the
  wrong-kind-becomes-`EXTERNAL` rule, the unresolved-reference count on stderr, the report of which
  `main` was chosen and which were not. The rewrite changes *how* some of these fail (see §5) but
  not that they must.
- **Identity assigned at creation, never derived by hashing attributes.** `serial`, and
  `GraphNodeId.key()` comparing components instead of folding them into a number.
- **The two-questions split** — `serial` for "which box", `JNodeId` for "which variable" — which is
  correct and was hard-won.
- **`reaches()`.** An edge assertion says two labels are adjacent; only a walk over ids can say a
  value actually arrived. The findings doc records a behaviour test that passed on a graph where the
  value never came.
- **The viewer's `neighbourhood`**: an undirected ball of bounded radius, revealed and never
  subtracted. It is independent of everything below and it is good.

## 1. A control-flow graph and SSA, instead of one mutable slot per variable

**Since done**, in `ir.Lowering`: a use resolves to its defining instruction, and `if`, `switch`,
loops and `try`/`catch` join with a `Phi`. The join an `if` or a `switch` statement makes is *gated*
— the condition is an input to it, and each path's edge says which arm it came in on — so the same
choice written as a statement and written as a ternary now come out as the same shape. The
diagnosis below is left in the past tense it was written in — the `if1` and `forLoop` goldens it
quotes have moved, and are now what this section asked for. What it names and this does not have:
`break` and `continue` as edges of their own (a `continue` mid-body does not reach the loop header),
labels, and `&&`/`||` as branches. The CFG also stayed implicit — a flat instruction list with phis,
rather than a graph of blocks, since nothing downstream asks which block an instruction is in.

Two joins are still ungated, and deliberately. A loop's condition is lowered *after* the header phi
and computed *from* it, so gating it would draw an edge back into the phi from a value the phi
produced; an enhanced `for` has no condition to draw at all. And for a `try`, which `throw` reached
the handler is control flow, not a value that exists anywhere — there is nothing on the page to make
an input.

One piece of residue: a `switch` statement's join is gated by the **selector**, so the per-arm `==`
comparisons the lowering emits still have no outgoing edge. That is the same shape §1 opened with,
in miniature — a computed value drawn as unused — and it will stay until an arm's edge can name the
comparison that admitted it rather than a string like `"true"`.

This is the largest change and the one that alters the most answers.

`Variable.lastNode` is a single mutable slot overwritten by whichever assignment the tree walk
reached last. There is no `visitIf`, so `TreeScanner`'s default walks the condition and then both
branches in source order. "Last write wins" therefore means "the textually last branch wins", which
is not an approximation of anything.

`if1/truth.md`, committed in the repository, is what that produces:

```java
int a = 5;
int b = a;
if (b == 7) { b = 13; } else { a = 17; }
final int c = b;
final int d = a;
```

`c` receives only `13`, and `d` receives only `17`. Both variables lost the value they hold on the
other path, and `5` reaches nothing past `b`. The diagram is complete, readable, and false in
exactly half of all executions, with nothing on it to say so — which is the failure the whole
project is organised against, occurring in its own smallest fixture.

Loops are the same wound. `for (int i = 0; i < 3; i++) total = total + i;` draws `total → +` and
`+ → total`, but the second `total` has no edge back into the `+`: there is no back edge, so the
accumulator's own contribution to itself is invisible. `postInc` is drawn with nothing flowing out
of it, and the `i` read in the body is the initial `0`. A reader following the arrows concludes
`out = 0 + 0`.

The fix is not a merge node bolted onto `if`. It is building the structure that makes merge nodes
fall out: **a CFG per method, and SSA over it**. A φ at a join *is* codemap's merge node, arrived at
by construction rather than by special case. What that buys, all from one mechanism:

- `if`, `switch`, `try`/`catch`/`finally` and the conditional operator join the same way.
- Loops get a header φ and a back edge, so an accumulator is drawn as an accumulator.
- `&&` and `||` become real branches. Today they are plain binary operators, so the graph claims
  both operands are always evaluated, which is the one thing short-circuiting guarantees is false.
- `unassigned`'s local-versus-field split mostly dissolves. SSA construction *computes* the
  definition reaching each use; it does not discover an absence at read time and then have to guess
  whether that absence is a default value or a lost variable.
- `break`, `continue`, labels and exceptional edges become work that exists, rather than work that
  is accidentally omitted.

Cost, stated honestly: it is the most expensive item here, it moves every branching golden, and it
puts an analysis-shaped stage in front of a codebase that currently has none.

## 2. Method summaries computed once; inlining as a view, not as the analysis

`visitMethodInvocation` re-walks the callee's body at every call site, with no depth limit and no
in-progress set. Two things follow.

**Recursion is a crash.** `int fact(int n) { ... return n * fact(n - 1); }` produces a
`StackOverflowError`, zero bytes of output, and a stack trace naming `AstBlockProcessor` rather than
a line of Java. It exits 1, so it is at least loud — but the message tells the reader nothing about
their own code, and a five-line factorial is not an exotic input.

**Node count scales with call sites rather than with source size.** The entire progressive-reveal
viewer — a payload format, a vendored Cytoscape, ELK layout, a revealed-set model, a browser test
suite — exists to make that survivable. That is a great deal of machinery paying interest on an
analysis decision.

Instead: analyse each method **once** into a summary — which parameters reach the return, which
reach which field write, which escape into which onward call — and compose summaries at call sites.
Recursion becomes a fixpoint over summaries and terminates. Expansion in the viewer becomes
instantiation of a summary at a call site, computed on demand, so inlining is what "open this box"
*means* rather than something the builder did a thousand times in advance.

The precision objection is real and has an answer. Per-call-site inlining exists so that `f(a)` and
`f(b)` are different objects with different fields. A summary parameterised over its inputs gives
the same result when instantiated; what changes is that it is computed once and rendered many times,
instead of computed many times.

## 3. Points-to as a named analysis, not `MemPos` accumulated along the way

`MemPos` is a points-to analysis that was never called one, and it carries the costs of being
unnamed:

- **A variable holds exactly one `MemPos`.** `if (c) p = i1; else p = i2; int a = p.m;` cannot be
  represented, only resolved to one arm — §1's wound reappearing in the alias model. This wants a
  *set* of allocation sites, which is also exactly what a φ over references needs.
- **It is global mutable state** (`GlobalContext.idToMemPos`) keyed by node id, so the alias model
  cannot be tested in isolation. Its only observable is the drawn graph, which is why bugs in it
  surfaced as strange edges several inferences away from the cause.
- **`AstMemPosProcessor.visitIdentifier` catches `Exception` and logs a warning.** The findings doc
  records what that cost: the static-field bug sat behind it as "swallowed, stderr only, graph still
  printed". This is the project's own stated principle being violated inside the project.
- Arrays have no element model, and objects from outside the sources have no summary.

Design it as what it is: an allocation-site abstraction, `Map<Value, Set<AllocSite>>`, computed as
its own pass over the IR, with its own unit tests that never render a diagram. Field reads and
writes go through it. Deep fields, aliasing, merged references and the object returned by a factory
then become one mechanism with one test surface.

**Status: the set is done; the separate pass is not.** A variable now points at a `Set<MemPos>`
(`Frame.Value.objects`, `Frame.owner`, `holderOf`, `GlobalContext.objectsOf`), a phi unions what
each path left behind, and a field read through several holders merges the field on each — the
`aliasBranch` fixture and `aFieldReadThroughEitherOfTwoObjectsFindsBoth`. A method with two returns
now hands the caller both objects rather than the first. `AstMemPosProcessor` and its swallowed
`Exception` went with the tree walker, so that bullet is moot. A ternary or `switch` expression over
references unions too, over `Select.alternatives` rather than over every input — `aliasTernary` and
`aFieldReadThroughEitherArmOfATernaryFindsBoth`. Not done: it is still computed while the graph is
drawn rather than as its own pass over the IR, so it still has no observable but the diagram; arrays
still have no element model; and a phi's back edge contributes no objects, because the value it
names has not been drawn when the phi is.

## 4. An IR between javac and the graph

`AstBlockProcessor` is 902 lines that simultaneously resolve names, decide primitive versus
reference, track object identity, inline calls, mint ids and add edges. The evidence that this is
one job too many is structural, not stylistic:

- Four satellite scanners (`AstMemPosProcessor`, `AstLastNameProcessor`, `AstParentExprProcessor`,
  `AstMethodInvocationProcessor`) exist to re-walk the same tree asking a different question.
- `AstMemPosProcessor` has to call *back* into `AstBlockProcessor.constructedMemPos` and
  `invocationMemPos`, guarded by an `IdentityHashMap` memo, so that asking "which object is this"
  does not inline the callee a second time. A memo whose job is to stop a query from having side
  effects is the design saying that evaluation and interrogation have been fused.

Lower javac trees into a small explicit instruction set — const, read, write, binop, unop, call, new,
field-read, field-write, phi, return — each instruction carrying its source position and its
resolved `Element`. The graph builder then consumes instructions and never sees an `ExpressionTree`.

Two consequences worth the work on their own. The `MODELLED_EXPRESSIONS` gate moves to the lowering
step, where it can cover **statements** as well as expressions — the hole that let the enhanced
`for` and the `catch` parameter fail several lines away from their cause, and that the `unboundLocal`
fixture still marks. And the lowering becomes testable by asserting on instructions, with no
rendering involved.

## 5. Failure that is scoped, instead of total

One `(int)` cast on a reachable path produces **zero bytes of output for the entire corpus**. That
is not a hypothetical: `Helper.twice` containing `(int)(v * 2.0)` takes down a graph of which it is
one node, and a thousand other files would go with it.

The principle behind the gate is right and stays: a gap must never be drawn as a flow. But codeflow
already has an honest rendering for "there is something here I cannot see inside" — the opaque
`EXTERNAL` node, used for every call into the standard library. A cast is not more dangerous than
`java.util`.

So an unmodelled construct becomes an `UNMODELLED` node: visually distinct from both a real value
and an external call, labelled with its kind, carrying `file:line:col`, inputs flowing in and one
value out. Alongside it, a diagnostics list in the JSON payload, a stderr summary in the style of
the existing counters (`codeflow: 4 constructs not modelled`), and a non-zero exit. The reader
cannot mistake it for a flow, and one cast does not cost them the other 999 files.

The hard failure is kept for the case that really is the analysis having lost something: a local
read with no reaching definition. That distinction already exists in `unassigned`; this just puts
the two kinds of failure on the right sides of it.

## 6. Every node knows where it came from

Nothing carries a source position past the builder, so no exporter and no viewer can answer "which
line is this?" — the one question a reader of unfamiliar code asks constantly. codemap tags every
node with its line and asserts it in goldens; the findings doc names this as the one idea worth
taking from it.

Born with `(file, line, column, kind)` on every node, this is nearly free at construction time and
it pays in four places: the viewer can show and link source, goldens can assert positions, the
tool's own failures become cheap to localise, and an `UNMODELLED` node (§5) has something to say.

## 7. Entry points as a selector, not `main`

`AstReader` graphs whatever one `main` reaches, and throws `No method named 'main'` otherwise. Real
Java — including Fineract, the tool's own stated real-world target — is services, controllers and
libraries, most of which have no `main` at all. The tool's contract excludes most of its subject
matter.

With summaries (§2), roots are cheap. Default to every public method as its own root; let
`--from Class#method` narrow it; keep exactly the reporting that exists today, which is the good
part of the current design — say which roots were taken and which were not, because silence there
reads as "this is the codebase".

**Landed, in part.** `--from Class#method` selects any method the sources declare, `main` remains
the default, and both paths report the choice and the alternatives on stderr; a corpus with no
`main` and no `--from` now names the flag and lists the candidates instead of dead-ending. The
every-public-method default waits on §2, as this section's own first sentence says it must: without
summaries a root is not cheap, because each one re-inlines every method it reaches at every call
site.

## 8. One graph model, several renderers, and no hand-written syntax

The analysis is currently bent to fit Mermaid. `/` is called `div`, `?:` is called `ternary`, `|` is
called `bitOr` — not because those are better names but because the symbols are Mermaid syntax and a
raw one corrupts the document. `assertNoUnknownOperators` exists to police the leak. A rendering
constraint is reaching back into what the analysis calls things, and it reaches through every
exporter, including the three that have no such constraint.

Nodes should carry a semantic operator (`Op.DIVIDE`) and the source text. Each renderer maps that to
its own display form and escapes at its own boundary with a real writer, rather than `JsonExporter`
hand-escaping JSON and `GraphmlExporter` hand-writing XML. Then a label can be exactly what the
source says.

## 9. Tests: invariants first, snapshots as change detectors

CLAUDE.md already says a green golden is evidence of nothing, and the findings doc sharpens it: the
prescribed structural multiset diff was **blind** to the `x = x + 1` fix, because the cyclic graph
and the correct one carry the same label-stripped edges. Both checks are necessary and neither is
sufficient. Yet 44 goldens are the bulk of the suite.

Invert the ratio. Lead with assertions that need no expected output and hold on every fixture and
every corpus:

- every literal written in the source reaches at least one node;
- every read of a local has an incoming edge from a definition (this is `unboundLocal`'s bug, stated
  as a property);
- node count equals IR instruction count, so nothing is silently dropped or duplicated;
- the existing three invariants, unchanged.

Then `reaches()` assertions for behaviour, because they walk ids. Then snapshots, generated and
kept — but named `snapshot.md`. The current filename claims precisely the thing the documentation
spends a paragraph explaining it does not have.

## Housekeeping the rewrite makes moot

Recorded so it is not rediscovered: `graph/ObjVariable.kt` is never referenced and is a copy of
`graph/Variable.kt`; `NodeType.MEM_SPACE` and `GraphNode.MemSpace` are never constructed;
`GlobalContext.getMemPos` — the throwing variant — exists only to be caught and swallowed one line
later in `AstMemPosProcessor`; `MemPos.counter` is a JVM-global mutable counter, the same
non-determinism `serial` was changed to avoid, still present for anything that prints a `MemPos`.
CLAUDE.md says 33 goldens; there are 44.

## The order it would actually be done in

If this were treated as a rewrite of the existing tool rather than a green field:

1. **IR + lowering** (§4), with the gate moved onto it and positions attached (§6). Nothing else can
   be built cleanly first, and it is the only step here that does not change a single answer — so it
   can be verified against the current goldens byte for byte.
2. **CFG and SSA** (§1). Every branching golden moves; this is where the multiset-diff procedure
   earns its keep, and where each moved fixture has to be read rather than regenerated.
3. **Points-to** (§3) on top of the IR, replacing `MemPos`.
4. **Summaries and call-site instantiation** (§2), which is also when recursion stops crashing and
   the viewer's expansion changes meaning.
5. **Renderers and tests** (§8, §9).

§5 (scoped failure) and §7 (entry points) depend on none of the above and are each an afternoon.
They are also the two that most change who can use the tool, which is an argument for doing them
first regardless of this ordering.
