package codeflow

import codeflow.graph.GraphBuilderBlock
import codeflow.graph.GraphNode
import codeflow.graph.NodeType
import codeflow.ir.BinOp
import codeflow.ir.Bind
import codeflow.ir.Call
import codeflow.ir.Const
import codeflow.ir.Delegate
import codeflow.ir.Insn
import codeflow.ir.IrGraphBuilder
import codeflow.ir.New
import codeflow.ir.Opaque
import codeflow.ir.Param
import codeflow.ir.Phi
import codeflow.ir.ReadField
import codeflow.ir.Return
import codeflow.ir.Select
import codeflow.ir.ThisRef
import codeflow.ir.UnOp
import codeflow.ir.Unmodelled
import codeflow.ir.WriteField
import codeflow.ir.WriteLocal
import codeflow.java.AstReader
import codeflow.java.Analysis
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The properties that hold on every fixture, asserted with no expected output to compare against.
 *
 * These are `docs/if-written-again.md` §9's three, and the reason they are wanted is that the suite's
 * other two kinds of assertion can both pass on a graph that is wrong. A golden certifies
 * *unchanged*: `ternary/truth.md` was once written from a buggy run and passed happily while
 * encoding a graph with a branch missing. An `edgeLabels` assertion says two labels are adjacent and
 * nothing about where the value came from: `anInheritedMethodIsInlinedHoweverItIsReached` asserted
 * `offset -> +` while `super.shift` read a *fresh* `offset` with nothing flowing into it. And the
 * prescribed structural diff of a moved golden could not see the `x = x + 1` fix at all, since the
 * cyclic graph and the correct one carry the same label-stripped edges.
 *
 * Nothing here can be regenerated into agreement with a bug, and nothing here needs a fixture author
 * to have anticipated the failure - which is what makes them the assertions worth having on a corpus
 * nobody has read.
 *
 * The graph is compared against **the IR it was drawn from**, joined on the source position both
 * sides carry - `Insn.source` and `GraphNode.source`, `path:line:column`. One property of that key
 * shapes every check below: [codeflow.java.processors.ProcessorContext.location] uses the start
 * offset only, so in `a + b + c` both `+` instructions have one position between them, which is why
 * `getPosId` exists separately. So these ask whether a box *exists* for an instruction, never how
 * many boxes sit at a position - with the one exception of a literal, which is the only instruction
 * whose mapping to boxes is exactly one, and where counting is therefore the point.
 */
class InvariantsTest {

    private val testResourcesPath = System.getenv("CODEFLOW_CORPUS")?.let { Path.of(it) }
        ?: Path.of(System.getProperty("user.dir")).resolve("src").resolve("test").resolve("resources")

    /**
     * One fixture drawn, with the instruction lists it was drawn from still in hand.
     *
     * Built through [IrGraphBuilder] directly rather than through [AstReader.process], for the one
     * property the whole comparison rests on: `bodyOf` is memoised, so asking the builder afterwards
     * for a drawn block's method hands back *the same* [codeflow.ir.MethodBody] the drawing read.
     * The two sides therefore carry identical positions by construction. Re-lowering to get them
     * would be asserting that two lowerings agree, which is not the question.
     */
    private class Drawn(val fixture: String, val root: GraphBuilderBlock, val builder: IrGraphBuilder) {

        val blocks: List<GraphBuilderBlock> = flatten(root)

        val nodes: List<GraphNode> = blocks.flatMap { it.graph.getNodes() }

        /**
         * The serial of every node something points at.
         *
         * Built by inverting the whole graph because a node only holds the edges that *leave* it,
         * and because an edge crosses blocks: an argument reaches a parameter in the callee's block
         * and a return value comes back out of it.
         */
        val arrivedAt: Set<Int> = nodes
            .flatMap { node -> node.edgesIterator().asSequence().map { it.target.serial }.toList() }
            .toSet()

        /** The instructions of one block, which is one invocation of one method. */
        fun body(block: GraphBuilderBlock): List<Insn> = builder.bodyOf(block.method).instructions

        /** Every block drawn for a method, which is one per call site that reached it. */
        fun blocksOf(block: GraphBuilderBlock): List<GraphBuilderBlock> =
            blocks.filter { it.method.element == block.method.element }

        fun nodesAt(block: GraphBuilderBlock, source: String, label: String): List<GraphNode> =
            block.graph.getNodes().filter { it.source == source && it.label == label }

        private companion object {
            fun flatten(block: GraphBuilderBlock): List<GraphBuilderBlock> =
                listOf(block) + block.calledMethods.flatMap { flatten(it) }
        }
    }

    /**
     * What the box an instruction draws is captioned with, or null when it draws none of its own.
     *
     * A `when` over the sealed [Insn] rather than a lookup, so that a new instruction cannot be added
     * without someone deciding which of these it is - the same reason `Frame.draw` is one. The four
     * that draw nothing each have their own reason and none of them is "not covered yet":
     *
     * - [ReadField] through one holder *is* the node the write left behind, which is what keeps a
     *   value traceable from where it was set to where it is read.
     * - [Return] adds an edge to the block's existing RETURN node; there is one of those per method
     *   however many `return` statements it has.
     * - [ThisRef] is memoised per frame, since `this` is one object however many times the method
     *   names it, so every mention after the first draws nothing.
     * - [Delegate] to a constructor outside the analysed sources has no body to inline and, being a
     *   statement, no value for anything to read.
     */
    private fun drawnLabel(insn: Insn): String? = when (insn) {
        is Const -> insn.text
        is Param -> insn.name
        is WriteLocal -> insn.name
        is WriteField -> insn.name
        is Phi -> insn.name
        is BinOp -> insn.label
        is UnOp -> insn.label
        is Select -> insn.label
        is Bind -> insn.name
        is Call -> insn.name
        is New -> insn.typeName
        is Opaque -> insn.label
        is Unmodelled -> insn.kind
        is ReadField, is Return, is ThisRef, is Delegate -> null
    }

    /**
     * Every literal written in the source is drawn once per drawing of the method holding it, and
     * every literal drawn reaches something.
     *
     * §9's first bullet, and the *duplicated* half of its third. A literal is the one instruction
     * with a one-to-one mapping onto boxes - a gated phi draws two, a write through two holders draws
     * one on each, an inlined call draws none - so it is the one where an exact count is available,
     * and the count is what a body is drawn once per block makes exact.
     *
     * Both halves have a bug behind them. Too many is the `this(...)` guard's: a constructor
     * delegating to another must not run the field initializers the other has already run, and
     * removing that guard turns one `5` into three with nothing else complaining. Reaching nothing is
     * `x = x + 1`'s: the assignment target was created before the right-hand side was evaluated, so
     * the `x` in the expression found the node about to be written, and the literal behind the old
     * value was left pointing at a box nothing read.
     *
     * The initializers of a class are counted only for the second half. How many times they ran is
     * [codeflow.ir.Frame]'s own bookkeeping - a class with no constructor runs them in the caller's
     * block on every `new`, so two `new Plain()` in one method legitimately draw the same initializer
     * twice into one block - and a check that had to know the number would be a copy of the code that
     * decides it.
     */
    @Test
    fun everyLiteralIsDrawnOncePerDrawingAndReachesSomething() {
        var checked = 0
        fixtures().forEach { fixture ->
            val drawn = drawn(fixture)
            drawn.blocks.forEach { block ->
                drawn.body(block).filterIsInstance<Const>().forEach { const ->
                    val literals = drawn.nodes.filter {
                        it.getType() == NodeType.LITERAL && it.source == const.source && it.label == const.text
                    }
                    checked++
                    assertEquals(
                        drawn.blocksOf(block).size, literals.size,
                        "$fixture: the literal ${const.text} at ${const.source} is drawn ${literals.size} " +
                                "times for ${drawn.blocksOf(block).size} drawing(s) of ${block.getMethodName()}"
                    )
                }
            }
            drawn.nodes.filter { it.getType() == NodeType.LITERAL }.forEach { literal ->
                assertTrue(
                    literal.edgesIterator().hasNext(),
                    "$fixture: the literal ${literal.label} at ${literal.source} reaches nothing"
                )
            }
        }
        assertTrue(checked > 100, "the sweep found almost no literals to check: $checked")
    }

    /**
     * A box drawn for an instruction that consumes a value shows that value arriving.
     *
     * §9's second bullet, restated for the model it now has. "Every read of a local has an incoming
     * edge from a definition" is enforced upstream and totally: a use resolves to its defining
     * instruction while the tree is still in hand, so a local with nothing reaching it fails in
     * `Lowering` at the line that reads it, and a read is not a box at all. What is left to assert
     * here is the property that failure was protecting - that nothing is drawn as a value arriving
     * from nowhere - and the IR is what says which boxes those are: an instruction with inputs.
     *
     * This is the shape a behaviour test could not see. `super.shift` reading a *fresh* `offset` drew
     * a box with nothing flowing into it and an edge onwards to the operator, so an assertion on
     * adjacent labels held while the value it named never arrived.
     *
     * [ReadField] is the exemption, and it is by design rather than for convenience: a read through
     * one holder produces no box of its own, and the receiver is deliberately not drawn as flowing
     * into the field - `counter.value` is the box `value` was written into, not a new one taking
     * `counter`.
     */
    @Test
    fun everyBoxThatConsumesAValueShowsItArriving() {
        var checked = 0
        fixtures().forEach { fixture ->
            val drawn = drawn(fixture)
            drawn.blocks.forEach { block ->
                drawn.body(block).forEach { insn ->
                    if (insn is ReadField || insn.inputs.isEmpty()) return@forEach
                    val label = drawnLabel(insn) ?: return@forEach
                    drawn.nodesAt(block, insn.source, label).forEach { node ->
                        checked++
                        assertTrue(
                            node.serial in drawn.arrivedAt,
                            "$fixture ${block.getMethodName()}: '${insn.render()}' at ${insn.source} " +
                                    "is drawn as ${node.label} with nothing arriving at it"
                        )
                    }
                }
            }
        }
        assertTrue(checked > 100, "the sweep found almost no boxes to check: $checked")
    }

    /**
     * Every instruction draws something, and nothing is drawn that no instruction accounts for.
     *
     * §9's third bullet. Not as a count: the lowering runs once per *method* and the drawing runs
     * once per *call site*, so a body reached from three places is three times its instructions in
     * boxes, and within one block the ratio is not one either. What makes it checkable is that both
     * sides carry a position, so the claim becomes a correspondence.
     *
     * The first half is the one that catches a construct going missing, and the case it is written
     * for is the most instructive failure in `docs/plans/codemap-port-findings.md`: `switch` used as
     * a statement had no visitor, so its selector was read, given a box, and left with no edge out of
     * it - the value deciding the whole branch drawn as unused, nothing declared, nothing failed, and
     * every test green. An instruction that draws nothing at all is that shape's other half, and this
     * is what notices it. A call is allowed to draw a *block* instead of a box, which is what
     * inlining is; [GraphBuilderBlock.openedAt] is what makes it possible to ask.
     *
     * The second half is a tripwire rather than a bug's memorial. Every box is built from an
     * instruction's own position today, so what it guards is that staying true - the way it would
     * stop being true is a box built from a tree the caller happens to hold, which is how a node once
     * got a real line number naming the wrong file. [IrGraphBuilder.drawnInstructions] is the corpus
     * it checks against, and it holds what this run *read* rather than what the sources contain.
     */
    @Test
    fun everyInstructionDrawsSomethingAndEveryBoxComesFromAnInstruction() {
        var checked = 0
        fixtures().forEach { fixture ->
            val drawn = drawn(fixture)
            drawn.blocks.forEach { block ->
                drawn.body(block).forEach { insn ->
                    val label = drawnLabel(insn) ?: return@forEach
                    checked++
                    assertTrue(
                        drawn.nodesAt(block, insn.source, label).isNotEmpty() ||
                                block.calledMethods.any { it.openedAt == insn.source },
                        "$fixture ${block.getMethodName()}: '${insn.render()}' at ${insn.source} drew nothing"
                    )
                }
            }
            val positions = drawn.builder.drawnInstructions().mapTo(HashSet()) { it.source }
            drawn.blocks.forEach { block ->
                block.graph.getNodes().forEach { node ->
                    // The block's own RETURN node stands for the method rather than for any one
                    // `return`, of which there may be several or none, so it sits at the declaration.
                    if (node === block.returnNode) return@forEach
                    assertTrue(
                        node.source in positions,
                        "$fixture ${block.getMethodName()}: ${node.label} is drawn at ${node.source}, " +
                                "which no instruction this run read occupies"
                    )
                }
            }
        }
        assertTrue(checked > 100, "the sweep found almost no instructions to check: $checked")
    }

    /**
     * Every fixture directory, which is more than the goldens cover: `codemap` and `ls` have no test
     * naming them at all.
     *
     * One exclusion, and it is a gap in the *program* rather than in codeflow: `unwrittenLocal` reads
     * a local nothing has written, which javac rejects and attribution accepts, and refusing to draw
     * it is what the fixture is for. Its own assertions are
     * [codeflow.ir.LoweringTest.aLocalWithNoReachingDefinitionFailsWhereItIsRead] and
     * [codeflow.ir.IrGraphBuilderTest.aReadWithNoReachingDefinitionStillFails].
     */
    private fun fixtures(): List<String> = Files.list(testResourcesPath)
        .filter { Files.isDirectory(it) && Files.walk(it).anyMatch { f -> f.toString().endsWith(".java") } }
        .map { it.fileName.toString() }
        .filter { it != "unwrittenLocal" }
        .sorted()
        .toList()

    /**
     * Analysed and drawn once per fixture, however many of these tests ask for it.
     *
     * Three sweeps of sixty-eight directories is three hundred javac runs otherwise, and none of
     * them would be answering a different question: what is asserted differs, what is analysed does
     * not.
     */
    private fun drawn(fixture: String): Drawn = cache.getOrPut(fixture) {
        val paths = Files.walk(testResourcesPath.resolve(fixture))
            .filter { it.toString().endsWith(".java") }.toList()
        val analysis = AstReader(testResourcesPath).analyse(paths)
        val builder = IrGraphBuilder(analysis.globalCtx)
        Drawn(fixture, builder.build(entry(analysis, fixture)), builder)
    }

    /**
     * The root, chosen the way `AstReader.selectEntry` chooses it: the first `main` by source path.
     *
     * `noMain` is the fixture that exists because most Java has none, so it names one the way the
     * command line does.
     */
    private fun entry(analysis: Analysis, fixture: String) =
        System.getenv("CODEFLOW_ENTRY")?.let { analysis.method(it) }
            ?: ENTRY_POINTS[fixture]?.let { analysis.method(it) }
            ?: analysis.globalCtx.mainMethods().first()

    private companion object {
        val ENTRY_POINTS = mapOf("noMain" to "Report#total")

        val cache = HashMap<String, Drawn>()
    }
}
