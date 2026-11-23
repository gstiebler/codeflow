# High-Level Concepts in codeflow: Design Specification

Date: 2026-11-23

## Problem

Codeflow's graph atoms (`LITERAL`, `VARIABLE`, `OBJ_VARIABLE`, `BIN_OP`, `FUNC_PARAM`, `RETURN`, `EXTERNAL`, `UNMODELLED`, `BOUNDARIES`, `EVENT`) are expression-level. Even in the condensed viewer (`condensed.ts`), the reader jumps straight from a folded `method (N)` box to individual `+`, `==`, `0`, and temporary local nodes. For real enterprise Java (Spring/JAX-RS services, repositories, domain entities), three things are missing:

1. No abstraction layers between "folded method" and "individual operator".
2. Collapsed method boxes say only how many nodes they hide, not what they do.
3. Semantic roles already parsed by `javac` (HTTP routes, `@Scheduled`, `@Repository`, `@Entity`, `throw`, loggers) and single-implementation DI wiring are discarded or collapsed into opaque `EXTERNAL` boxes.

The constraint is codeflow's founding rule: **never draw a silently wrong graph**. High-level projections must be deterministic derivations from the IR and AST, never guesses; every assumption and every gap must remain visible.

---

## The Six Capabilities

### 1. Expression Contraction (Derived Edges & Decision Labels)

In the **Workflow** view (Level 2), pure expression chains between high-level **anchors** are contracted into single derived edges:

- **Anchors** (always kept): `EVENT`, `FUNC_PARAM`, `RETURN`, `THROW`, `BOUNDARIES`, non-noise `EXTERNAL` calls, `UNMODELLED` gaps (never silently hidden), **domain field** accesses (`fieldOwner` present), and **gated decisions** (`BIN_OP` nodes receiving a `CONDITION` edge).
- **Contracted glue**: `LITERAL`, non-decision `BIN_OP`, local `VARIABLE`/`OBJ_VARIABLE` (no `fieldOwner`), and `NOISE`-tagged calls (when noise hiding is on).
- **Soundness invariant**: An anchor `B` is reachable from anchor `A` in the contracted graph **if and only if** `B` is reachable from `A` in the underlying graph via non-anchor intermediates.
- **Decision captions**: Gated decision nodes (`if`, `switch`, `ternary`) are captioned with a concise condition snippet from `payload.lines` at their source line (e.g., `if: amount > balance`) rather than bare `if`.
- **Click-to-expand**: Each contracted edge carries the exact `contractedIds` it collapsed. Clicking a contracted edge reveals those intermediate expression nodes directly (or lets the reader switch to Level 3 with those nodes revealed).

### 2. Summary Cards on Collapsed Method Boxes

`JsonExporter` computes a call-site-exact `BlockSummary` over each `GraphBuilderBlock` and its inlined callees and attaches a compact `summary` object to every `METHOD` box in the payload:

- `reads`: qualified/short fields read (`Loan.rate`)
- `writes`: fields mutated (`Loan.balance`, `Loan.status`)
- `boundaries`: external/boundary calls (`DB: saveAll`, `publish`)
- `throws`: exception types thrown (`IllegalStateException`)
- `gaps`: count of analysis gaps inside the method
- `assumed`: true when the call site was entered via assumed single-implementation dispatch

In Architecture (Level 1) and Workflow (Level 2) views, folded methods render as **Summary Cards** showing their effect badges (`writes`, `calls`, `throws`, `gaps`, `assumed`). In Expressions (Level 3), the compact hover tooltip also displays the summary without changing existing caption invariants.

### 3. Semantic Roles Harvested from the AST

`codeflow.java.Semantics` inspects `javac`'s parsed annotations, supertypes, and `throw` statements deterministically:

- **`throw` as a first-class construct:**
  - `Insn.Throw(value, exceptionType, source)` lowered by `Lowering.visitThrow`.
  - Drawn by `IrGraphBuilder` as `NodeType.THROW` (`throw <SimpleName>`, `lead = "throw"`), styled in Mermaid (`classDef THROW fill:#D32F2F30,stroke:#C62828`, emitted only when present) and `theme.ts`.
  - Recorded in `Summarizer` as a sink `throw:<SimpleName>@<source>` so `--summaries` and `diverges.py` see escaping exceptions.
- **Entrypoint annotations** (used for `EVENT` captions and `--discover-roots`):
  - Spring MVC (`@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`, `@PatchMapping`, `@RequestMapping` combining class + method paths).
  - JAX-RS (`@GET`, `@POST`, `@PUT`, `@DELETE`, `@PATCH` + `@Path`).
  - Scheduling & messaging (`@Scheduled`, `@EventListener`, `@TransactionalEventListener`, `@KafkaListener`, `@JmsListener`, `@CommandType`).
- **Auto-classified boundaries (`--auto-boundaries` and always tagged as `role` metadata):**
  - `DB`: `@Repository`, `*Repository`, `JpaRepository`, `CrudRepository`, `JdbcTemplate`, `EntityManager`.
  - `HTTP`: `@FeignClient`, `RestTemplate`, `WebClient`, `HttpClient`.
  - `EVENT`: `ApplicationEventPublisher`, `KafkaTemplate`, `JmsTemplate`, `RabbitTemplate`.
  - `NOISE`: SLF4J/Log4j/JUL loggers (`Logger`, `Log`, `LoggerFactory`), `Objects.requireNonNull*`, `Preconditions.check*`, `StringBuilder`/`StringBuffer`, `PrintStream` (`println`/`print`).
- **Class roles (`classRole` metadata on `METHOD` blocks):** `CONTROLLER`, `SERVICE`, `REPOSITORY`, `ENTITY`.
- **Field provenance metadata on `GraphNode`:** `fieldOwner` (declaring class qualified name) and `fieldOp` (`read` | `write` | `join`), emitted on JSON field nodes so downstream projections never guess field ownership from labels.

### 4. Domain State / Entities as First-Class Nodes

In the **Architecture** view (Level 1), field accesses sharing a `fieldOwner` class are aggregated into a single **Entity State Card** (`entity:<qualifiedClassName>`), skipping read-only injected collaborator fields on `CONTROLLER`/`SERVICE`/`REPOSITORY` classes:

- Displays the entity's simple name plus its aggregated `writes:` and `reads:` fields.
- Methods/components that write a field of `Loan` get an edge `method -> entity:Loan`; methods that read a field of `Loan` get `entity:Loan -> method`.
- Clicking an Entity card drills into Level 2 (Workflow) focused on the nodes touching that entity.

### 5. Three Explicit Zoom Levels

The viewer and CLI expose three zoom levels (`--level architecture|workflow|expressions`, plus toolbar buttons `Architecture`, `Workflow`, `Expressions` and keyboard shortcuts `1`, `2`, `3`):

1. **Architecture (Level 1):** Entrypoints (`EVENT`) → Service/Component Summary Cards (with `assumed` indicator) → Domain Entity State Cards (`ENTITY`) & Typed Boundary Cards (`DB`, `HTTP`, `EVENT`) & `THROW`s & `UNMODELLED` gaps. Noise hidden by default (with a `Noise` toggle).
2. **Workflow (Level 2):** Contracted intra/inter-method workflow: anchors, readable decision nodes, domain field mutations, folded callee Summary Cards, typed boundaries in `ORDER`, `THROW`s, `RETURN`s, and click-to-expand derived edges.
3. **Expressions (Level 3):** The existing condensed progressive-reveal dataflow view (`condensed.ts`), unchanged in its opening and invariants (default when `--level` is omitted for full backwards compatibility).

`Mermaid` export works on whichever zoom level is currently on screen.

### 6. Single-Implementation DI Traversal (`--assume-single-impl`)

Enterprise Java wires services through interface-typed fields (`private final LoanService loanService;`) that are never `new`-ed in source.

- Enabled explicitly via `--assume-single-impl` (strict mode remains the default).
- In `Dispatch.resolve`: when a virtual call targets a non-boundary interface/abstract/overridable method and the receiver has no concrete constructed type (`types.isEmpty()`), `GlobalContext.singleImplementation(declared)` finds every concrete source class that is a subtype of the declaring type (using javac's `Types.isSubtype`, never string matching) and resolves its overriding method.
- **Only when exactly 1 concrete implementation exists** in the analysed sources does dispatch resolve to it—and it returns `Dispatch.Resolution(method, assumed = true)`. Zero or ≥ 2 implementations remain `EXTERNAL` / `dispatch` gap (never a guess among siblings).
- **Never silent:**
  - Reported on stderr (`codeflow: assumed single implementation ...`).
  - `GraphBuilderBlock.assumed = true` → `"assumed": true` in `JsonExporter` → dashed amber outline (`#e65100`) and `assumed` badge in the viewer and Mermaid export.
  - `Summarizer` records `Gap("assumed", at, "Interface#m -> Impl#m (single implementation)")`, so `diverges.py` and summary consumers always know the result rests on an assumption.

---

## CLI Surface

New flags (validated strictly by `Args`; unknown flags still fail):

- `--assume-single-impl`: traverse single-implementation interfaces/abstract methods, visibly marked as assumed.
- `--auto-boundaries`: automatically treat detected `DB`/`HTTP`/`EVENT` classes as boundaries and `NOISE` classes as pure boundaries.
- `--discover-roots`: select all annotated entrypoints (HTTP routes, `@Scheduled`, listeners, `@CommandType`) as roots when `--from` is omitted.
- `--level <architecture|workflow|expressions>`: set the initial zoom level for `--html` (default: `expressions`).

---

## Testing Strategy

- **Kotlin behaviour tests (`AppTest`, `LoweringTest`, `SummarizerTest`, `InvariantsTest`):**
  - `Throw` instruction lowering, `NodeType.THROW` drawing, Mermaid output, `Summarizer` `throw:` sinks, and `InvariantsTest` coverage.
  - AST semantic extraction: Spring/JAX-RS HTTP routes, `@Scheduled`, `@Repository`/`@Entity`/`@Service`, `DB`/`HTTP`/`EVENT`/`NOISE` role tags, `--auto-boundaries`, `--discover-roots`, and field `owner`/`fieldOp` metadata in `graph.json`.
  - `--assume-single-impl`: resolves when 1 implementation exists (marked `assumed = true` in graph, JSON, and `Gap("assumed")` in summaries); stays `EXTERNAL` when 2 implementations exist; stays boundary when `--boundary` is set.
  - `MethodSummary` attachment on `METHOD` nodes in `JsonExporter`.
- **JS unit tests & corpus sweeps (`app/src/test/js/unit/`):**
  - Exact reachability preservation invariant for Expression Contraction across all 60+ fixture payloads.
  - Architecture (Level 1) entity aggregation, summary card content, noise filtering, assumed styling, and Mermaid export across all 3 levels.
  - ELK routing sweep for Architecture and Workflow views.
- **Playwright browser tests (`app/src/test/js/browser/`):**
  - Switching between `Architecture`, `Workflow`, and `Expressions` via toolbar buttons and `--level` startup.
  - Clicking a contracted edge to expand its underlying expressions; toggling `Noise`; rendering Summary Cards, Entity Cards, `THROW` nodes, and dashed amber `assumed` cards.
