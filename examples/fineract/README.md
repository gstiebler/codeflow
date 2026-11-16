# Fineract entry points

Graphs of a few Apache Fineract service methods, made for the **Condensed** view: each injected
collaborator (`loanAssembler`, `journalEntryPoster`, the repositories, …) is a boundary, so the
condensed page reads as "what this service asks of whom, in which order".

```shell
./gradlew installDist
examples/fineract/run.sh ~/src/fineract out/fineract                 # every *.args here
examples/fineract/run.sh ~/src/fineract out/fineract repayment.args  # one
```

Open `out/fineract/<name>.html`, switch to Condensed.

## Files

| File | What |
|---|---|
| `*.args` | one entry point each: `--from Class#method`, then `--boundary <type>` lines. Read by codeflow as `@file` (one or more arguments per line, `#` comments). Edit freely. |
| `seed-args.py` | writes a first `.args` for a root: its class's `private final` field types that are declared in the sources become boundaries; the others are listed as comments. |
| `stubs/` | signature-only copies of Spring Data's `JpaRepository` and `JpaSpecificationExecutor`. Fineract's repositories inherit `save`/`findById` from them; without the declarations javac cannot resolve those calls and they stay bare `EXTERNAL`. |
| `run.sh` | links every `fineract-*/src/main/java` (client, e2e, avro, doc, war left out) plus `stubs/` into one dir and runs each `.args` with `--html`. |

New root:

```shell
examples/fineract/seed-args.py out/fineract/src \
  out/fineract/src/fineract-provider/org/apache/fineract/.../SomeServiceImpl.java someMethod \
  org.springframework.data.jpa.repository.JpaRepository \
  org.springframework.data.jpa.repository.JpaSpecificationExecutor > examples/fineract/some.args
```

## Limits

- A call on an injected service is never read through: nobody `new`s the object, so codeflow cannot
  know which implementation runs. That is why it is a boundary. To see inside one, give it its own
  root (`repayment-domain.args` is the inside of `LoanAccountDomainService#makeRepayment`).
- No classpath: anything from a library other than the stubbed Spring Data calls is `EXTERNAL`.
- Lombok is not run. Fineract's `@RequiredArgsConstructor` services still work (the fields are
  written in source); run `scripts/delombok.sh` first if a root needs generated getters inlined.
- Every `JpaRepository#save` merges into one condensed group, whichever repository it was called on.
