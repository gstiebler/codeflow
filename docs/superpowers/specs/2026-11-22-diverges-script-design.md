# `scripts/diverges.py`: sibling comparison over `--summaries` — design (short)

Status: approved (user, 2026-11-22: "Yes, build it", after
`docs/research/2026-11-22-codeflow-usefulness/review.md` leaning 1). Built the same day; this page says what
was built. My own calls are marked *call*.

## Intent
"Path A does X, its sibling B forgets to" is the bug shape the study found most often. Two real bugs
(Kafka #24128, Keycloak #49604) were found by hand in today's `--summaries` JSON. This script is that
reading, written down once, so it can be run on the other cases **before** any engine work. Where it
stops finding them is where the engine work starts.

## What it is
- *call*: a Python 3 script, standard library only, reading the JSON document. Not a CLI flag: nothing
  in `Summarizer` changes, and a query that turns out useless is deleted without touching the tool.
- It refuses a document whose `format`/`version` is not `codeflow-summaries` / `1`.
- stdout is the result (text, or `--json`); nothing else is printed there. `-` reads the document on stdin.

## Effects of one method
Read off its `flows` and `gaps`, nothing else:

| Effect | From |
|---|---|
| `write F` | a flow into `this.F`, `P<i>.F` or `RET.F`; or a gap `F written …` on an object the summary cannot name |
| `call L(F)` | a flow from a field `F` into `ext-arg:L@pos` |
| `call L` | anything at all reaches `L` |
| `read F` | a flow out of a field; or a gap `F read …` on an object the summary cannot name |

- *call*: a call's key is its **label and the field reaching it**, not its position. Siblings that do
  the same thing in their own bodies have different positions. Positions are kept as evidence.
- *call*: the JSON does not say which source is the receiver, so `put(members)` means "`members`
  reaches `put`". The engine would know; the script's docstring says so.
- *call*: class and field are split using the class names in the method ids, because a field path is
  printed as one dotted string (`…SP.PrincipalNameMapping.policy` is a field of the nested class). A path
  whose class has no method in the document is split at its last dot.
- *call*: a read or write known only from a gap row has the **same key** as one known from a flow. Keycloak
  #49604 is found in gap rows; a separate `?:F` key would have made each sibling's reads incomparable.
- A fixed list of mutator names (`put`, `add`, `remove`, `set`, …) **orders** rows. It never hides one.

## Families
`diverges.py <summaries.json> <selector>`:

| Selector | Siblings | Case |
|---|---|---|
| `overrides:NAME` | every method with that simple name and the same parameter list, across classes | Kafka #24128 |
| `stem:GLOB` | methods whose simple name matches | none: too wide on Kafka #24108 |
| `A B …` | methods named one by one (each a part of exactly one id) | Kafka #24108 |
| `fields:CLASS` | the fields of one class; reports each method touching most of them and missing some | Keycloak #49604 |
| `sink:LABEL[@AT]` | every method something reaches the call `LABEL` in, grouped by what feeds it | Kafka #24187, #23856 |

- *call*: `overrides:` is name plus parameter list. The JSON has no override relation.
- *call*: a class's fields are the ones the document reads or writes. A field nobody touches is not in it.
- *call*: `sink:` is the form of the sketch's `callers:` that the JSON supports. A feed is keyed by its last
  field, by `g()` for an opaque call's result, or as `a parameter`: which parameter is not comparable from one
  method to the next. A callee's sinks are inlined into its callers, so a caller is listed under its callee's
  way unless it adds a feed.
- *call*: `anchor:` and `cowrite:` are **not** built. Measured on their cases (Kafka #24155, #23530): a summary
  holds no gates and no order, and the co-written fields never meet in one method. Both need the engine.

## Output
- **Families.** One row per effect that at least one sibling lacks, grouped by who lacks it:
  `in 1,2,4, not in 3: 72 effects`, then the rows with up to three positions from the siblings that have it.
  - Groups lacking in the fewest siblings come first (the majority vote); a firm absence before an unknown
    one; inside a group, writes, mutator calls, other calls, reads.
  - **Absent behind a gap is unknown.** A sibling with any gap gets `?` on every absence, and its line in the
    header counts its gaps by kind. *call*: every gap kind counts; the script cannot tell which paths a gap
    sits on, so it does not try.
- `fields:` prints, per method that touches at least half of the class's fields (`--min`), the fields
  it does not touch.
- `sink:` prints the feeds every method shares, then each way: the feeds beyond those, and its methods. The
  way adding least comes first.
- `--json` holds the same, with every gap count.

**"Firm" is as firm as the summary.** A call chained on a type outside the sources loses its receiver with no
gap counted (`Lowering.isTypeName`, found while measuring; see
`docs/research/2026-11-22-codeflow-usefulness/review.md`). Until that is fixed, a sibling with no gaps can
still be missing a flow.

## Test
- Real output, trimmed: `scripts/testdata/` holds three documents as `--summaries` printed them.
  - #24128: `call put(ModernGroup.members)` is present in `ShareGroup`, absent (firm) in `ConsumerGroup`.
  - #49604: `build` misses exactly `turnOffChangeSessionIdOnLogin`.
  - #24187: the two deserialising methods share `metadata()` and `wrap()`; `fromClassicGroup` adds `get()`.
- Hand-made documents for the rules: `?` when the absent sibling has a gap; majority order with three
  siblings; an ambiguous or empty selector fails; a wrong `format` fails; a `sink:` position names one call.
- Each rule was checked by changing the script and watching a test fail.
- CI: one step, `python3 -m unittest discover -s scripts -p '*_test.py'`.

## Not changed
`Summarizer`, the JSON format, the exporters, the viewer.
