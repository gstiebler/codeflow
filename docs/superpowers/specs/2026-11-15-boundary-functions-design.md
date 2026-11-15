# Boundary functions — design (short)

Status: approved (user request 2026-11-15; calls marked *call* are the controller's).

## Intent
Some methods live outside what we want to read: other packages, frameworks, things a team
configures. codeflow should not read their bodies. It should assume a fixed implementation,
so a call to one is drawn the same way every time and the graph stays honest about what flowed in.

## The assumed implementation
For a boundary method `T f(p0, p1, …)`:

```java
T f(p0, p1, …) {
    T result = call<f>(p0, p1, …);   // one opaque operation named "call" + the method name
    return result;
}
```

- Every parameter goes into one operation labelled `call<name>` (e.g. `callboundaryfunc1`).
- That operation is assigned to a local `result`, and `result` is returned.
- A `void` boundary method does the same, but has no return (*call*).
- The real body, if there is one, is never lowered.

## How it is built (*calls*)
- **Configured by class.** A repeatable CLI flag `--boundary <Class>` names a class; every method
  of it is a boundary. The class is matched the way `--from` matches its class part (simple or
  qualified name, resolved through javac `Element`s, never by file name). A `--boundary` that names
  no class in the sources fails loudly, like an unknown `--from`.
  For tests, the fixture passes `boundaries = ["Boundary"]`: everything in `Boundary.java`.
- **Lowering replaces the body.** When `Lowering` lowers a method whose enclosing class is a
  boundary, it emits the assumed implementation above instead of visiting the body. Everything
  downstream (IrGraphBuilder inlining, dispatch, `--summaries`) is unchanged and sees an ordinary
  method. "What a name means is per method" stays in Lowering.
- **The operation is the existing opaque-call instruction**, labelled `call<name>`, with the
  parameters as its arguments and no receiver. It is drawn as an `EXTERNAL` node — which is what
  it is: an operation whose inside we do not read. No new `Insn`, node type, colour, or viewer change.
- `result` is an ordinary local (`VARIABLE`), so the graph reads
  `args → params → call<name> → result → RETURN f → caller`.

## Test
New fixture `app/src/test/resources/boundary/` with `App.java` and `Boundary.java`, root
`--from App#entrypoint`:

```java
static void entrypoint(int a, int b, int c) {
    int result1 = Boundary.boundaryfunc1(a);
    int result2 = 0;
    if (a > b) {
        result2 = Boundary.boundaryfunc2(result1, a);
        Boundary.boundaryfunc3(result2, b, c);
    } else {
        Boundary.boundaryfunc3(a, b, c);
        Boundary.boundaryfunc2(result2, a);
    }
}
```

`Boundary`'s methods are static, return `int`, and have bodies that must NOT show up (e.g. return a
distinctive literal), proving the body is not read.

- Golden `snapshot.md` (Mermaid), written by the normal `codeflow(...)` fixture path.
- Behaviour tests (`reaches` / `edgeLabels`):
  - `a` reaches `callboundaryfunc1`; `callboundaryfunc1` reaches `result`; `result1` reaches
    `callboundaryfunc2`; `result2` (the `if` phi / written value) reaches the `callboundaryfunc3`
    in the true arm; `b` and `c` reach `callboundaryfunc3`.
  - The body's distinctive literal is absent with the boundary configured, and present without it
    (proves the gate, and that the test fails against a wrong implementation).
  - An unknown `--boundary` class fails loudly.
  - `--summaries` on the fixture: `Boundary#boundaryfunc2` has `P0` and `P1` reaching
    `ext:callboundaryfunc2@…` and that atom reaching `RET`.

## Not now
- Boundaries without source (outside the analysed sources) — they are `EXTERNAL` calls already.
- Boundary rules by package, annotation, or config file.
- Typing boundaries (net/db, in/out).
