# Calls without source are boundaries — design (short)

Status: approved (user request 2026-11-15, scope "calls only"; calls marked *call* are the controller's).

## Intent
A call into code we do not have — the JDK, a library, a name javac could not resolve — is drawn
today as one bare `EXTERNAL` box. The user wants it drawn the way a configured boundary is
(`docs/superpowers/specs/2026-11-15-boundary-functions-design.md`): the assumed implementation
`params → call<name> → result → RETURN → caller`. One shape for every call we do not read.

## Scope: calls only
Becomes a boundary:
- A method call whose declaration is **outside the analysed sources**, or that javac could not
  resolve at all (`Call.target` not an `ExecutableElement`), when no source body could run instead.
- `new X(...)` where `X` is a class outside the sources. Field initializers still run as today; the
  value still carries the created object.
- `super(...)` / `this(...)` to a constructor outside the sources (today it draws nothing): a void
  boundary, the arguments flow into `call<X>` and nothing comes out (*call*).

Stays `EXTERNAL` (not code without source; drawing a boundary would hide why the analysis stopped):
- several implementations, or a receiver whose objects say nothing and a declaration with no body
  in the sources (abstract/interface method declared in the sources);
- the recursion cut (`isBeingInlined`);
- `new` of a source class whose only constructor is the one attribution inserted;
- field reads on outside receivers (`System.out`, a field inherited from an outside class),
  enum constants, method references, and the `call<name>` box itself.

The rule must be computed, not guessed from a null: `Dispatch.resolve` returns null for "no source"
and for "several implementations" alike. A call is outside only when no candidate is a source body:
target unresolved, or the declaration is not in the sources **and** no receiver type has a source
implementation (*call*: a mix of outside and source implementations is several → `EXTERNAL`).
Put that decision beside `Dispatch.resolve` so the summarizer could ask it too.

## The shape
Exactly the configured-boundary shape, so the two read the same:
- A nested block for the callee (Mermaid subgraph), with one `FUNC_PARAM` per argument, an
  `EXTERNAL` node labelled `call<name>`, a `VARIABLE` `result`, and the `RETURN`, whose node is the
  call's value at the caller.
- `<name>`: the method's simple name (`list.add(x)` → `calladd`); for a constructor, the class's
  simple name (`new ArrayList<>()` → `callArrayList`).
- Parameter names: the element's parameter names when javac has an element; `p0, p1, …` when not (*call*).
- **The receiver is an input.** An instance call's receiver flows into `call<name>` (as a `this`
  parameter), as it flows into the `EXTERNAL` box today. Dropping it would silently cut
  `list → list.get(0)`. *Call:* the same fix applies to configured boundaries — an instance method of a
  `--boundary` class currently passes only its parameters, so `this` is silently dropped. Fix both.
- void: when javac says the return type is `void`, no `result` and no `RETURN`. With no element the
  return type is unknown: draw `result` and `RETURN` (an unused value is harmless; a missing one is not) (*call*).

## Not changed
- `--summaries`: outside calls are already ext atoms and sinks there (*call*: labels unchanged).
  Parity (P<i>→RET) must stay 100% on the fixtures.
- Node types, colours, viewer: none. `EXTERNAL` stays a type.

## Test
- New fixture `app/src/test/resources/outsideCalls/` (golden + behaviour tests) covering:
  a JDK static call (`Math.max(a, b)`), a JDK instance call on a JDK object (`sb.append(x)`),
  a call javac cannot resolve (an import of a package not in the sources), `new ArrayList<>()`,
  `super(n)` to an outside class, and a void JDK call.
- Behaviour tests (`reaches` / `edgeLabels`): arguments reach `call<name>`, the **receiver** reaches
  `call<name>`, `call<name>` reaches `result` reaches the caller's use; param names `p0…` for the
  unresolved call; the void one has no `result`.
- Still `EXTERNAL`, asserted: ambiguous dispatch, recursion cut, `System.out`, an enum constant
  (existing fixtures may already cover; assert on them).
- A configured instance boundary method: its receiver reaches `call<name>` (fails today).
- Every golden that moves: structural diff (sorted `label:TYPE` multisets, ids stripped, old from
  `git show HEAD:<path>`), and every leftover explained in the report.
- Fineract smoke (`scripts/smoke.sh`): no crash, no skipped root; report time and memory against
  ~70 s / 742 MB (the graph grows — every library call becomes a block).
