# `--pure Class`: a boundary with no side effects — design (short)

Status: approved (user choice 2026-11-16, "Add --pure", after asking that `dateFormat` not be a
boundary: "there's probably no side effects on it"). Builds on `docs/decisions/calls-and-dispatch.md`
(`--boundary`).

## Intent
Every `--boundary` call takes the order token, so in Fineract `command.dateFormat()`,
`command.locale()`, finders and calculators sit in the `boundaries` chain beside `save` and
`postJournalEntries`. The chain is meant to say which side effects ran in which order; pure reads in it
are noise, and they hide the order that matters.

## Approaches
- **`--pure Class`: a boundary whose calls skip the order token** (chosen). One box per call as today,
  parameters in, value out; no `boundaries` in or out. Per class, like `--boundary`.
- Drop the class from `--boundary` (config only): its bodies are read again, and JsonCommand's reads
  inline to FromJsonHelper/JsonParserHelper — the duplicated receivers the boundary was added to stop.
- Per-method `--boundary Class#method`: finer, but a pure method would still take the token; order
  and "not read" are separate questions.

## Changes
1. `Args`: `--pure Class` / `--pure=Class`, repeatable, like `--boundary`.
2. `AstReader.process/summarise/analyse` take `pures`; matched by `selectBoundary` (same errors).
   A class named by both flags fails: which one was meant is not ours to guess.
3. `GlobalContext`: pure classes are boundaries too (`isBoundary` true, bodyless methods registered);
   `isPure(method)` says which.
4. `Lowering.assumeBoundary`: a pure method emits `Call` without `ReadBoundaryOrder` /
   `WriteBoundaryOrder`; the rest unchanged (`call <name>`, `Return`).
5. Fineract `.args`: JsonCommand → `--pure`.

## Not changed
Exporters, viewer, condensed grouping (a pure method is a boundary box like any other), summaries
beyond losing the token they already ignored.

## Test
- AppTest on `boundaryOrder` with `--pure Gateway`: the four `call` boxes are there, each fed by its
  argument; no `boundaries` node and no ORDER edge (pair: the same fixture with `--boundary` has both).
- A class given to both flags fails, naming it.
