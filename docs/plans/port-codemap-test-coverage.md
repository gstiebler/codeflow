# Port what codemap's tests cover

## Context

[codemap](https://github.com/gstiebler/codemap) is this project's C++ predecessor: same idea, same
output shape (a `.dot` dataflow graph with method boundaries as nested subgraphs), 50 fixtures each
pinned by a committed golden file. Its test harness is not worth taking — `TestsUtil.baseTest` diffs
`generated.dot` against `<name>.dot` line-for-line, which certifies unchanged rather than correct,
the weakness CLAUDE.md already names about our own goldens. Its `GraphCompare` does structural,
id-insensitive matching, but nothing calls it.

What is worth taking is **which constructs its fixtures pin down**. Mapping each C++ fixture to its
Java equivalent and running that through codeflow turns up four graphs we draw wrong today, silently
— the failure mode this project is organised against.

Every claim below was checked by running the built CLI against a Java port of the fixture. The
probes are not committed; each task carries the fixture that replaces them.

## 0. The build does not run on the JDK it pins

`app/build.gradle` sets `jvmToolchain(21)` and `.github/workflows/gradle.yml` sets up JDK 21, but
`gradle/wrapper/gradle-wrapper.properties` pins Gradle 8.0, which cannot *run* on Java 21 — Groovy
fails to compile the settings file with `Unsupported class file major version 65`. Gradle 8.5 was the
first release supporting Java 21.

CI on `main` has been red since d604cb8 for this reason, failing in 8s before a single test runs.
Nothing else in this plan can be verified until it is fixed.

## 1. Silently wrong today

### 1a. No join at a branch — `nested_if`, `switch`, `if_pointer`, `if_member`

codemap emits an explicit `If` merge node (`shape=invtriangle`) at every join, fed by the condition
and both candidate values. In `nested_if.dot`, `int c = a` reads the merge output, so `c` traces back
to all of `3`, `7`, `9`, `cond1`, `cond2`.

codeflow keeps the last write and drops the rest, with no sign anything was dropped. `if1/truth.md`
records it: `c` gets only `b = 13`, never the pre-if `b`. The same holds for `switch`, for
`try`/`catch`/`finally` (`b` gets only the `finally` value; the `try` and `catch` writes dangle), and
for object aliasing — `if (…) p = i1; else p = i2; int a = p.m;` reaches only `i2`'s field.

This is the largest item here and the one that moves every branching golden.

### 1b. `switch` as a statement is not modelled at all — `switch`

Worse than the ifs. The selector's read is *invisible*: in the probe, the `b` of `switch (b)` has
zero outgoing edges, where codemap's `switch.dot` has `b -> ==` three times plus fall-through from
case 2 into case 3. `visitSwitchExpression` exists; there is no `visitSwitch`, and a statement is not
covered by the `MODELLED_EXPRESSIONS` gate, so it fails the way CLAUDE.md warns statements fail —
somewhere else, later, blaming a line that is not at fault.

### 1c. A field written two call levels deep never reaches the caller — `deep_method`

```java
class C { int a; void inner() { a = 18; } void outer() { inner(); } }
// main: c.a = 17; c.outer(); int x = c.a;
```

`x` reads `17`. One level (`c.setA(18)`) works; two does not. codemap's `deep_method.dot` carries
`instance.a` in `calledFuncA` → `If` → `instance.a` → `x`, so its golden covers this.

### 1d. Field initializers and initializer blocks never run — `constructor_chain`

codemap's C++ member-init lists are Java field initializers, and codeflow skips them:

```java
class Outer { Inner in = new Inner(); int n = 5; Outer() { n = n + 1; } }
```

`n = n + 1` reads an `n` with nothing flowing in, and `o.in.v` comes out as a bare `EXTERNAL` node —
the object was never constructed, so the chain dies there. Instance blocks (`{ y = 7; }`) and static
blocks (`static { x = 42; }`) are skipped the same way.

### 1e. Qualified static field access is broken — `static_member`, `global_var`

`Holder.globalVar` logs `Variable not found: JNodeId=(name: 'Holder')` and degrades to `EXTERNAL`.
Two separate reads collapse onto one node, and the write inside `Holder.func()` never connects to the
read after it. Unqualified `globalVar` inside the declaring class works.

### 1f. Enum constants with constructor arguments — `enum`

Our `enumConstant` fixture covers bare constants. codemap's covers constants with initializers, which
in Java means a constructor: `FIRST(0)`. The enum constructor never runs, `MyEnum.FIRST` goes
`EXTERNAL`, and `get()` returns a `v` with nothing behind it.

## 2. Already correct, but nothing asserts it

Verified working; fixtures here are regression value only.

| codemap | Java |
|---|---|
| `return` | early and multiple returns converging on the RETURN node |
| `inheritance` | a field declared on a superclass, read through the subclass |
| `parent_class_method` | `super.m()`, an inherited instance method, an inherited static |
| `write_pointed_inside_block` | a write through an alias in a nested block, read after it |
| `template` | a generic class, a type-parameter field, a method returning `T` |
| `files/sub_folder` | sources in a subdirectory and a second package |

## 3. Found on the way, not from codemap

- `TYPE_CAST` is not in `MODELLED_EXPRESSIONS`, so `(String) o` aborts the run. This fails loudly, as
  designed, but a cast is ordinary Java and the gate should have a visitor behind it.
- codemap tags every node with `startinglines=<line>` and asserts it in the goldens. codeflow carries
  no source position into any exporter, so the viewer cannot answer "which line is this?".

## 4. Deliberately not ported

- **Polymorphic dispatch** — `polymorphism`, `polymorphism2`, `polymorphism3`, codemap's headline
  fixtures. codemap tracks the object through the pointer and dispatches to the concrete class;
  codeflow refuses to guess, and `abstractMethod` is an existing test asserting the opaque
  `EXTERNAL` path. The receiver's concrete class *is* known at the call site (`h` is an
  `OBJ_VARIABLE` carrying `HandlerA`'s `MemPos`), so this is a reversal of a stated decision rather
  than a gap, and belongs in its own plan with its own argument.
- `destructor`, `operator_overload`, `typedef`, `pointer`, `reference`, `namespace`, `char_str` — no
  Java equivalent, or already covered. `header_only_func` is `abstractMethod`.

## Tasks

Ordered. Group A lands green and buys the regression net before anything is touched; group B is
red-then-green per bug; C is the design change and goes last because it moves every branching
golden.

Done so far: the wrapper, all of group A, and B0/B1/B5. What each one turned out to be is in
`codemap-port-findings.md`; this list is only what is left.

- [x] **0.** Raise the Gradle wrapper to a release that runs on Java 21, so `./gradlew build` and CI
      work at all. — `1a411d9`
- [x] **A1.** `earlyReturn` fixture — multiple and early returns converge on the RETURN node. — `2f6fb5c`
- [x] **A2.** `inheritance` fixture — a superclass field read through the subclass. — `2f6fb5c`
- [x] **A3.** `parentMethod` fixture — `super.m()`, inherited instance method, inherited static. — `2f6fb5c`
- [x] **A4.** `aliasInBlock` fixture — a write through an alias inside a nested block. — `2f6fb5c`
- [x] **A5.** `generic` fixture — generic class, type-parameter field, method returning `T`. — `2f6fb5c`
- [x] **A6.** `subpackage` fixture — sources in a subdirectory, in a second package. — `2f6fb5c`
- [x] **B0.** An assignment reads the value the variable held going in. Not from codemap and not in
      the original list: found while fixing B1, where `total = total + by` stayed broken after the
      static field was tracked. — `98c8422`
- [x] **B1.** Qualified static field access: `Class.field` read and written across methods. — `98c8422`
- [ ] **B2.** Field initializers and instance initializer blocks run as part of construction.
- [ ] **B3.** Static initializer blocks. **Blocked on a decision** — see below.
- [ ] **B4.** Enum constants with constructor arguments.
- [x] **B5.** A field written two or more call levels deep reaches the caller. Widened while being
      fixed: `super.m()` loses its receiver the same way an unqualified call does, so the fix covers
      both, and one golden moved because an inherited method had been reading a field nothing had
      assigned. — `3bb1b72`
- [ ] **B6.** `switch` as a statement: the selector is read, each case label is compared.
- [ ] **C1.** A join node at `if`, `switch` and `try`/`catch`/`finally`, so a value written in one
      branch does not silently replace the other.
- [ ] **D1.** A visitor for `TYPE_CAST`, then its kind in `MODELLED_EXPRESSIONS`.
- [ ] **D2.** Carry source line onto every node and out through the exporters.
- [ ] **D3.** Wire up or delete the untested `codemap` and `ls` fixtures — CLAUDE.md flags both, and
      `codemap/truth.md` is stale in the pre-serial id format.

Each of B1–B6 and C1 lands as a fixture plus an `edgeLabels` behaviour test, per CLAUDE.md: new
behaviour needs an assertion that can fail on a graph that has never been correct, not a regenerated
snapshot. B5 is why that is not sufficient on its own — see the second lesson in the findings.

B2, B3 and B4 are one bug wearing three hats: nothing outside a method body is ever walked, so a
field initializer, an initializer block and an enum constant's constructor arguments are all skipped
alike. They are listed separately because B3 needs an answer B2 and B4 do not.

### The open decision, B3

An instance initializer has a `new` to attach to, so B2 knows when it runs. A static block has no
call site at all — it runs when the class is first touched, which is control flow, and codeflow
models none. Two answers:

1. **Run it once before the entry point.** Every static a block assigns then has a value, and reads
   of it connect. The order between two classes' blocks is arbitrary, and would be drawn as fact.
2. **Leave it out and say so.** Statics assigned only in a block read as fields nothing has
   assigned, which is what `unassigned` already draws for an instance field in the same position —
   honest, and consistent with the treatment next door.

Option 1 is the default unless someone objects, because a static block is usually the *only* writer
of what it assigns, and option 2 draws its value as arriving from nowhere.
