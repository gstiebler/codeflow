# Boundaries reached through injected interfaces, and an arguments file — design (short)

Status: approved (user request 2026-11-16, "Generate the graphs for Fineract. I'm particularly
interested in the condensed graph. You may need to find the entry points, the boundaries, and have a
way to define them before running."; calls marked *call* are the controller's). Builds on
`2026-11-15-boundary-functions-design.md`, `docs/decisions/calls-and-dispatch.md`.

## Intent
A Fineract service talks to everything outside it through Spring-injected, interface-typed fields:
`loanAssembler.assembleFromInUserScope(id)`, `journalEntryPoster.post…(t)`,
`businessEventNotifierService.notify…(e)`. The receiver's objects say nothing (nobody `new`ed them),
so `Dispatch.resolve` takes javac's static answer — the interface method — which has no body, was never
registered (`AstProcessor.visitMethod`, "No body to inline"), and the call is a bare `EXTERNAL`.
`--boundary LoanAssembler` changes nothing today: no `call` box, no order token, no Condensed group.
Measured on Fineract `ProgressiveLoanScheduleGenerator#generate --boundary EMICalculator`: zero
boundary nodes. These calls are exactly the boundaries the condensed view exists to show.

## Changes
1. **Bodyless methods of a boundary class are boundary methods.** An abstract or interface method
   declared directly on a `--boundary` class is registered and lowered with the assumed
   implementation (`Lowering.assumeBoundary` never reads a body). Bodyless methods of other classes
   stay unregistered, as today.
2. **No dispatch past a boundary** (*call*). A call javac resolves to a method declared on a boundary
   class is that boundary method, whatever objects the receiver holds: a boundary is the declared API,
   not one implementation, and its bodies are not read anyway. Decided in `Dispatch.resolve`, so the
   summarizer agrees. Today's one behaviour change: a concrete boundary class whose receiver's objects
   are a non-boundary subclass now draws the boundary instead of the subclass body — say if a fixture moves.
   A method inherited from a supertype that is not itself a boundary is not a boundary (matched on the
   declaring class, as today).
3. **Arguments file** (*call*: javac's convention). A command-line argument `@path` is replaced by the
   contents of that file: each line split on whitespace into arguments; blank lines and lines whose
   first non-blank character is `#` skipped. No nesting (`@` inside the file is an ordinary argument).
   A missing file fails loudly. Any number of `@file`s, mixed with ordinary arguments, in order.
   So `codeflow <dir> @repayment.args --html` reads its roots and boundaries from a file a person edits.

## Not changed
Runs without `--boundary` and without `@`: every golden byte-identical. Viewer, payload, exporters,
order token, split. Calls javac cannot resolve at all stay `EXTERNAL` (the paused outside-calls design).

## Test
- New fixture `boundaryInterface/` (*call*): interface `Store` (`save(x)`, `load()`), class
  `StoreImpl implements Store` whose bodies do something distinctive, class `Service` with a field
  `Store store` set from a constructor parameter, root `Service#run(int a)` calling `store.save(a)`
  then `store.load()`; plus a second root/method where the receiver is a known `new StoreImpl()`;
  `--boundary Store`. Behaviour (`reaches` / `edgeLabels`):
  - `a` reaches `call save`; `call save` → order token → `call load` (ORDER);
  - `StoreImpl`'s bodies are drawn nowhere — with the unknown receiver and with the known
    `new StoreImpl()` (fails against dispatching to the implementation);
  - the same sources without `--boundary` draw `save` as today's bare `EXTERNAL` (fails against
    registering every bodyless method).
  - the JSON block for `Store#save` carries `boundary: true` and `method`, so the npm corpus sweep
    covers it in the condensed model.
- Arguments file (AppTest): a file with comments, blank lines and two arguments per line gives the
  same document as the same arguments inline; a missing file throws naming it.
- InvariantsTest / SummaryParityTest stay green on every fixture.
- `./gradlew test`, `npm test`, `npm run test:browser` green.
- Docs: `docs/decisions/calls-and-dispatch.md` `--boundary` paragraph (interfaces; no dispatch past a
  boundary, why); `CLAUDE.md` commands block gains the `@file` line.
