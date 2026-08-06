# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

codeflow reads Java source and emits a Mermaid dataflow graph: which value reaches which, through
which operators and calls. It is meant to be pointed at code nobody has read yet and be believed,
so **a silently wrong graph is worse than a failure** — a wrong diagram is readable, plausible, and
gives no sign anything is missing. Most of the design decisions below follow from that.

## Commands

```shell
./gradlew build                      # compile + test
./gradlew test --rerun-tasks         # tests, ignoring up-to-date checks
./gradlew test --tests '*ternary*'   # one test
UPDATE_SNAPSHOTS=1 ./gradlew test    # rewrite the golden files (see Testing)
./gradlew run --args="path/to/java/dir"
```

The toolchain is pinned to **JDK 21** (`app/build.gradle`). The installed CLI
(`./gradlew installDist`, then `./app/build/install/app/bin/app <dir>`) therefore needs a 21
runtime — if the shell's `java` is older it fails with `UnsupportedClassVersionError`, so set
`JAVA_HOME=$(/usr/libexec/java_home -v 21)`. The pin matters because codeflow parses with the
*running* JDK's javac (`ToolProvider.getSystemJavaCompiler()`), which makes the JDK an input to the
output, not just to the build.

## Architecture

`AstReader.process` is the whole pipeline, in three passes over the parsed compilation units:

1. `AstClassProcessor` — records fields and `extends` relationships into `GlobalContext`.
2. `AstProcessor` — records methods into `GlobalContext.methods` and constructors into
   `Constructors`, keyed per class by parameter type names.
3. `AstBlockProcessor.invokeMethod` starting from `main` — walks statements and builds the graph.

`MermaidExporter` then renders the root `GraphBuilderBlock` and its `calledMethods` recursively.

### Parsed, never attributed

`AstReader` calls `task.parse()` and **not** `task.analyze()`, so there is no symbol table and no
type information anywhere. Everything is resolved by simple name. This one fact explains most of
what looks odd:

- `TypeNameExtractor` / `resolveArgumentTypeNames` (`ArgumentTypes.kt`) recover an argument's type
  from the `MemPos` its variable points at, because the declared type is not available.
- `Constructors.constructorMatch` picks an overload by comparing uppercased type *name strings*.
- `JMethodId` and `JIdentifierId` hash the bare name, so all same-named methods across all classes
  collapse, and `GlobalContext.isPrimitiveMap` is global by name with no scope at all.

That last group is a known correctness limit, not a style issue — see `docs/plans/` for the
assessment. Do not assume a name resolves to what it would resolve to in javac.

### Calls are inlined per call site

`visitMethodInvocation` builds a nested `GraphBuilderBlock` and recurses into the callee's body for
*every* call site, rather than summarising a method once. `PosStack` — a stack of `file:pos` — is
what keeps the same variable in two different invocations apart. There is no depth limit and no
in-progress set, so a recursive method has nothing stopping it. A method with no body to inline
(outside the analysed sources) becomes a single opaque `EXTERNAL` node instead: arguments and
receiver flow in, the result flows out.

### Identity vs. lookup — do not conflate these again

Two different questions, deliberately answered by two different things:

| Question | Answered by | Granularity |
|---|---|---|
| "Which box on the diagram?" | `GraphNode.serial` | Per **occurrence** — `y = 1; y = y + 1` is two boxes |
| "Which variable is this?" | `GraphNodeId` / `JNodeId` | Per **variable** — every read of `x` must find the same one |

`serial` comes from a counter owned by the root `GraphBuilderBlock` (`nextSerial()`), handed out at
creation, per-run so snapshots stay deterministic. `JNodeId` deliberately leaves the source position
*out* of its key so a read finds its declaration.

**Never derive a rendered id by hashing attributes.** That is what this used to do, and two
unrelated nodes whose hashes agreed were drawn as one box carrying every edge of both — a diagram
asserting flows that do not exist, with nothing to notice. `assertNoDuplicateNodeIds` guards this.

### MemPos is object identity

A `MemPos` stands for one object instance and owns the nodes for its fields. It is how
`this.field`, an implicit-`this` field read, and a field written in a constructor and read in
another method all find each other — the block-parent chain does not span sibling methods, so
`visitIdentifier` and `visitMemberSelect` consult the owning `MemPos` first. `MemPos` has no
`equals`, so two instances are two objects, which is what identity should mean here.

## Adding support for a Java construct

`AstBlockProcessor.scan` is a gate: any `ExpressionTree` whose kind is not in
`MODELLED_EXPRESSIONS` throws a `GraphException` naming the kind and `file:line:col`. This exists
because `TreeScanner`'s default — scan the children, return one of their results — is a *fabricated
edge* for an expression: `!flag` comes back as the node for `flag`, so the operator vanishes and
the graph claims something the code does not do. Two real bugs (the dropped ternary branch, vanished
unary operators) came from exactly that.

So, to add a construct: write the visitor, then add its `Tree.Kind` to `MODELLED_EXPRESSIONS`.
Never widen that set without a visitor behind it.

Two related rules:

- Use `evaluate(tree, ctx)` — not `tree.accept(...)` — for anything that needs a *value*. It routes
  through `scan`, so the gate cannot be bypassed, and it fails loudly when an expression produces
  nothing.
- Put `ctx.location(tree)` in `GraphException` messages. The first question about any failure is
  which line of which file.

Operator labels go through `binaryOperatorLabel` / `unaryOperatorLabel` / `compoundAssignmentLabel`,
which map symbols that are also Mermaid syntax to words (`/` → `div`, `|` → `bitOr`, `&` → `bitAnd`,
and `?:` → `ternary`). A raw symbol corrupts the diagram rather than just looking odd.

## Testing

`AppTest.kt` has three kinds of assertion, and the mix is deliberate:

- **Golden files** (`app/src/test/resources/<fixture>/truth.md`) — 16 of them. They certify
  *unchanged*, not *correct*. `ternary/truth.md` was once written from a buggy run and passed
  happily while encoding a graph with a branch missing. Treat a green golden file as evidence of
  nothing.
- **Behaviour tests** using `edgeLabels`, which reduces the graph to `(sourceLabel, targetLabel)`
  pairs and ignores ids. These are the assertions that can fail on a graph that has never been
  correct. **New behaviour needs one of these**, not just a regenerated snapshot.
- **Suite-wide invariants** run on every fixture: `assertNoSelfEdges`, `assertNoUnknownOperators`,
  `assertNoDuplicateNodeIds`.

Snapshots are only written when missing or under `UPDATE_SNAPSHOTS=1`, so a regression cannot
overwrite its own expectation. When a change does move snapshots, verify them *structurally* rather
than reading diffs: normalise old (`git show HEAD:<path>`) and new to sorted multisets of
`label:TYPE` nodes and `label:TYPE -> label:TYPE` edges with ids stripped, and diff those. Anything
left over is a real change and needs explaining.

`app/src/test/resources/codemap` and `ls` have no test referencing them; `codemap/truth.md` is stale
and still in the pre-serial id format.
