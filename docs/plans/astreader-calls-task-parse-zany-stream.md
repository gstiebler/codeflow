# Resolve by symbol, not by name

## Context

`AstReader` calls `task.parse()` and not `task.analyze()`, so there is no symbol table and no type
information anywhere. Everything is resolved by simple name, and CLAUDE.md currently has to warn the
reader that this "explains most of what looks odd". The odd things are not stylistic — they are the
places where the graph can be confidently wrong:

- `JMethodId` hashes the bare method name, so every same-named method in every class collapses into
  one entry. A call to `Account.close()` can be inlined with the body of `Connection.close()`.
- `GlobalContext.isPrimitiveMap` is keyed by bare name with no scope at all, so an `int count` in
  one class decides how a `Counter count` in another is modelled.
- `Constructors.constructorMatch` picks an overload by comparing *uppercased type-name strings*, and
  says so in its own TODO ("very naive, there will be false positives").
- `resolveArgumentTypeNames` and `TypeNameExtractor` exist only to feed that string comparison,
  recovering an argument's type from the `MemPos` its variable points at because the declared type
  is not available.
- `JNodeId`'s key is `(name, memPos)`, and for a local variable `memPos` is the *enclosing instance*.
  Two methods on the same object each declaring a local `a` produce the same key, and
  `getVariable` walks the block-parent chain, so a callee's `a` can resolve to its caller's.

None of these fail loudly. They produce a readable, plausible diagram asserting flows that do not
exist — the exact failure mode the whole project is organised against.

The fix is to stop guessing: run attribution and ask javac what each name means.

This continues `docs/plans/plan-for-these-improvements-mighty-prism.md`, which fixed node identity
(item 2) and explicitly deferred resolution by simple name (item 3) as separate work. This is that
work.

## Attribution is viable here — measured, not assumed

The obvious objection is that codeflow is pointed at a bare directory with no classpath, where
nothing compiles. Probes against a file with two unresolvable imports and no classpath (kept in the
session scratchpad as `probe/Probe.java`, `probe/Probe2.java`) settle it:

- `analyze()` **returns without throwing** on uncompilable input. Errors land in the
  `DiagnosticCollector` that `AstReader` already constructs at line 22 and never uses.
- Anything declared inside the analysed sources resolves exactly. `run(Thing).a` and
  `main(String[]).a` are different `Element`s; `test.Broken.counter` carries its owner.
- **Overloads resolve.** `helper(c)` → `test.Broken.helper(int)`, not the `String` overload. In a
  second file, `helper(b)` → `test.Fine.helper(int)` — **a file full of errors does not blind the
  others**, which is what makes this usable on real input.
- Primitive vs reference is `TypeMirror.getKind().isPrimitive`.
- `String.valueOf(int)` resolves against the platform with no classpath at all.
- Unresolvable things are **marked** as such: `TypeKind.ERROR`, and a call on an error-typed receiver
  yields an `Element` whose kind is `CLASS` rather than `METHOD`. The unknown announces itself,
  rather than being silently mistaken for something known.
- Attribution costs milliseconds on this input size.

That last point is what makes the design safe: **an `Element` is trusted only when its kind is the
kind expected at that site.** Anything else is treated as outside the analysed sources and takes the
existing opaque `EXTERNAL` path — arguments and receiver in, result out. So the tool keeps working on
real code, where most of a file's imports are unresolvable, and never claims a resolution it did not
make.

## Design: an attribution side table

`Trees.getElement` needs a `TreePath`, but every processor here is a `TreeScanner` holding a bare
`Tree`, and `AstBlockProcessor.invokeMethod` re-enters a callee's body with no path at all.
Converting them all to `TreePathScanner` would mean threading paths through `Method` and every
recursion point.

Instead: `analyze()` fills symbols into the **same tree objects** `parse()` already returned. So one
`TreePathScanner` pass after `analyze()` can record what it finds, and every existing scanner stays
exactly as it is and simply asks.

New `app/src/main/kotlin/codeflow/java/Symbols.kt`:

```kotlin
/**
 * What javac resolved each tree to.
 *
 * Populated by one pass after attribution, so the scanners that build the graph can ask for a
 * symbol without carrying a TreePath. Keyed by tree identity: the trees are the ones parse()
 * returned and analyze() annotated, so two structurally equal trees at different sites are
 * correctly two different keys.
 */
class Symbols(...) {
    /** The declaration this tree resolved to, or null if javac could not resolve it. */
    fun element(tree: Tree): Element?

    /** As [element], but null unless the resolution is of the expected kind. */
    fun element(tree: Tree, expected: ElementKind): Element?

    fun type(tree: Tree): TypeMirror?
    fun isPrimitive(tree: Tree): Boolean

    /** Call sites and types javac could not resolve, for the summary line. */
    val unresolvedCount: Int
    val totalCount: Int
}
```

Both maps are `IdentityHashMap<Tree, _>`.

---

## Commit 1 — attribute, change nothing

`AstReader.process`:

- Pass `diagnostics` as the task's `DiagnosticListener` (currently `null`, so javac would print
  "cannot find symbol" to stderr for every unresolved import).
- Pass `-proc:none`, so no annotation processor is discovered off the empty processor path.
- Call `task.analyze()` after `task.parse()`, wrapped so that a `RuntimeException` from javac
  degrades to "no symbols" rather than taking the run down — the graph must still build on input
  javac chokes on.
- Build `Symbols` from the parsed units, and thread it into `GlobalContext` (which every processor
  already receives).
- Print the summary line to **stderr**, not stdout: stdout is the Mermaid document and is
  redirected to a file. Something like `codeflow: 47 of 210 references unresolved`.

Nothing consumes `Symbols` yet.

**This commit must not change a byte of any `truth.md`.** If it does, attribution is altering what
the existing name-based code sees, and that has to be understood before anything is built on it.

## Commit 2 — look things up by element

Same shape everywhere: where a name was the key, the `Element` is.

**Primitives.** `AstBlockProcessor.visitAssignment` and `visitVariable` ask
`symbols.isPrimitive(tree)` instead of `globalCtx.isPrimitive(JIdentifierId(name))`. That deletes
`isPrimitiveMap`, `registerIsPrimitive`, `isPrimitive`, `IdentifierId`, `JIdentifierId`, and
`AstClassProcessor.visitVariable`, whose only job was to populate the map.

**Methods.** `GlobalContext.methods` is keyed by `ExecutableElement`. `AstProcessor.visitMethod`
registers under `symbols.element(node, ElementKind.METHOD)`; `visitMethodInvocation` looks up
`symbols.element(node, ElementKind.METHOD)`. When the element is absent or the wrong kind — the
`balance.plus(...)` case, where the receiver's type is an error — it falls through to the existing
`invokeExternalMethod`, unchanged. That deletes `MethodId` and `JMethodId`.

The `super(...)` / `this(...)` special case in `visitMethodInvocation` (which exists because those
parse as invocations of a method literally named "super") is no longer needed for *lookup*: both
resolve to a `CONSTRUCTOR` element directly. Keep `invokeConstructorDelegation` for its distinct
*behaviour* — it runs against the current `owner` MemPos rather than creating one — but select it on
the element's kind rather than on the string "super".

**Constructors.** `AstMemPosProcessor.visitNewClass` reads the `CONSTRUCTOR` element off the
`NewClassTree` and finds its `MethodTree` in the same element-keyed map. That deletes `Constructors`,
`Constructors.constructorMatch`, `resolveArgumentTypeNames`, `ArgumentTypes.kt` and
`TypeNameExtractor` outright.

`GraphBuilderBlock.className` (used to render `X.constructor`) comes from the constructor element's
enclosing `TypeElement`, which is also correct for a qualified `new a.b.C()` where
`node.identifier.toString()` currently yields the whole dotted string.

**Superclasses.** `registerSuperclass` / `getSuperclass` / the `superclasses` map exist only to
resolve `super(...)`, which now resolves directly. Expect `AstClassProcessor` to have nothing left
and be deleted; confirm rather than assume, since it is also the first of the three passes.

Fixture sources have no overloads and no cross-class name collisions, so **snapshots should not move
here either**. Any that do is a place where the old string matching was picking the wrong target —
read it and name it in the commit message.

## Commit 3 — `JNodeId` keyed by its declaration

`JNodeId.key()` becomes `listOf(element, memPos)`.

`memPos` stays: an `Element` identifies a *declaration*, and one field declaration lives at a
different address in every instance. `(element, memPos)` is "which variable, in which object", which
is what the key has always been trying to say. The source position stays out, for the reason already
documented on `JNodeId` — a read of `x` has to find the `x` declared elsewhere.

Two sites have no declaration element and need handling:

- **The return node** (`GraphBuilderBlock.createReturnNode`) uses the method's *name* as a variable
  id. It is never looked up by key — `addReturnNode` holds the reference — so give it a plain
  `GraphNodeId(stack, name)`, whose `(label, stack)` key is already unique per block.
- **Parameter nodes** do have elements (`ElementKind.PARAMETER`), and switching them fixes the
  shadowing hazard `AstMemPosProcessor` documents at lines 39–44: an argument sharing a name with a
  parameter can no longer resolve to that parameter.

`AstLastNameProcessor` is then only reached from `visitCompoundAssignment` and `visitAssignment`,
both of which can ask for the element instead; expect it to be deletable.

**This is the commit that can legitimately move snapshots**, by separating locals that used to
collide. Every difference needs the structural check below and an explanation.

---

## Files

| File | Change |
|---|---|
| `app/src/main/kotlin/codeflow/java/Symbols.kt` | **new** — the side table |
| `app/src/main/kotlin/codeflow/java/AstReader.kt` | diagnostics listener, `-proc:none`, `analyze()`, build `Symbols`, stderr summary |
| `.../processors/GlobalContext.kt` | hold `Symbols`; methods keyed by element; drop `isPrimitiveMap`, `superclasses`, `constructors` |
| `.../processors/AstBlockProcessor.kt` | primitives, method lookup, constructor delegation by element |
| `.../processors/AstMemPosProcessor.kt` | `visitNewClass` by constructor element |
| `.../processors/AstProcessor.kt` | register methods by element; drop constructor registration |
| `.../java/ids/JNodeId.kt` | `key()` = `(element, memPos)` |
| `.../graph/GraphBuilder.kt` | return node uses `GraphNodeId`; `className` from the type element |
| **deleted** | `Constructors.kt`, `ArgumentTypes.kt`, `TypeNameExtractor.kt`, `MethodId.kt`, `JMethodId.kt`, `IdentifierId.kt`, `JIdentifierId.kt`, and — pending confirmation — `AstClassProcessor.kt`, `AstLastNameProcessor.kt` |
| `CLAUDE.md` | rewrite the "Parsed, never attributed" section |

## Verification

**Commits 1 and 2** — `./gradlew test --rerun-tasks`, and `git diff --stat` shows no `truth.md`
touched. That is the whole check for these two: either one changing a snapshot did something it was
not supposed to.

**Commit 3** — passing tests prove nothing, since the snapshots are expected to move. Use the
structural comparison CLAUDE.md prescribes: normalise old (`git show HEAD:<path>`) and new to sorted
multisets of `label:TYPE` nodes and `label:TYPE -> label:TYPE` edges with ids stripped, and diff
those. Every surviving difference is read and explained before the snapshot is accepted.

**New behaviour tests** (`edgeLabels`, not snapshots — a green golden file is evidence of nothing).
Each targets a bug this change removes, so each must **fail on `main`**:

1. **Overloads** — a class with `helper(int)` and `helper(String)` and different bodies; assert the
   edges of the body actually selected. Today `JMethodId` collapses both.
2. **Same name, two classes** — `Account.close()` and `Connection.close()` with different bodies;
   assert a call on an `Account` shows `Account.close`'s flow.
3. **Same name, two types** — `int count` in one class and an object `count` in another; assert both
   are modelled correctly. Today `isPrimitiveMap` lets whichever registered last decide.
4. **Locals that used to collide** — `a` in a caller and an unrelated `a` in a callee; assert no edge
   between them. Today the block-parent chain connects them.
5. **Unresolvable receiver stays opaque** — a file importing a nonexistent type and calling a method
   on it; assert an `EXTERNAL` node with the arguments flowing in, and assert the run *succeeds*.
   This is the regression guard on the whole "must still work without a classpath" property.

**End to end** — the Fineract input from the previous plan, at
`/private/tmp/claude-501/-Users-guistiebler-Documents-Projetos-fineract/b2dc6db1-dd22-41d0-b53e-f2e3e7ac0870/scratchpad/real-fineract`,
with its output alongside as `real-fineract-graph.md`.

```
./gradlew installDist
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./app/build/install/app/bin/app <that dir> > after.md
```

Three things to check:

1. It still completes. This input is almost entirely unresolvable imports, so it is the real test of
   the degradation path.
2. The guarded-vs-unguarded division asymmetry is still visible: the loop-body division reaches
   `periodAmortization` through a `ternary` fed by `==`, the post-loop one through `divide` with
   nothing on the path. This is the finding the tool exists to produce and it has to survive.
3. The unresolved-references summary is plausible against the file's imports — a count near zero
   would mean the counter is measuring the wrong thing.
