package codeflow.ir

import codeflow.TestFixtures
import codeflow.graph.Method
import codeflow.java.AstReader
import java.nio.file.Files
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.type.TypeKind
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

    /** Methods compared and builder pairs seen, so a sweep that compared nothing cannot pass. */
    private var compared = 0
    private var pairsDrawn = 0

    @Test
    fun everySummaryHasTheBuildersParameterToResultPairs() {
        val found = TestFixtures.fixtures().flatMap { differences(it) }
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

    /** One fixture analysed once, and every method in it with a result compared. */
    private fun differences(fixture: String): List<String> {
        val paths = Files.walk(TestFixtures.resources.resolve(fixture))
            .filter { it.toString().endsWith(".java") }.toList()
        return AstReader(TestFixtures.resources).analyse(paths).use { analysis ->
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
                val at = "$fixture/${keys.getValue(method)}"
                compared++
                pairsDrawn += drawn.size
                (drawn - summarised).map { "$at/$it/missing" } + (summarised - drawn).map { "$at/$it/extra" }
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

        /**
         * The builder's answer is wrong here. A root runs on no object (`build` opens it with an empty
         * owner), so a callee's write to `this` is filed on nothing and the root's own read of the
         * field below never finds it - the gated join that would carry the flag to the result is never
         * drawn. The summary has `this` as a root object, keeps the callee's write, and joins it.
         */
        const val ROOT_THIS = "builder: a root runs on no object, so a callee's write to this never reaches the root's read"
    }
}
