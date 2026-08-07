# Polymorphic Dispatch, Stage 1 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended)
> or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`)
> syntax for tracking.

**Goal:** A call whose receiver's tracked objects name exactly one concrete implementation inlines
*that* implementation, instead of whichever method javac resolved statically.

**Architecture:** `MemPos` already stands for one object instance and is already threaded through
every call site by `Frame`, so the concrete class is available where the decision has to be made —
it is simply not recorded. Stage 1 records it, adds one resolver that asks javac which method a
class would run, and consults it in `Frame.call`. No new node type, no new edge kind, no exporter
change.

**Tech Stack:** Kotlin, JDK 21 (`app/build.gradle` toolchain pin), javac's `com.sun.source` tree API
and `javax.lang.model`, JUnit via `./gradlew test`.

## Global Constraints

- The toolchain is pinned to **JDK 21**. Run the CLI with `JAVA_HOME=$(/usr/libexec/java_home -v 21)`.
- Every exporter writes to **stdout**; nothing else may. Debug goes to stderr.
- An `Element` is believed only when its kind is the kind expected at that site. Anything else takes
  the opaque `EXTERNAL` path.
- New behaviour needs a **behaviour test** using `edgeLabels`/`reaches`/`nodeTypes`, not a
  regenerated golden. A green golden file is evidence of nothing.
- A negative assertion must be paired with a positive one that fails when the feature does nothing.
- Snapshots are only written under `UPDATE_SNAPSHOTS=1`.
- Commit after every task. Branch is `rebuild-batch-1`; commit straight to it, no PR.

---

## Context

`app/src/test/resources/abstractMethod/App.java` is the fixture that says codeflow refuses to guess:
a call through an interface has no body registered, so it takes the opaque `EXTERNAL` path. That is
honest. But the *concrete* case is not, and nothing covers it. Given

```java
class Base { int f(int x) { return x + 1; } }
class Sub extends Base { @Override int f(int x) { return x * 100; } }

Base b = new Sub();
int out = b.f(7);
```

codeflow today draws:

```
n5[7] --> n8[x]     subgraph b6["f"]     n8[x] --> n10[+]     n9[1] --> n10[+]     n10[+] --> n7[f]
```

It inlines **`Base.f`** and shows `out` receiving `7 + 1`. The program returns `700`. There is no
`EXTERNAL` node and no warning — a complete, readable, wrong diagram, which is the failure the whole
project is organised against, in the one case nobody wrote a fixture for.

Two facts make the fix small:

- **The graph is already context-sensitive.** `Frame.invoke` binds each argument's `Set<MemPos>` onto
  the callee's parameter (`IrGraphBuilder.kt:126-136`), and inlining is per call site, so a method
  called from two places is two frames holding two different object sets. Verified: with
  `c(Holder x)` called from `a()` with a holder whose `v` is 11 and from `b()` with one whose `v` is
  22, the read of `x.v` resolves to `n8[v]` in one copy and `n18[v]` in the other. Nothing has to be
  invented to know which object a receiver is.
- **One object is one `MemPos` across the whole inheritance chain.** `construct` creates a single
  position and `delegate` runs superclass constructors on `owner`, which is what the `inheritance`
  fixture already asserts for fields. So a `Sub`'s position says `Sub` no matter which class's method
  is currently running on it — which is what makes `this.step()` inside `Base.template()` reach
  `Sub.step`.

What is missing is only that `MemPos` records no type: it holds a label and its field nodes and
nothing else (`app/src/main/kotlin/codeflow/graph/MemPos.kt`).

### Decisions taken

- **Dispatch on objects, not on declared types.** Where the receiver's objects are known, they are a
  strictly better answer than the declared type, and they are already computed.
- **Exactly one implementation, or nothing.** When the receiver's objects agree on one
  implementation, inline it. When they name several, take `EXTERNAL`. Drawing every implementation
  and joining them is **Stage 2** and is deliberately out of scope — see "Stage 2" below.
- **No object tracked means today's behaviour.** Fall back to whatever javac resolved statically,
  which for an interface method is nothing and so is `EXTERNAL` already. This is what keeps existing
  fixtures still — a loop element or a caught exception gets a fresh typeless `MemPos`, and turning
  those into `EXTERNAL` would move goldens for no gain.
- **No gate on the dispatch.** Where the receiver's type was decided by an `if`, the chain is already
  on the page: the receiver variable is a gated join box carrying the condition, so `== --> if --> b`
  is drawn and the call sits one hop below it. A second gate mechanism at the call site would have to
  walk from the receiver `Val` back to its defining `Insn` and invert each path's objects into arm
  names, and would silently produce nothing for `Base b2 = b; b2.f()` or for a field read through two
  holders.

### The one thing that is not optional

`Lowering.receiverOf` (`Lowering.kt:243-251`) collapses `this` and `super` into a single
`Receiver.Enclosing`, on the correct grounds that both name the object the enclosing method runs on.
For dispatch they are opposites: `this.step()` **must** dispatch to the override, and `super.step()`
**must not**, or a base class calling `super` inlines the subclass override and recurses. So
`Receiver` gains a `Super` case. Task 3 is that, and Task 4's test is what proves it was needed.

### Verified before planning

- `constructorElement?.enclosingElement` is non-null for a class that declares no constructor.
  Checked against `fieldInitializer`, whose `Plain` class declares none: its golden contains
  `n15[4] --> n16[seeded]`, and that initializer only runs because `construct` passed a non-null
  class element to `initializersOf`. So `New`'s constructor element is a sound source for the type
  and no fallback is needed.
- `LoweringTest` asserts nothing about `super` or the `parentMethod` / `superCall` fixtures, so
  Task 3's change to `Insn.render()` output breaks no instruction-level assertion.

### Residue this plan does not fix

Recorded so a later reader does not mistake them for oversights:

- A receiver with **no tracked object** still inlines the statically resolved method, which can be
  the wrong one if the real object is a subclass. Unchanged from today, and narrowing it needs the
  declared-type fallback that was considered and rejected.
- A receiver assigned inside a loop from a variable declared above it dispatches on an **incomplete**
  object set, because a phi whose back edge has not been drawn yet contributes no objects. This is
  CLAUDE.md's stated open edge in the alias model; it is a *missing* possibility rather than an
  invented one.
- An enum constant with a constant-specific class body is not dispatched to. `enumConstantMemPos`
  leaves the type null, so such a call falls back to the statically resolved method.

---

## File Structure

| File | Responsibility after this plan |
|---|---|
| `app/src/main/kotlin/codeflow/graph/MemPos.kt` | Also carries the class the object was constructed as |
| `app/src/main/kotlin/codeflow/java/Symbols.kt` | Also answers "does this method override that one, seen from this class?" |
| `app/src/main/kotlin/codeflow/java/processors/GlobalContext.kt` | Also answers "which registered method would this class run for this declaration?" |
| `app/src/main/kotlin/codeflow/ir/Insn.kt` | `Receiver` distinguishes `super` from `this` |
| `app/src/main/kotlin/codeflow/ir/Lowering.kt` | Emits `Receiver.Super` for a `super.` qualifier |
| `app/src/main/kotlin/codeflow/ir/IrGraphBuilder.kt` | `Frame.call` resolves which body runs, instead of taking javac's static answer |
| `app/src/test/resources/overriddenMethod/` | New fixture: the bug |
| `app/src/test/resources/overriddenSuper/` | New fixture: `super` must stay non-virtual |
| `app/src/test/resources/templateMethod/` | New fixture: `this` must be virtual |
| `app/src/test/kotlin/codeflow/AppTest.kt` | Four behaviour tests, one rewritten; three golden registrations |

---

### Task 1: Record the class on `MemPos`

Nothing observable changes. This is the fact everything else reads.

**Files:**
- Modify: `app/src/main/kotlin/codeflow/graph/MemPos.kt:11`
- Modify: `app/src/main/kotlin/codeflow/java/processors/GlobalContext.kt:122`
- Modify: `app/src/main/kotlin/codeflow/ir/IrGraphBuilder.kt:546`

**Interfaces:**
- Produces: `MemPos.type: Element?` — the class element the object was constructed as, or null when
  unknown. `GlobalContext.createMemPos(label: String, type: Element? = null): MemPos`.

- [ ] **Step 1: Add the field to `MemPos`**

Replace the class declaration line with:

```kotlin
class MemPos(private val label: String, val type: Element? = null) {
```

Add the import at the top of the file:

```kotlin
import javax.lang.model.element.Element
```

Add this KDoc paragraph to the existing class comment, after the `[label]` paragraph:

```
 * [type] is the class the object was constructed as, which is what decides *which* implementation a
 * call on it runs. Null where nothing said - a loop element, a caught exception, an object from
 * outside the analysed sources - and a null type dispatches to nothing, which leaves the call
 * exactly where it was before.
 */
```

- [ ] **Step 2: Widen the factory**

In `GlobalContext.kt`, replace:

```kotlin
    fun createMemPos(label: String): MemPos = MemPos(label)
```

with:

```kotlin
    /** [type] is the class the object was constructed as - see [MemPos.type]. */
    fun createMemPos(label: String, type: Element? = null): MemPos = MemPos(label, type)
```

- [ ] **Step 3: Pass the class at construction**

In `IrGraphBuilder.kt`, inside `construct`, replace:

```kotlin
        val created = setOf(into ?: globalCtx.createMemPos(insn.source))
```

with:

```kotlin
        // The class comes from the constructor's own declaration, which is present even when the
        // class declares no constructor - attribution inserts one, and its enclosing element is
        // still the class. `Plain` in the fieldInitializer fixture is the case that proves it.
        val created = setOf(
            into ?: globalCtx.createMemPos(insn.source, constructorElement?.enclosingElement)
        )
```

- [ ] **Step 4: Verify nothing moved**

Run: `./gradlew build`
Expected: `SUCCESS: Executed 145 tests`. No golden moves — nothing reads `type` yet.

Run: `git status --short -- '*truth.md'`
Expected: empty output.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/codeflow/graph/MemPos.kt \
        app/src/main/kotlin/codeflow/java/processors/GlobalContext.kt \
        app/src/main/kotlin/codeflow/ir/IrGraphBuilder.kt
git commit -m "Let a memory position say which class it was constructed as"
```

---

### Task 2: Ask javac which method a class would run

Still nothing observable. This is the resolver, tested through Task 4's behaviour test rather than
directly — it takes javac `Element`s, which cannot be built without a compilation task.

**Files:**
- Modify: `app/src/main/kotlin/codeflow/java/Symbols.kt` (add a method after `isDeclaredInSources`, `:67`)
- Modify: `app/src/main/kotlin/codeflow/java/processors/GlobalContext.kt` (add after `findMethod`, `:86`)

**Interfaces:**
- Consumes: `MemPos.type` from Task 1.
- Produces: `GlobalContext.implementation(declared: ExecutableElement, type: Element): Method?` —
  the registered `Method` a value of `type` would run for a call to `declared`, or null when these
  sources register none.

- [ ] **Step 1: Expose javac's override relation**

In `Symbols.kt`, add after `isDeclaredInSources`:

```kotlin
    /**
     * Whether [candidate], seen from [type], is the method that runs for a call to [declared].
     *
     * javac's own answer rather than a name-and-signature comparison of ours: overriding has rules
     * about visibility, static-ness, return types and generic substitution that a match on the
     * simple name gets wrong in both directions, and getting it wrong here inlines a body the call
     * cannot reach.
     */
    fun overrides(candidate: ExecutableElement, declared: ExecutableElement, type: TypeElement): Boolean =
        elementUtils.overrides(candidate, declared, type)
```

Add these imports:

```kotlin
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
```

(`Element` and `ElementKind` are already imported.)

- [ ] **Step 2: Add the resolver**

In `GlobalContext.kt`, add after `findMethod`:

```kotlin
    /**
     * The body a value of [type] runs for a call to [declared], or null when these sources have none.
     *
     * Which implementation a call reaches is decided at run time by the receiver's class, so asking
     * the class rather than the call site is the whole of dispatch. The walk goes up the superclass
     * chain because a class that does not override inherits, and the inherited declaration is the
     * one registered - `Child` declaring no `shift` runs `Parent.shift`, which is what the
     * parentMethod fixture already draws.
     *
     * Null means these sources register nothing for it: an interface method whose implementation is
     * outside the corpus, or a class javac loaded from the classpath. The caller falls back to what
     * javac resolved statically, which is the opaque EXTERNAL path when that has no body either.
     */
    fun implementation(declared: ExecutableElement, type: Element): Method? {
        // `overrides` is asked *seen from* the receiver's own class, not from whichever superclass
        // the walk has reached - that is the parameter's whole purpose, and passing `current`
        // instead would ask whether Base.f overrides Base.f as seen from Base.
        val receiverType = type as? TypeElement ?: return null
        var current: TypeElement? = receiverType
        while (current != null) {
            for (candidate in ElementFilter.methodsIn(current.enclosedElements)) {
                if (candidate == declared || symbols.overrides(candidate, declared, receiverType)) {
                    return methods[candidate]
                }
            }
            current = (current.superclass as? DeclaredType)?.asElement() as? TypeElement
        }
        return null
    }
```

Add these imports:

```kotlin
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.util.ElementFilter
```

(`Element`, `ElementKind`, `ExecutableElement` and `Modifier` are already imported.)

- [ ] **Step 3: Verify it compiles and nothing moved**

Run: `./gradlew build`
Expected: `SUCCESS: Executed 145 tests`. Nothing calls `implementation` yet.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/codeflow/java/Symbols.kt \
        app/src/main/kotlin/codeflow/java/processors/GlobalContext.kt
git commit -m "Ask javac which method a class would run for a declaration"
```

---

### Task 3: Tell `super` apart from `this`

**Files:**
- Modify: `app/src/main/kotlin/codeflow/ir/Insn.kt:217-236`
- Modify: `app/src/main/kotlin/codeflow/ir/Lowering.kt:236-251`
- Modify: `app/src/main/kotlin/codeflow/ir/IrGraphBuilder.kt:230-234`, `:248`, `:487-492`

**Interfaces:**
- Produces: `Receiver.Super` — a fourth case, an object like `Enclosing` but one whose implementation
  is chosen by the *written* class rather than by the receiver's.

- [ ] **Step 1: Add the case**

In `Insn.kt`, the `Receiver` KDoc opens "Three cases rather than a nullable value". Change that
opening to:

```kotlin
 * Four cases rather than a nullable value, because "no receiver written", "no object at all" and
 * "this object, but the implementation the written class names" are different facts. The first two
 * were conflated once already: an unqualified `record()` inside a method of Gauge runs on the same
 * object the method does, while `Math.abs(x)` runs on nothing, and both were lowered to a missing
 * receiver, so every field the first one wrote landed where nobody could look it up.
```

Then add after the `Enclosing` object:

```kotlin
    /** `super.m()` - the same object, but the implementation the written class names, not the one it is. */
    data object Super : Receiver() {
        override fun toString() = "super"
    }
```

and narrow `Enclosing`'s own KDoc, replacing its one line with:

```kotlin
    /** `value`, `this.value`, `record()` - the object the enclosing method is on. */
```

- [ ] **Step 2: Emit it**

In `Lowering.kt`, replace the body of `receiverOf`:

```kotlin
        private fun receiverOf(expression: ExpressionTree?, ctx: ProcessorContext): Receiver {
            if (expression == null) return Receiver.Enclosing
            if (expression is IdentifierTree) {
                if (expression.name.contentEquals("this")) return Receiver.Enclosing
                if (expression.name.contentEquals("super")) return Receiver.Super
            }
            if (isTypeName(expression)) return Receiver.TypeName
            return Receiver.Value(evaluate(expression, ctx))
        }
```

and replace the KDoc paragraph above it:

```kotlin
        /**
         * The object an access or a call is written against.
         *
         * `this` and `super` both name the object the enclosing method is running on, and neither
         * produces a value, but they are not interchangeable: `super.m()` runs the implementation
         * the *written* class names, while `this.m()` runs the one the object *is*. A type name
         * produces no value either, but for the opposite reason - there is no object at all.
         */
```

- [ ] **Step 3: Handle it everywhere `Receiver` is matched**

In `IrGraphBuilder.kt`, in `holderOf`, replace:

```kotlin
            Receiver.Enclosing -> owner
```

with:

```kotlin
            Receiver.Enclosing, Receiver.Super -> owner
```

In `readField`, replace:

```kotlin
        val written = insn.receiver != Receiver.Enclosing
```

with:

```kotlin
        // `super.f` and `this.f` are both a receiver somebody wrote, and neither is the unqualified
        // name that falls back to this method's own field.
        val written = insn.receiver != Receiver.Enclosing
```

(No behavioural change: a field read through `super` names the same object, and `Receiver.Super` is
already `!= Enclosing`, which is the answer wanted — it *was* written.)

In `call`, replace:

```kotlin
            Receiver.Enclosing -> if (Modifier.STATIC in method.element.modifiers) emptySet() else owner
```

with:

```kotlin
            Receiver.Enclosing, Receiver.Super ->
                if (Modifier.STATIC in method.element.modifiers) emptySet() else owner
```

- [ ] **Step 4: Verify nothing moved**

Run: `./gradlew build`
Expected: `SUCCESS: Executed 145 tests`. `Receiver.Super` is emitted but treated identically
everywhere, so the graph is unchanged.

Run: `git status --short -- '*truth.md'`
Expected: empty output.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/codeflow/ir/Insn.kt \
        app/src/main/kotlin/codeflow/ir/Lowering.kt \
        app/src/main/kotlin/codeflow/ir/IrGraphBuilder.kt
git commit -m "Tell \`super\` apart from \`this\` in the IR"
```

---

### Task 4: Dispatch

The one task that changes what is drawn. Red first.

**Files:**
- Create: `app/src/test/resources/overriddenMethod/App.java`
- Create: `app/src/test/resources/overriddenSuper/App.java`
- Create: `app/src/test/resources/templateMethod/App.java`
- Modify: `app/src/test/kotlin/codeflow/AppTest.kt` (three new tests, one rewritten, three golden registrations)
- Modify: `app/src/main/kotlin/codeflow/ir/IrGraphBuilder.kt:480-496`

**Interfaces:**
- Consumes: `MemPos.type` (Task 1), `GlobalContext.implementation` (Task 2), `Receiver.Super` (Task 3).

- [ ] **Step 1: Write the three fixtures**

`app/src/test/resources/overriddenMethod/App.java`:

```java
/*
 * A concrete method overridden by a subclass, reached through a variable of the superclass type.
 *
 * javac resolves `b.f(7)` to `Base.f`, because that is what the declared type of `b` offers. What
 * runs is `Sub.f`, because that is what `b` *is*. Inlining javac's answer drew `out` receiving
 * `7 + 1` for a program that returns 700 - complete, readable and wrong, with no EXTERNAL node and
 * no warning to say so.
 */
package overriddenMethod;

class Base {
    int f(int x) {
        return x + 1;
    }
}

class Sub extends Base {
    @Override
    int f(int x) {
        return x * 100;
    }
}

public class App {
    public static void main(String[] args) {
        Base b = new Sub();
        int out = b.f(7);
    }
}
```

`app/src/test/resources/overriddenSuper/App.java`:

```java
/*
 * `super.f(...)` inside the override, which must NOT dispatch.
 *
 * The receiver of a `super` call is the same object as `this`, so dispatching on the object would
 * send `super.f(x)` straight back to `Sub.f` - the method it is written inside. `isBeingInlined`
 * would catch the recursion and draw an opaque box, so the failure looks like a limit of the
 * sources rather than like the analysis having gone wrong. `Base.f` is what runs and `+` is what
 * must be on the diagram.
 */
package overriddenSuper;

class Base {
    int f(int x) {
        return x + 1;
    }
}

class Sub extends Base {
    @Override
    int f(int x) {
        return super.f(x) * 100;
    }
}

public class App {
    public static void main(String[] args) {
        Base b = new Sub();
        int out = b.f(7);
    }
}
```

`app/src/test/resources/templateMethod/App.java`:

```java
/*
 * An unqualified call inside a superclass method, which MUST dispatch.
 *
 * `step(x)` inside `Base.template` is written with no receiver, so it runs on whatever object
 * `template` was entered on - and that is a `Sub`. One object is one MemPos across the whole chain,
 * which is what makes the subclass's `step` reachable from a method that never mentions `Sub`.
 */
package templateMethod;

class Base {
    int template(int x) {
        return step(x) + 1;
    }

    int step(int x) {
        return x;
    }
}

class Sub extends Base {
    @Override
    int step(int x) {
        return x * 100;
    }
}

public class App {
    public static void main(String[] args) {
        Base b = new Sub();
        int out = b.template(7);
    }
}
```

- [ ] **Step 2: Write the three failing tests**

In `AppTest.kt`, add next to `aCallThroughAnInterfaceIsOpaqueAndACallWithABodyIsNot` (`:796`):

```kotlin
    /**
     * A call reaches the implementation the receiver's object has, not the one its declared type
     * names.
     *
     * `Base b = new Sub(); b.f(7)` resolved to `Base.f`, which is what javac answers about the
     * declared type, and codeflow inlined it: `out` received `7 + 1` for a program returning 700.
     * No EXTERNAL node and no warning - the wrong body drawn with the same confidence as a right
     * one, which is the failure the whole project is organised against.
     *
     * The object is what decides, and it was already known here: `b` carries the MemPos that
     * `new Sub()` created, and inlining is per call site, so nothing had to be invented to ask.
     */
    @Test
    fun aCallReachesTheOverrideTheReceiverActuallyHas() {
        val graph = buildGraph("overriddenMethod", listOf("App.java"))
        val edges = edgeLabels(graph)
        assertTrue("100" to "*" in edges, "the override's body is not drawn: $edges")
        assertTrue(reaches(graph, "7", "out"), "the argument does not reach the result: $graph")
        assertFalse("1" to "+" in edges, "the superclass body was inlined instead: $edges")
    }

    /**
     * `super.f(...)` runs the implementation the written class names, not the one the object is.
     *
     * The receiver of a `super` call is the same object as `this`, so dispatching on the object
     * sends it back to the method it is written inside. The lowering used to collapse `this` and
     * `super` into one receiver, which was right until the object started deciding anything.
     */
    @Test
    fun aSuperCallDoesNotDispatchBackToTheOverride() {
        val graph = buildGraph("overriddenSuper", listOf("App.java"))
        val edges = edgeLabels(graph)
        assertTrue("1" to "+" in edges, "the superclass body is not drawn: $edges")
        assertTrue("100" to "*" in edges, "the override's own body is not drawn: $edges")
        assertFalse("f" to "EXTERNAL" in nodeTypes(graph), "the super call went opaque: ${nodeTypes(graph)}")
    }

    /**
     * An unqualified call inside a superclass method reaches the subclass's override.
     *
     * `step(x)` in `Base.template` names no receiver, so it runs on the object `template` was
     * entered on. One object is one MemPos across the whole inheritance chain - `construct` makes
     * one and `delegate` runs the superclass constructors on it - so the position says `Sub` even
     * while a method of `Base` is running on it. Without that, a template method is drawn as
     * though the hook were never overridden.
     */
    @Test
    fun anUnqualifiedCallInASuperclassReachesTheOverride() {
        val graph = buildGraph("templateMethod", listOf("App.java"))
        val edges = edgeLabels(graph)
        assertTrue("100" to "*" in edges, "the override is not reached through `this`: $edges")
        assertTrue(reaches(graph, "7", "out"), "the argument does not reach the result: $graph")
    }
```

`AppTest.kt` imports `assertEquals`, `assertFailsWith` and `assertTrue` but **not** `assertFalse`.
Add it at line 19:

```kotlin
import kotlin.test.assertFalse
```

- [ ] **Step 3: Run them to verify they fail**

Run: `./gradlew test --tests '*ReachesTheOverride*' --tests '*SuperCallDoesNot*'`

Expected: FAIL, three tests.
- `aCallReachesTheOverrideTheReceiverActuallyHas` — "the override's body is not drawn"
- `anUnqualifiedCallInASuperclassReachesTheOverride` — "the override is not reached through `this`"
- `aSuperCallDoesNotDispatchBackToTheOverride` — "the override's own body is not drawn", because
  today `b.f(7)` inlines `Base.f` and `Sub.f` is never entered at all.

- [ ] **Step 4: Implement dispatch**

In `IrGraphBuilder.kt`, replace the whole of `call` (`:480-496`) with:

```kotlin
    private fun call(insn: Call, run: Run): Value {
        val receiverMemPos = receiverObjects(insn, run)
        val method = resolve(insn, receiverMemPos)
        if (method == null || isBeingInlined(method)) {
            val receiverNode = (insn.receiver as? Receiver.Value)?.let { run.node(it.value) }
            val inputs = listOfNotNull(receiverNode) + insn.args.map { run.node(it) }
            return Value(block.addExternal(base(labelId(insn.name, insn), insn), inputs))
        }
        val child = enter(method, receiverMemPos, insn)
        child.invoke(insn.args.map { run.node(it) }, insn.args.map { run.objects(it) })
        return Value(child.block.returnNode, child.block.returnedMemPos)
    }

    /** The objects the callee runs on, which is also what decides which body that is. */
    private fun receiverObjects(insn: Call, run: Run): Set<MemPos> = when (insn.receiver) {
        // A static method runs on no object at all, so it has nothing to inherit.
        Receiver.Enclosing, Receiver.Super ->
            if (Modifier.STATIC in (insn.target?.modifiers ?: emptySet())) emptySet() else owner
        Receiver.TypeName -> emptySet()
        is Receiver.Value -> run.objects(insn.receiver.value)
    }

    /**
     * Which body this call runs, which is a question about the receiver rather than about the name.
     *
     * javac resolves a call against the *declared* type, so `Base b = new Sub(); b.f(7)` comes back
     * as `Base.f` - a correct answer to a different question. Taking it drew the superclass body
     * with no sign anything had been chosen, which is the silently wrong graph in its purest form.
     *
     * Dispatch is on the objects the receiver actually holds, not on the declared type, because
     * those are already known: inlining is per call site and arguments carry their memory positions
     * into the callee, so a receiver is a concrete set here even when it is a parameter several
     * frames down.
     *
     * Only when they agree on **one** implementation. Several is a real answer this cannot draw yet
     * - it needs a box saying "one of these", which is Stage 2 - and picking one of them would be
     * the guess this whole change exists to stop. So several takes the opaque EXTERNAL path, as does
     * a receiver whose objects say nothing about their class.
     */
    private fun resolve(insn: Call, objects: Set<MemPos>): Method? {
        val declared = insn.target as? ExecutableElement ?: return null
        val statically = globalCtx.findMethod(declared)
        if (!isVirtual(insn, declared)) return statically
        val types = objects.mapNotNull { it.type }.distinct()
        if (types.isEmpty()) return statically
        return types.map { globalCtx.implementation(declared, it) }.distinct().singleOrNull()
    }

    /**
     * Whether the receiver's class gets to choose, which is what "virtual" means.
     *
     * `super.f()` names the implementation the written class has, and its receiver is the same
     * object as `this` - so dispatching on the object would send it back to the method it is
     * written inside. A `static` method has no receiver, and a `private` one is not inherited, so
     * neither can be overridden. Anything that is not a method declaration - a constructor, or a
     * name javac could not resolve - is not dispatched either.
     */
    private fun isVirtual(insn: Call, declared: ExecutableElement): Boolean =
        insn.receiver != Receiver.Super &&
            declared.kind == ElementKind.METHOD &&
            Modifier.STATIC !in declared.modifiers &&
            Modifier.PRIVATE !in declared.modifiers
```

Add the import if it is not already present:

```kotlin
import javax.lang.model.element.ExecutableElement
```

(`ElementKind`, `Modifier`, `MemPos` and `Method` are already imported.)

- [ ] **Step 5: Run the three tests to verify they pass**

Run: `./gradlew test --tests '*ReachesTheOverride*' --tests '*SuperCallDoesNot*'`
Expected: PASS, three tests.

- [ ] **Step 6: Rewrite the interface test, which now asserts the opposite**

`aCallThroughAnInterfaceIsOpaqueAndACallWithABodyIsNot` asserts `"read" to "EXTERNAL"`, which is
exactly the behaviour this change removes: `Source source = new Doubling()` gives `source` a MemPos
typed `Doubling`, so the interface call now dispatches to `Doubling.read`. Replace the whole test
with:

```kotlin
    /**
     * A call through an interface reaches the implementation its receiver was constructed as.
     *
     * This was the fixture that asserted the opposite: a declaration with no body was left
     * unregistered, so the call went opaque rather than guessing which implementation it reached.
     * The refusal was right and the reason it gave was wrong - which implementation a call reaches
     * is decided by the receiver's class, and the receiver's class is *known* here, because
     * `new Doubling()` is on the page two lines up.
     *
     * What is still refused is a receiver that says nothing: no tracked object means no dispatch,
     * and the opaque path is what that draws.
     */
    @Test
    fun aCallThroughAnInterfaceReachesTheImplementationItsReceiverHas() {
        val graph = buildGraph("abstractMethod", listOf("App.java"))
        val edges = edgeLabels(graph)
        assertFalse("read" to "EXTERNAL" in nodeTypes(graph), "the interface call is still opaque: ${nodeTypes(graph)}")
        assertTrue(reaches(graph, "3", "viaInterface"), "the interface call drops its argument: $graph")
        assertTrue(reaches(graph, "4", "viaClass"), "the value does not survive the direct call: $graph")
        assertEquals(2, edges.count { it == "seed" to "+" }, "both calls should inline the same body: $edges")
    }
```

- [ ] **Step 7: Register the three new goldens**

In `AppTest.kt`, next to `@Test fun abstractMethod() = codeflow("abstractMethod", listOf("App.java"))`
(`:1560`), add:

```kotlin
    @Test fun overriddenMethod() = codeflow("overriddenMethod", listOf("App.java"))
    @Test fun overriddenSuper() = codeflow("overriddenSuper", listOf("App.java"))
    @Test fun templateMethod() = codeflow("templateMethod", listOf("App.java"))
```

- [ ] **Step 8: Generate the new goldens and see which existing ones moved**

Run: `UPDATE_SNAPSHOTS=1 ./gradlew test`
Run: `git status --short -- '*truth.md'`

Expected: three new untracked files under the new fixtures, and **`abstractMethod/truth.md` modified**.
If any *other* golden moved, stop and explain it before continuing — a fixture with one implementation
per receiver should resolve to exactly the method it resolved to before, so a move means dispatch is
picking a different body somewhere it should not.

- [ ] **Step 9: Verify `abstractMethod` structurally**

```bash
cat > /tmp/norm.py <<'EOF'
import re, sys
node = re.compile(r'^\s*n\d+\[(.*)\]:::(\w+)\s*$')
edge = re.compile(r'^\s*n\d+\[(.*?)\]:::(\w+) -->(?:\|(\w+)\|)? n\d+\[(.*?)\]:::(\w+)\s*$')
out = []
for line in sys.stdin:
    if (m := edge.match(line)):
        out.append(f"EDGE {m.group(1)}:{m.group(2)} -{m.group(3) or 'flow'}-> {m.group(4)}:{m.group(5)}")
    elif (m := node.match(line)):
        out.append(f"NODE {m.group(1)}:{m.group(2)}")
print('\n'.join(sorted(out)))
EOF
p=app/src/test/resources/abstractMethod/truth.md
diff <(git show HEAD:$p | python3 /tmp/norm.py) <(python3 /tmp/norm.py < $p)
```

Expected: the `read:EXTERNAL` node and its two edges disappear, replaced by a second inlined copy of
`Doubling.read` — a `seed:FUNC_PARAM`, a `+:BIN_OP`, a `read:RETURN` and their edges. Anything else
needs explaining.

- [ ] **Step 10: Run everything**

Run: `./gradlew build`
Expected: `SUCCESS: Executed 151 tests` (145 + 3 behaviour + 3 golden).

Run: `npm test`
Expected: 7 passed. (Unchanged — no exporter or viewer change in this plan, so this is a check that
none crept in.)

- [ ] **Step 11: Commit**

```bash
git add app/src/main/kotlin/codeflow/ir/IrGraphBuilder.kt \
        app/src/test/kotlin/codeflow/AppTest.kt \
        app/src/test/resources/overriddenMethod \
        app/src/test/resources/overriddenSuper \
        app/src/test/resources/templateMethod \
        app/src/test/resources/abstractMethod/truth.md
git commit -m "Run the method the receiver has, not the one its declared type names"
```

---

### Task 5: Say so in the documentation

Three places currently assert what this change makes false.

**Files:**
- Modify: `CLAUDE.md` (the `AstProcessor` bullet in "Architecture", and "Calls are inlined per call site")
- Modify: `docs/plans/codemap-port-findings.md:186` ("Deliberately not ported")
- Modify: `docs/if-written-again.md` (add to §3, the points-to section)

- [ ] **Step 1: Correct the `AstProcessor` bullet in `CLAUDE.md`**

It currently says a method with no body is skipped because "picking one would be a guess drawn as
fact". The skipping is unchanged; the reason is now narrower. Replace that sentence's tail with:

```
   under its own. A method with no body is skipped: there is nothing to inline. Which implementation
   a call to an abstract or interface method reaches is decided at run time by the receiver's class,
   and that class is now *asked* rather than guessed - see "Dispatch is on the object" below. A
   receiver that names no object still takes the opaque `EXTERNAL` path.
```

- [ ] **Step 2: Add the section to `CLAUDE.md`**

Add after the "Calls are inlined per call site" section:

```markdown
### Dispatch is on the object, not on the declared type

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
```

- [ ] **Step 3: Correct `docs/plans/codemap-port-findings.md`**

Under "Deliberately not ported", replace the polymorphic-dispatch paragraph with:

```markdown
**Polymorphic dispatch** — `polymorphism`, `polymorphism2`, `polymorphism3`, codemap's headline
fixtures and the whole of its README. **Since done**, in `Frame.resolve` — and it turned out to be a
bug rather than a gap: the case codeflow got *wrong* was not the interface call, which honestly went
opaque, but the overridden concrete method, which confidently inlined the superclass body. See
"Dispatch is on the object" in CLAUDE.md. What is still not done is a receiver that could be several
implementations: that needs a join box and stays `EXTERNAL` for now.
```

- [ ] **Step 4: Add to `docs/if-written-again.md` §3**

Append to the section on points-to:

```markdown
**Since partly done.** Dispatch reads the points-to set: a call resolves to the implementation the
receiver's `MemPos` was constructed as, rather than to whichever method javac resolved against the
declared type. That is the first thing in codeflow to *consume* the alias model rather than only
maintain it, and it inherits the model's open edge — a receiver assigned inside a loop from a
variable declared above it dispatches on an incomplete set, because a phi whose back edge has not
been drawn yet contributes no objects.
```

- [ ] **Step 5: Verify and commit**

Run: `./gradlew build`
Expected: `SUCCESS: Executed 151 tests`.

```bash
git add CLAUDE.md docs/plans/codemap-port-findings.md docs/if-written-again.md
git commit -m "Record that dispatch asks the object rather than the declared type"
git push origin rebuild-batch-1
```

---

## Stage 2, deliberately not in this plan

When the receiver's objects name **several** implementations, Stage 1 draws `EXTERNAL`. Stage 2 would
inline each and feed their return nodes into a `dispatch` box — `addSelection`, a `BIN_OP` like
`ternary` and `if`, with each implementation's edge labelled with its concrete class. That needs
`GraphNode.Edge` to carry an optional label overriding `kind.label`, which is the one thing that
touches all four exporters and the viewer.

It is held back because its value is an empirical question neither the code nor this document can
answer: how often a receiver at a real call site is genuinely two objects rather than one, weighed
against N inlined bodies per call site on a tool whose node count is already the wall the viewer
exists to avoid. Answer it by running Stage 1 on real corpora and counting how often `resolve`
returns null with a `types` list longer than one — worth a `logger.debug` line in `resolve` for
exactly that purpose.

No gate is needed on that box. Where an `if` decided the receiver's type, the receiver variable is
already a gated join carrying the condition, so `== --> if --> b --> dispatch` is on the page as soon
as the receiver is drawn into the box.
