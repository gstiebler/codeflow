package codeflow.ir

import codeflow.java.AstReader
import codeflow.java.MethodSpec
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Summaries, asserted on as the pairs they spell and the gaps they carry.
 *
 * Each source is one file of small classes in the default package, so a field is spelled `A.x`.
 * Every expectation is what the builder draws for the same method as a root, where the two
 * interpreters overlap - the summarizer is a second reader of one IR, and drifting from the first
 * is the failure it must not have.
 */
class SummarizerTest {

    @TempDir
    lateinit var dir: Path

    private fun summarise(source: String, method: String): Summary {
        val file = dir.resolve("A.java")
        Files.writeString(file, source)
        return AstReader(dir).analyse(listOf(file)).use { analysis ->
            val target = analysis.methods().first { MethodSpec.matches(it, method) }
            val builder = IrGraphBuilder(analysis.globalCtx)
            Summarizer(analysis.globalCtx, builder::bodyOf, builder::initializersOf).summary(target)
        }
    }

    private fun pairs(source: String, method: String) =
        summarise(source, method).pairs().map { "${it.first}>${it.second}" }

    @Test
    fun aParameterReachesTheReturnThroughArithmetic() =
        assertEquals(listOf("P0>RET"), pairs("class A { int f(int a, int b) { int c = a * 2; return c; } }", "A#f"))

    @Test
    fun aConstantReachesNothing() =
        assertEquals(emptyList(), pairs("class A { int f(int a) { return 3; } }", "A#f"))

    @Test
    fun aFieldWriteIsAnEffectAndAFieldReadIsAnInput() {
        assertEquals(listOf("P0>this.A.x"), pairs("class A { int x; void f(int a) { this.x = a; } }", "A#f"))
        assertEquals(listOf("this.A.x>RET"), pairs("class A { int x; int f() { return x; } }", "A#f"))
    }

    /** The bug the spike had: the else arm's write used to win, and `a` was a dead end. */
    @Test
    fun bothArmsOfAFieldWriteReachAReadBelowTheIf() =
        assertEquals(
            listOf("P0>RET", "P0>this.A.x", "P1>RET", "P1>this.A.x", "P2>RET", "P2>this.A.x"),
            pairs(
                "class A { int x; int f(int a, int b, boolean c) { if (c) this.x = a; else this.x = b; return this.x; } }",
                "A#f"
            )
        )

    /**
     * The arm with no write arrives with what the field held before the `if`, which only holds if
     * the arm that did write was undone before the other one ran.
     */
    @Test
    fun aFieldWrittenInOneArmKeepsItsOldValueOnTheOther() =
        assertEquals(
            listOf("P0>RET", "P0>this.A.x", "P1>RET", "P1>this.A.x", "P2>RET", "P2>this.A.x"),
            pairs(
                "class A { int x; int f(int a, int b, boolean c) { this.x = a; if (c) this.x = b; return this.x; } }",
                "A#f"
            )
        )

    /** Two constants and nothing else, so the only way `c` arrives is as the phi's gate. */
    @Test
    fun aGatedPhiCarriesItsCondition() =
        assertEquals(
            listOf("P1>RET"),
            pairs("class A { int f(int a, boolean c) { int r = 0; if (c) r = 1; return r; } }", "A#f")
        )

    @Test
    fun aWriteThroughAParameterIsAnEffectOnThatParameter() =
        assertEquals(listOf("P1>P0.B.v"), pairs("class B { int v; } class A { void f(B b, int a) { b.v = a; } }", "A#f"))

    @Test
    fun aStaticFieldIsKeyedByItsClass() =
        assertEquals(listOf("P0>static.A.s"), pairs("class A { static int s; void f(int a) { s = a; } }", "A#f"))

    @Test
    fun anOpaqueCallIsAnExtResultAndASink() {
        val p = pairs("class A { int f(String a) { return a.length(); } }", "A#f")
        assertTrue(p.any { it.startsWith("P0>ext-arg:length@") }, "$p")
        assertTrue(p.any { it.startsWith("ext:length@") && it.endsWith(">RET") }, "$p")
        assertTrue("P0>RET" in p, "$p")
    }

    @Test
    fun anUnmodelledConstructIsAGap() =
        assertEquals(
            setOf("unmodelled"),
            summarise("class A { int f(double d) { return (int) d; } }", "A#f").gaps.map { it.kind }.toSet()
        )

    @Test
    fun aFieldPathDeeperThanTheCapIsAGap() =
        assertTrue(
            summarise(
                "class C { int z; } class B { C c; } class D { B b; } class A { int f(D d) { return d.b.c.z; } }",
                "A#f"
            ).gaps.any { it.kind == "depth" }
        )
}
