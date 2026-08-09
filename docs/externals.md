# EXTERNAL

`EXTERNAL` is the node codeflow draws where it stopped reading. Arguments and receiver flow in, a
value flows out, and nothing inside is claimed. It is the most common node type on real input — **365
of 883 nodes, 41%,** on the Fineract measurement below — which makes it worth knowing exactly what it
says, and worth knowing that it currently says four different things in one colour.

This document is what the orange boxes turned out to be when a real corpus was measured, not what
they were designed to be.

## What it means, and what it is not

> Something from outside the analysed sources: a call with no body to inline, or a value such as an
> enum constant, that we can only treat as opaque.
>
> — `GraphNode.External`

The shape is deliberate and is the same shape `UNMODELLED` has: inputs in, value out. That is the
honest reading of "something here I cannot see inside", and it is the reading that keeps a value
traceable *across* the gap instead of ending the analysis at it. A call into `java.util` that dropped
its inputs on the floor would break every trace that passes through one, and almost every trace does.

`EXTERNAL` is **not** `UNMODELLED`, and the two are separate types on purpose:

| | says | what is behind it |
|---|---|---|
| `EXTERNAL` | a limit of the **sources** | code codeflow was never given |
| `UNMODELLED` | a limit of **codeflow** | code sitting in the corpus, readable, that the diagram is not showing |

Sharing a type would make a gap in the tool indistinguishable from a gap in the input.
`UNMODELLED` is dashed and makes the process exit non-zero; `EXTERNAL` is neither, because there is
nothing for the reader to fix.

## The five paths that produce one

Two of them are in the lowering (an instruction is built that has no body by construction), three are
in the graph builder (a `Call` was built and turned out to have nothing to enter).

### From `Lowering` — the `Opaque` instruction

`Opaque` is emitted where javac's answer failed the wrong-kind rule: *an `Element` is believed only
when its kind is the kind expected at that site.* `IrGraphBuilder` renders every `Opaque` through
`addExternal`.

1. **A bare name javac could not resolve** — `visitIdentifier`. A statically imported constant, or an
   enum constant in a `case` label whose enum is one module away, comes back as a `ClassSymbol` of
   kind `CLASS`. A name whose element is not `isVariable` is `Opaque`. Fixtures: `externalConstant`,
   `externalEnumSwitch`.

2. **A member javac could not find** — `visitMemberSelect`. javac reports it by handing back a
   `ClassSymbol` named `Owner.member`, kind `CLASS`, typed `ERROR`. A class whose supertype is
   outside the corpus has *every* inherited field come back that way. The receiver flows in when
   there is one (`other.code`) and does not when there is not (`this.code`) — the object a field was
   read from is the only thing on the page saying where the value came from. Fixture:
   `inheritedField`.

3. **A method reference** — `visitMemberReference`. `DisbursementData::disbursementDate` is a
   function value; nothing here calls it, so there is no call site to nest a body under. A qualifier
   that is a value (`charges::add`) is captured and flows in.

### From `IrGraphBuilder` — a `Call` with nowhere to go

`call()` takes the external path when `resolve` returns null or the method is already being inlined.

4. **No body to inline.** Either the method is genuinely outside the analysed sources, or
   `symbols.element(node, ElementKind.METHOD)` returned nothing and `insn.target` is null — the
   wrong-kind rule again, arriving at a call site. `resolve` opens with
   `insn.target as? ExecutableElement ?: return null`, so an unresolvable call and a JDK call reach
   the same box by different routes. **This is the path Lombok takes** — see below.

5. **Dispatch could not be settled.** `resolve` asks the receiver's objects, not the declared type.
   Several candidate implementations is a real answer needing a box that says "one of these", which
   does not exist yet, and picking one would be the guess the whole dispatch change exists to stop.
   So several takes `EXTERNAL`, and so does a receiver whose objects carry no type — a loop element,
   a caught exception, an object from outside the corpus.

   Recursion lands here too: `isBeingInlined` walks up the parent frames, and a method already on the
   stack becomes an opaque box rather than an infinite inline.

## What it looks like on real input

Root: `ProgressiveEMICalculator#calculateRateFactorPerPeriodForInterest`, Apache Fineract at
`de17f9632` (2026-07-15), run on javac 25.

### Narrow — one module (`fineract-progressive-loan/src/main/java`, 81 files)

```
codeflow: 23311 of 40457 references unresolved      (57.6%)
883 nodes, 1159 edges, 33 blocks

EXTERNAL 365 (41.3%)   BIN_OP 164   FUNC_PARAM 124   OBJ_VARIABLE 79
VARIABLE 57            LITERAL 57   RETURN 33        UNMODELLED 4
```

The most frequent labels:

```
 31 getFromDate      25 valueOf       23 getDueDate      21 multiply
 19 divide           13 DAYS          12 plus            12 getExactDifference
 12 getDayOfMonth    11 WEEKS         11 getDifferenceInDays
 11 UnsupportedOperationException     9 MONTHS
  9 getRepaymentPeriodFrequencyType   8 ZERO             8 isBefore
  8 getYear           7 setScale       7 isZero           7 equals
```

### The four causes, by weight

Sorting those labels by *why* they are opaque gives four groups, and they want four different
responses:

| cause | examples | weight | who can fix it |
|---|---|---|---|
| **Domain arithmetic** — the computation itself, as method calls | `multiply` 21, `divide` 19, `setScale` 7, `valueOf` 25, `ZERO`/`ONE` 9 | ~100 | codeflow |
| **Lombok** — accessors that do not exist without annotation processing | `getFromDate` 31, `getDueDate` 23, `getRepaymentPeriodFrequencyType` 9 | ~85 | codeflow |
| **Corpus scoping** — source in the repo, not in the directory passed | `getExactDifference` 12, `getDifferenceInDays` 11, `isZero` 7 | ~32 | the user |
| **Genuinely foreign** — JDK internals, `java.time`, exceptions | `plus`, `isBefore`, `getYear`, `UnsupportedOperationException` 11 | remainder | nobody |

Only the last group is what `EXTERNAL` was designed to say.

## Finding 1 — on a `BigDecimal` codebase, the arithmetic is external

The trace that mattered in this method ended:

```
multiply :EXTERNAL → divide :EXTERNAL → interestFractionPerPeriod :OBJ_VARIABLE
  → multiply :EXTERNAL → multiply :EXTERNAL → divide :EXTERNAL
```

That is `interestRate × fraction × actualDays ÷ calculatedDays` — the formula the module exists to
compute — rendered as five featureless boxes. Meanwhile the 164 `BIN_OP` nodes, the type codeflow
draws *best*, are `+` and `>` on loop counters and year numbers.

In any codebase that takes money seriously, `+` is a method call. So codeflow's strongest rendering
is spent on its least interesting arithmetic and its "I cannot see in here" rendering on its most
interesting. This is a property of the domain, not of Fineract.

## Finding 2 — Lombok is invisible, and silently so

`AstReader` passes `-proc:none`:

> `-proc:none` because there is no processor path to discover anything on.

Correct as stated — codeflow is pointed at bare directories with no classpath, so there is no jar to
load Lombok from. The consequence is that `@Getter` methods **do not exist** in the attributed tree.
javac cannot resolve `repaymentPeriod.getFromDate()`, the wrong-kind rule makes `insn.target` null,
and `resolve` returns null on its first line. The call becomes `EXTERNAL` by path 4.

In the module measured, **46 of 81 files import Lombok** (57%). `getFromDate` alone is 31 nodes — the
single most common label in the graph, and every one of them is a field read that codeflow could
model perfectly if the method existed.

The failure is quiet in the way this project cares about. Nothing is *wrong*: an accessor really is a
call whose body codeflow does not have, and drawing it opaque is honest about what was read. But the
reader is told "outside the analysed sources" about a field that is declared eight lines up in the
file they are looking at. The diagram is accurate and the impression it leaves is false.

There is a second cost. `getFromDate` as `EXTERNAL` produces a *fresh opaque value* each call, so two
reads of the same field in one method are two unrelated boxes. A modelled `ReadField` would resolve
both to one `JNodeId` keyed `(element, memPos)` — the identity machinery that `MemPos` exists for
never gets a chance to run.

## Finding 3 — widening the corpus moves the frontier, it does not remove it

The same root was run against a two-module corpus (`fineract-progressive-loan` + `fineract-core`, 876
files) built by symlinking both package roots into one directory.

| | narrow (81 files) | wide (876 files) |
|---|---|---|
| unresolved references | 23311 / 40457 — **57.6%** | 37072 / 165077 — **22.5%** |
| nodes | 883 | 1530 |
| blocks | 33 | 120 |
| `EXTERNAL` | 365 — **41.3%** | 404 — **26.4%** |

Per label, the outcome splits three ways, cleanly:

```
                                        EXTERNAL        inlined as a block
  getExactDifference                     12 ->   0        0 -> 12
  getDifferenceInDays                    11 ->   0        0 -> 11
  isZero                                  7 ->   0        0 ->  7
  isDateInRangeFromExclusiveToInclusive    2 ->   0        0 ->  2

  between                                 0 ->  23        0 ->  0
  toIntExact                              0 ->  12        0 ->  0
  IllegalArgumentException                0 ->  23        0 ->  0

  getFromDate                            31 ->  31        0 ->  0
  getDueDate                             23 ->  23        0 ->  0
  multiply                               21 ->  21        0 ->  0
  divide                                 19 ->  19        0 ->  0
  valueOf                                25 ->  25        0 ->  0
```

Three distinct behaviours, and each confirms a different thing:

- **Scoping externals dissolved.** `DateUtils` and `MathUtil` live in `fineract-core`; given them,
  codeflow inlined every call site. This cause is a user error, and the fix is to point codeflow at
  more source.
- **New externals appeared to replace them.** `DateUtils.getDifferenceInDays` inlined into
  `ChronoUnit.between` and `Math.toIntExact`, which are JDK and opaque. Absolute `EXTERNAL` count
  went *up*, 365 → 404. **Inlining an external method does not remove opacity — it pushes the
  frontier one layer deeper and usually widens it.** The graph grew 73% for a net gain in
  transparency of one call layer.
- **Lombok and JDK arithmetic did not move at all.** Identical counts. No amount of corpus is going
  to fix either, which is the cleanest available evidence that these two are codeflow's problem and
  not the user's.

The `EXTERNAL` *fraction* fell from 41% to 26%, but read that carefully: the denominator grew by 647
nodes. What improved is the ratio of readable to opaque, at a cost of 73% more diagram.

## Finding 4 — delombok works, and buys less than Finding 2 predicted

Finding 2 says the Lombok accessor is invisible. There are three ways to make it visible, and only
one of them is cheap.

**Run Lombok in-process.** Drop `-proc:none`, put `lombok.jar` on the processor path, and the
generated methods appear in the attributed tree with real bodies. Rejected, for four separate
reasons and any one would do. It gives codeflow a jar dependency it must locate at run time, when
the whole input contract is *a directory and nothing else*. Lombok works by reaching into javac's
internals, so it needs `--add-opens` on the analysing JVM — ten of them on JDK 25, which is exactly
the flag set the installed CLI's start script cannot be given from the build. It pins codeflow to
whichever Lombok versions have caught up with whichever javac it is running on, on top of the 21/25
duality already in play. And `-proc:none` is doing a second job nobody wrote down: turning
annotation processing on generally means any processor discoverable from the analysed corpus gets to
**execute** during what the user thinks is a read.

**Delombok as a preprocessing step.** Lombok's own `delombok` rewrites annotated source into plain
Java source with the generated members written out — which is a *directory of Java files*, codeflow's
input contract exactly. Nothing changes in the tool. This was measured.

```
java --add-opens jdk.compiler/com.sun.tools.javac.{code,comp,file,main,model,parser,
                 processing,tree,util,jvm}=ALL-UNNAMED \
     -jar lombok-1.18.46.jar delombok <module>/src/main/java -d <out>
./gradlew run --args="<out> --from ProgressiveEMICalculator#calculateRateFactorPerPeriodForInterest"
```

Delombok degrades the same way codeflow does — it printed the same unresolved-symbol errors for the
sibling modules and wrote all 81 files anyway.

|                                    | as written | delomboked |
|---|---|---|
| unresolved references              | 23311 / 40457 (57.6%) | 21454 / 48091 (44.6%) |
| nodes                              | 883 | 982 |
| edges                              | 1159 | 1201 |
| method blocks                      | 33 | **111** |
| `EXTERNAL`                         | **365** | **308** |
| `OBJ_VARIABLE`                     | 79 | 157 |
| `RETURN`                           | 33 | 111 |
| `BIN_OP` / `FUNC_PARAM` / `VARIABLE` / `LITERAL` | 164 / 124 / 57 / 57 | unchanged |

The `EXTERNAL` drop is exactly four labels going to zero, and nothing else moved:

```
  getFromDate                 31 -> 0        getFromDate               0 -> 39 blocks
  getDueDate                  23 -> 0        getDueDate                0 -> 23 blocks
  mc                           2 -> 0        loanProductRelatedDetail  0 -> 14 blocks
  loanProductRelatedDetail     1 -> 0        mc                        0 ->  2 blocks
                              -------                                    ----------
                                 57                                          78
```

Every label that Finding 1 and Finding 3 blamed on `BigDecimal`, the JDK and corpus scoping is
byte-for-byte unchanged, which is what says delombok addresses cause 2 and only cause 2. The
non-accessor block list is identical between the two runs, so the graph is the same graph.

Two things the numbers say that the proposal did not expect.

**The cost is boxes, not nodes.** 57 opaque nodes became 78 method blocks, each containing a field
read and a `RETURN`: three pieces of diagram where there was one. Node count rose 11% and *block*
count rose 236%. On the Mermaid document that is 78 more subgraphs to read past; in the viewer it is
cheaper, since a box with nothing revealed inside it draws as nothing. `getFromDate` appearing 39
times where the opaque form appeared 31 is per-call-site inlining doing what it does — I did not
chase the exact eight, and the `1 -> 14` on `loanProductRelatedDetail` is unexplained.

**The identity win did not happen.** Finding 2 predicted that a modelled accessor would key into
`MemPos` and collapse two reads of one field into one box. `OBJ_VARIABLE` rose by 78 — *exactly* one
per inlined accessor, so no two of them merged. The reason is upstream: the receivers are themselves
opaque values from external calls and stream operations, so they carry no `MemPos` for the field
node to be filed under. Making the accessor transparent does not help when the *object* is still
opaque. That is worth stating plainly because it is the half of Finding 2 that measurement removed.

**The real cost is positions.** The same unmodelled construct is reported at
`ProgressiveEMICalculator.java:1428:42` as written and `:1075:42` delomboked. Every position codeflow
prints — every failure message, every node's provenance — now names a generated file the reader does
not have open. For a tool whose first question about any box is *which line of which file*, that is
the trade, and it is why delombok belongs in a recipe rather than in the tool.

### Generically, for any annotation

An annotation carries no meaning of its own — the meaning is in its processor — so there is no
generic reading of `@Anything`. There are only two generic handles: run the processor, or read what
the processor already wrote. Which one applies is decided by how the processor generates, and there
are exactly two ways.

**Via the `Filer` API**, the sanctioned one: the processor writes *new source files* and javac
compiles them in a later round. MapStruct, Dagger, AutoValue, Immutables, the JPA metamodel, Spring's
configuration processor — nearly everything. For these the generic answer is that **the build has
already done it**. The output is plain Java source in a known directory
(`build/generated/sources/annotationProcessor/java/main`, `target/generated-sources/annotations`),
which is codeflow's input contract with nothing to add. Fineract has 15 such files on disk, all
MapStruct `*Impl` classes, and they are exactly the code codeflow models well:

```java
public List<TaxGroupMappingsData> map(List<TaxGroupMappings> taxGroupMappings) {
    if ( taxGroupMappings == null ) { return null; }
    List<TaxGroupMappingsData> list = new ArrayList<TaxGroupMappingsData>( taxGroupMappings.size() );
    for ( TaxGroupMappings taxGroupMappings1 : taxGroupMappings ) { list.add( map( taxGroupMappings1 ) ); }
    return list;
}
```

**By mutating the AST** through javac's internals: the processor rewrites existing classes and writes
no files, so there is nothing on disk to point at. Lombok, and in practice little else. This is the
category that needs a per-library de-sugarer, and it is why `delombok` exists as a separate program.
Not generic, and cannot be — but it is one library.

So the generic half needs one thing from codeflow, and it is small: **accept more than one source
root.** `App` takes exactly one directory today (`directory == null -> directory = arg`), which is
also what forced the symlinked corpus in Finding 3. `codeflow <src> <generated-src> --from …` serves
both, and it is the whole of category 1.

One caveat, and it is the same wall Finding 4 hit. Generated code is normally reached through an
*interface* that a framework injects — `@Autowired TaxGroupMappingsMapper mapper; mapper.map(x)`. The
receiver's `MemPos` carries no constructed type, so `Frame.resolve` cannot settle dispatch and the
call takes `EXTERNAL` regardless of `TaxGroupMappingsMapperImpl` being right there in the corpus.
Having the source is necessary and not sufficient: the object still has to be traceable to a `new`.
What would actually unlock it is a dispatch fallback for *exactly one implementation in the corpus* —
which is a guess, of the kind `Frame.resolve` deliberately refuses, so it would need to be marked as
one rather than drawn as an ordinary inlining.

And the collapse in recommendation 2 is already generic in the direction that matters: it asks what a
body lowered to, never who wrote it, so Lombok, delombok, records and hand-written getters all get the
same treatment.

### In Fineract, concretely

The whole repository declares **four** annotation processors, and only two of them emit Java:

| Processor | Emits | For codeflow |
|---|---|---|
| `org.projectlombok:lombok` | nothing on disk — mutates the AST | needs `delombok` |
| `org.mapstruct:mapstruct-processor` | `*Impl.java`, 15 files on disk | already solved, needs multi-root |
| `spring-boot-autoconfigure-processor` | `META-INF/*.properties` | nothing to do |
| `spring-boot-configuration-processor` | `META-INF/*.json` | nothing to do |

In the module measured there is no MapStruct at all, so **Lombok is the entire annotation story
there** — and it is not one feature. Counting annotation sites in those 81 files:

```
  @Setter 39   @Getter 30   @RequiredArgsConstructor 22   @AllArgsConstructor 11
  @Data 8      @Slf4j 8     @ToString 4
```

Everything else in the module (`@Override` 129, `@NotNull`, `@Path`, `@Column`, `@Operation`,
`@Service`) is metadata that generates nothing.

That list is the argument against recognising Lombok inside codeflow. `@Getter` is 30 of ~122 sites.
`@RequiredArgsConstructor` and `@AllArgsConstructor` generate **constructors**, which matter more than
accessors do — `construct` is what gives a `MemPos` its type, and that is what dispatch-on-the-object
runs on. `@Slf4j` generates a *field*, so every `log.…` is otherwise a name javac cannot resolve.
`@Data` alone expands to six of the others. Hand-rolling the recognition means implementing all seven;
`delombok` implements them already, and correctly.

### The recipe

The whole of handling Lombok, with no change to codeflow, is `scripts/delombok.sh`:

```shell
scripts/delombok.sh <module>/src/main/java /tmp/delomboked
./gradlew run --args="/tmp/delomboked --from Class#method"
```

It finds the lombok jar under `~/.gradle` or `~/.m2` (`LOMBOK_JAR` overrides), runs `delombok` on
`JAVA_HOME`'s JDK, and fails if nothing was written. Three things in it are worth knowing about
before changing it:

- The **`--add-opens` list** is Lombok reaching into javac's internals. Required from JDK 16 on, and
  the same reason running Lombok *inside* codeflow would need those flags on the analysing JVM —
  where the installed CLI's start script cannot be given them.
- The jar is matched on **both** `-path '*projectlombok*'` and `-name 'lombok-[0-9]*.jar'`. Either
  alone is wrong: Kotlin ships a `lombok-compiler-plugin-for-ide` jar with no main class, and the
  sources and javadoc jars sit beside the real one.
- **Delombok exits 0 having written nothing** when the `--add-opens` set is wrong for the JDK, so the
  script counts the output files. Unresolved-symbol errors on stderr are *not* that case: delombok is
  pointed at one module of a multi-module build exactly as codeflow is, and writes every file it
  could read.

Two things make the output better, in this order. The trivial-accessor collapse in recommendation 2,
which turns the 78 accessor blocks back into 78 nodes — without it delombok trades one kind of noise
for another. And, if positions matter for the run, keeping the delomboked tree beside the original so
a reported `file:line` can be resolved by hand.

## What `EXTERNAL` conflates, and why it matters

One colour is currently carrying claims of very different value to a reader:

- *"This is `java.time`. Nobody is ever going to show you inside it."* — correct and final.
- *"This is `DateUtils`, in the repo, one directory over. Re-run with a wider corpus."* — actionable.
- *"This is a Lombok accessor for a field declared eight lines up."* — actively misleading.
- *"This is arithmetic. It is a `multiply`. I know what it does."* — information withheld for no
  reason.
- *"Two implementations were possible and I refuse to guess."* — the most interesting thing on the
  diagram, and the least visible.

That last one deserves emphasis. Dispatch ambiguity is a genuine finding about the program — the
receiver could be one of several classes and the value depends on which — and it renders identically
to `BigDecimal.valueOf`. The project's own rule is that a gap must never be drawn as a flow; the
matching rule, not yet applied, is that two different gaps should not be drawn as one gap.

## What to do about it

Ranked by measured weight, not by appeal.

### 1. A table of known external operators

Map the resolved `ExecutableElement`'s owner — never a string match on the receiver's text — for
`java.math.BigDecimal#multiply/add/subtract/divide`, `Money`, `MathUtil`, and render them as `BIN_OP`
with the operator label. Roughly 100 nodes in the measurement, and they are the hundred that matter,
since they are the computation itself.

Two constraints keep it inside the project's grain:

- Give it a distinct shade. "codeflow read this operator" and "codeflow was told what this operator
  means" are different claims and a reader should be able to tell them apart, the way `UNMODELLED` is
  dashed. Folding a table lookup into ordinary `BIN_OP` would be exactly the conflation this document
  is complaining about.
- Drop the trailing `MathContext`. Every arithmetic call in Fineract takes `mc`, and an operator's
  rounding policy is not an operand.

### 2. Model the Lombok accessor as the field read it is

~85 nodes, and unlike the arithmetic these are *wrong-feeling* rather than merely unhelpful: the
field is in the corpus and the diagram says it is not.

Finding 4 changes what this should be. Delombok already makes the accessor visible with no change to
codeflow at all, so the question is no longer *how does codeflow see through `@Getter`* — it is
**what should codeflow draw for a method whose whole body is `return this.field`**, whoever wrote it.
Today it draws a box, a field node and a `RETURN`, 78 times in one module.

So: collapse the trivial accessor. A callee whose lowered body is a single field read returned, with
no arguments and a receiver, is drawn as the field read in the *caller's* block — no block, no
`RETURN`, one node. That is a claim about the IR the callee lowered to, not about its name, so it
needs no name-matching, no Lombok-annotation check, and no gate the project would be right to
distrust. It works identically on delomboked source, on hand-written getters, and on records.

Combined with delombok that is the whole of cause 2 removed for one node apiece instead of three.
Without delombok it does nothing for Lombok, which is the point: the annotation is somebody else's
problem and there is a tool for it.

The name-matching version — resolve `get<Field>` against a field the receiver's class declares, when
the call fails to resolve — is what this section used to propose. It should stay unbuilt while
delombok is available. It guesses, and a diagram that quietly guesses an accessor is worse than one
that admits it stopped.

### 3. Split the type, or at least the reporting

Even without modelling anything new, the five paths are distinguishable at the point of construction
and are currently thrown away. Two cheap options:

- Report them. `codeflow: N of M references unresolved` is one number; a breakdown by cause would
  have made all three findings in this document visible in the first run instead of the fourth.
- Give dispatch-ambiguity its own node type. It is the one `EXTERNAL` that is a claim about the
  *program* rather than about codeflow's inputs, and it is the one a reader most needs to see.

### What is not worth doing

**Chasing `EXTERNAL` to zero by widening the corpus.** Finding 3 measured the shape of that: the
frontier moves one layer deeper, the graph grows faster than the opacity shrinks, and it terminates
at the JDK boundary no matter what. Pointing codeflow at the module *plus* its direct dependencies is
worth it; pointing it at the whole repository is not.

## Provenance

Everything measured here comes from two runs of the committed tool, no local modifications:

```
./gradlew run --args="<corpus> --from ProgressiveEMICalculator#calculateRateFactorPerPeriodForInterest"
```

against `fineract-progressive-loan/src/main/java`, against a symlinked
`fineract-progressive-loan` + `fineract-core` corpus, and (Finding 4) against a delomboked copy of the
first. All three exit 1 on the same single unmodelled `TYPE_CAST`, and all three wrote a complete
document. The delomboked run reports it at `:1075:42` where the other two report `:1428:42` — same
construct, and that difference is the finding.

Node counts are from declaration lines only (`nN[label]:::TYPE`); counting every `:::TYPE` match
inflates them roughly 3.6×, because each edge line names two nodes with their types.
