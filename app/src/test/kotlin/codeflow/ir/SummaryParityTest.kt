package codeflow.ir

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import codeflow.TestFixtures
import codeflow.graph.Method
import codeflow.java.AstReader
import java.nio.file.Files
import java.nio.file.Path
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.type.TypeKind
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Every fixture method's summary held to what the builder draws for the same method as a root.
 *
 * The summarizer is a second interpreter over one IR, and the builder is the first and the
 * authority: a pair the builder draws and a summary lacks is a flow gone silently missing, which is
 * the one thing a summary may not do. So each method with a result is built as an inlined root,
 * and `P<i> -> RET` is read off it by walking from the root block's parameter node to its return
 * node; the summary's `P<i>>RET` pairs must be the same set.
 *
 * Where the two disagree on purpose, [knownDifferences] says so, one entry per pair and direction,
 * each with its reason. The test fails on a difference the map does not name *and* on an entry
 * that no longer happens, so the map stays the record of where the two disagree rather than
 * growing into a list of excuses nobody rereads.
 *
 * [theCorpusInCodeflowParitySrcHasNoSilentlyMissingPair] runs the same comparison on a real corpus,
 * by hand: `CODEFLOW_PARITY_SRC=<java dir> ./gradlew test --tests '*SummaryParityTest*'`.
 */
class SummaryParityTest {

    /**
     * `"<fixture>/<Class#method>/<pair>/<missing|extra>"` to why. `missing` is a pair the builder
     * draws and the summary lacks; `extra` the other way round.
     */
    private val knownDifferences: Map<String, String> = mapOf(
        "fieldIf/App#viaCallee/P0>RET/extra" to ROOT_THIS,
        "fieldIf/App#calleeThatForks/P0>RET/extra" to ROOT_THIS,
        "fieldIf/App#calleeThatForks/P1>RET/extra" to ROOT_THIS,
    )

    /** Methods compared and pairs seen on each side, so a sweep that compared nothing cannot pass. */
    private var compared = 0
    private var boundedCompared = 0
    private var pairsDrawn = 0
    private var pairsSummarised = 0

    /** One pair in one direction, and the gap kinds of the method it was found on. */
    private data class Difference(val key: String, val missing: Boolean, val gaps: Set<String>)

    @Test
    fun everySummaryHasTheBuildersParameterToResultPairs() {
        val plain = TestFixtures.fixtures().flatMap {
            differences(TestFixtures.resources, TestFixtures.resources.resolve(it), it)
        }
        // And again with each boundary fixture's `--boundary`, so parity is checked with the order
        // chain drawn: the builder's walk must not cross its ORDER edges, and the summarizer ignores it.
        val bounded = BOUNDARIES.flatMap { (fixture, boundaries) ->
            val name = "$fixture[--boundary ${boundaries.joinToString(",")}]"
            differences(TestFixtures.resources, TestFixtures.resources.resolve(fixture), name, boundaries)
        }
        assertTrue(boundedCompared > 0, "the boundary sweep compared nothing")
        val found = (plain + bounded).map { it.key }
        val unexplained = found.filter { it !in knownDifferences }
        val stale = knownDifferences.keys.filter { it !in found }
        assertTrue(compared > 40 && pairsDrawn > 25, "the sweep compared almost nothing: $compared methods, $pairsDrawn pairs")
        if (unexplained.isNotEmpty() || stale.isNotEmpty()) {
            fail(
                "summaries and the builder disagree where nothing says why:\n" +
                        unexplained.joinToString("\n") { "  $it" } +
                        "\nknown differences that no longer happen:\n" +
                        stale.joinToString("\n") { "  $it" }
            )
        }
    }

    /**
     * The same comparison on a corpus nobody wrote for codeflow, run by hand and skipped otherwise:
     * a corpus is not in the repository, and the numbers are a measurement, not a golden.
     *
     * What it fails on is the one difference the fixtures' map could never excuse on code nobody has
     * read: a pair the builder draws and the summary lacks on a method with no gap to say so. Every
     * other difference is reported - recall, precision, and each kind with a few examples - because
     * an `extra` is an over-approximation, and a `missing` on a method with a gap is already named.
     *
     * The log is turned down to warnings while it runs: the builder logs every node at debug, about
     * 300 MB of stderr for one Fineract root, and Gradle holds a test's output in memory until the
     * daemon dies of it.
     */
    @Test
    fun theCorpusInCodeflowParitySrcHasNoSilentlyMissingPair() {
        val src = System.getenv("CODEFLOW_PARITY_SRC")
        assumeTrue(src != null, "CODEFLOW_PARITY_SRC names no corpus")
        val root = Path.of(src!!).toAbsolutePath().normalize()
        val log = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
        val level = log.level
        log.level = Level.WARN
        val found = try {
            differences(root, root, root.fileName.toString())
        } finally {
            log.level = level
        }
        assertTrue(compared > 0, "nothing in $root has a result to compare")
        val missing = found.count { it.missing }
        val extra = found.size - missing
        println("parity: $compared methods, $pairsDrawn pairs drawn, $pairsSummarised summarised")
        println("parity: recall ${percent(pairsDrawn - missing, pairsDrawn)}, precision ${percent(pairsSummarised - extra, pairsSummarised)}")
        found.groupBy { reason(it) }.toSortedMap().forEach { (reason, all) ->
            println("parity: ${all.size} $reason")
            all.take(5).forEach { println("parity:   ${it.key}") }
        }
        val silent = found.filter { it.missing && it.gaps.isEmpty() }
        assertTrue(silent.isEmpty(), "pairs the builder draws and no gap explains:\n" + silent.joinToString("\n") { "  ${it.key}" })
    }

    private fun reason(d: Difference): String =
        (if (d.missing) "missing" else "extra") +
                (if (d.gaps.isEmpty()) ", no gap" else ", gaps ${d.gaps.sorted().joinToString(",")}")

    private fun percent(part: Int, whole: Int) = if (whole == 0) "n/a" else "%.1f%%".format(100.0 * part / whole)

    /** The sources under [dir] analysed once, relative to [root], and every method with a result compared. */
    private fun differences(root: Path, dir: Path, name: String, boundaries: List<String> = emptyList()): List<Difference> {
        val paths = Files.walk(dir).filter { it.toString().endsWith(".java") }.toList()
        return AstReader(root).analyse(paths, boundaries).use { analysis ->
            val builder = IrGraphBuilder(analysis.globalCtx)
            val summarizer = Summarizer(analysis.globalCtx, builder::bodyOf, builder::initializersOf)
            val methods = analysis.methods().filter { returnsAValue(it) }
            val keys = keys(methods)
            methods.flatMap { method ->
                val summary = summarizer.summary(method)
                val drawn = drawnPairs(method, builder, summary) ?: return@flatMap emptyList()
                val summarised = summary.pairs()
                    .filter { it.second == "RET" && PARAM.matches(it.first) }
                    .map { "${it.first}>RET" }.toSet()
                val at = "$name/${keys.getValue(method)}"
                val gaps = summary.gaps.map { it.kind }.toSet()
                compared++
                if (boundaries.isNotEmpty()) boundedCompared++
                pairsDrawn += drawn.size
                pairsSummarised += summarised.size
                (drawn - summarised).map { Difference("$at/$it/missing", true, gaps) } +
                        (summarised - drawn).map { Difference("$at/$it/extra", false, gaps) }
            }
        }
    }

    /**
     * The builder's pairs, from the method built alone - `build(roots)` with one root, which forgets
     * statics first, so nothing an earlier method wrote reaches this one.
     *
     * Null when the build throws for a method whose summary has a `body` gap: lowering failed for
     * both, and the summary already says so. A build that throws with no such gap is a failure.
     */
    private fun drawnPairs(method: Method, builder: IrGraphBuilder, summary: Summary): Set<String>? {
        val block = try {
            builder.build(listOf(method)).single()
        } catch (e: Exception) {
            if (summary.gaps.any { it.kind == "body" }) return null
            throw AssertionError("${method.element.enclosingElement.simpleName}#${method.element.simpleName}: " +
                    "the build threw and the summary has no body gap", e)
        }
        return block.parameterNodes.indices
            .filter { TestFixtures.reaches(block.parameterNodes[it], block.returnNode) }
            .map { "P$it>RET" }.toSet()
    }

    private fun returnsAValue(method: Method): Boolean {
        val element = method.element as ExecutableElement
        return element.kind != ElementKind.CONSTRUCTOR && element.returnType.kind != TypeKind.VOID
    }

    /** `Class#method`, with the parameter types added only where a class overloads the name. */
    private fun keys(methods: List<Method>): Map<Method, String> {
        fun short(m: Method) = "${m.element.enclosingElement.simpleName}#${m.element.simpleName}"
        val counts = methods.groupingBy { short(it) }.eachCount()
        return methods.associateWith { m ->
            if (counts.getValue(short(m)) == 1) short(m)
            else short(m) + (m.element as ExecutableElement).parameters.joinToString(",", "(", ")") { it.asType().toString() }
        }
    }

    private companion object {
        val PARAM = Regex("P\\d+")

        /** The boundary fixtures and the `--boundary` each is drawn with, as `InvariantsTest` draws them. */
        val BOUNDARIES = mapOf(
            "boundary" to listOf("Boundary"), "boundaryOrder" to listOf("Gateway"), "boundaryRoots" to listOf("Store")
        )

        /**
         * The builder's answer is wrong here. A root runs on no object (`build` opens it with an empty
         * owner), so a callee's write to `this` is filed on nothing and the root's own read of the
         * field below never finds it - the gated join that would carry the flag to the result is never
         * drawn. The summary has `this` as a root object, keeps the callee's write, and joins it.
         */
        const val ROOT_THIS = "builder: a root runs on no object, so a callee's write to this never reaches the root's read"
    }
}
