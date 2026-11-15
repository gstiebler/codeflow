# Pipeline, entry points and exporters

Moved verbatim from CLAUDE.md, which now keeps only the rules. This is the reasoning behind them.

# Architecture

`AstReader.process` parses, attributes, then:

1. `AstProcessor` — records each method under the `ExecutableElement` it declares, and each class
   under its own. A method with no body is skipped: there is nothing to inline. Which implementation
   a call to an abstract or interface method reaches is decided at run time by the receiver's class,
   and that class is now *asked* rather than guessed — see "Dispatch is on the object" below. A
   receiver that names no object still takes the opaque `EXTERNAL` path.
2. `ir.Lowering` — javac trees to instructions, **once per method**. Names resolved, overloads
   selected, primitives decided; no `MemPos`, no ids, no edges.
3. `ir.IrGraphBuilder` — instructions to graph, **once per call site**, starting from the entry
   point.

The split between 2 and 3 is the one to preserve. What a name means is a question about the source
and has one answer per method; which box a value is drawn as is a question about a *call site*,
since a callee is inlined at every one. Answering both in one walk is what the tree walker did, and
it is why four satellite scanners existed to re-walk the same tree asking a different question —
and why one of them had to call back into the builder behind a memo, so that *asking* what object a
call returned did not *inline* the callee a second time. `Frame` in `IrGraphBuilder` is one
invocation: it holds the block, the object the method runs on, and the value each instruction
produced, which is both answers at once.

Which method is the root is a decision, not a detail: the diagram is whatever that one method
reaches, so on a corpus with several candidates the choice is the whole diagram.
`AstReader.selectEntry` makes it. `--from Class#method` names any method the sources declare —
without it the root is the first `main` by source path. `GlobalContext.sourceMethods` sorts by path
so the same input gives the same answer twice, and `AstReader` prints the one it took and the ones
it did not to stderr. Silence there reads as "this is the codebase" when it is one of four.

`main` is a default rather than a requirement because most Java has none: a service, a controller or
a library is entered from a caller outside the corpus, and refusing to start anywhere else excluded
most of the tool's subject matter.

`--from` may repeat. Each entry point is its own root, built as if it ran alone, and a static field
read that found no write in its own entry gets a dashed `shared` edge from every other entry's last
write of that field, so the document does not depend on `--from` order (`twoEntryPoints` is the
fixture). `docs/if-written-again.md` §7 wants every public method to be a root by default, which
waits on method summaries, since without them each root re-inlines everything it reaches at every
call site.

An exporter then renders the root `GraphBuilderBlock` and its `calledMethods` recursively. There are
four, chosen by flag, and all four walk the same tree — a construct is supported once `Lowering`
and `IrGraphBuilder` model it, not once an exporter mentions it:

| Flag | Exporter | For |
|---|---|---|
| *(none)* | `MermaidExporter` | the default document |
| `--graphml` | `GraphmlExporter` | desktop editors (yEd, Cytoscape Desktop) |
| `--json` | `JsonExporter` | the viewer's payload, and what the tests assert on |
| `--html` | `HtmlExporter` | one self-contained interactive page |

The chain that picks between them ends in Mermaid, so `Args` **refuses a flag it does not know**
rather than letting it fall through. An unrecognised `--…` used to produce a complete, plausible
document that is not the one asked for, with nothing anywhere saying so — a mistyped `--html`, or
`--html-cytoscape` after the second renderer was removed. `Args.FORMATS` is the list.

Every exporter writes to **stdout**, so nothing else may. `logback.xml` pins its appender to
`System.err` for exactly this reason: a `ConsoleAppender` with no `<target>` defaults to stdout, and
debug lines interleaved into the document make it unparseable with no hint as to why.

`GraphmlExporter` nests a `<graph>` inside each block's `<node>` but declares **every edge at the
root**, since GraphML requires an edge to sit in a graph enclosing both endpoints and only the root
always qualifies. Plain GraphML carries no coordinates, so yEd opens it as a pile at the origin
until you run Layout → Hierarchical — that is the format, not a bug.

**Mermaid needs the same rule, and for a reason that is easy to miss.** Which subgraph a node
belongs to is not decided by where it is declared: Mermaid works it out from every statement naming
the node, and the *deeper* claim wins. So an edge written inside a callee's subgraph pulls its target
in, and `MermaidExporter` used to write each edge in the block of its **source** — which for a
callee's RETURN feeding the caller's variable is the callee. `int b = classify(a, 10)` drew `b`
*inside the classify box*, and `funcCall`'s `y` inside `methodA`. Every arrow was correct and the
value sat in the wrong method, with nothing on the page to suggest it: a box here means a method, so
that is a diagram confidently and readably wrong about where a value lives.

`MermaidExporter.placeEdges` writes each edge in the innermost block enclosing **both** endpoints.
Naming a node from an *enclosing* block is harmless and is left alone — that is where an argument
edge is written, and the declaration below still wins — so only the edges pointing outwards move,
and only as far as they must. `assertNoNodeIsMentionedBelowItsOwnBlock` is the guard, swept over
every fixture.

Moving an edge moves its **link index**, which is the trap next door: `linkStyle` numbers links
globally across the whole flowchart in declaration order, so anything that changes where an edge is
written renumbers every edge after it. A stale index is silent — it paints a condition's grey onto
an unrelated arrow and leaves the real one black, and the document still renders.
`assertLinkStylesAddressTheEdgesTheyMean` derives the mapping from the finished document rather than
from `Links.count`, so it fails if the counter and the writing ever disagree.

