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
 * Each source is one file of small classes in the default package, so a field is spelled `A.x`, and
 * a call site is spelled `@_`, since its position names a temporary directory.
 *
 * Every expectation is what the builder draws for the same method as a root, where the two
 * interpreters overlap - the summarizer is a second reader of one IR, and drifting from the first
 * is the failure it must not have. One place they part on purpose: a caller's field written on
 * only some paths keeps its entry value here (see [aFieldWrittenOnSomePathsKeepsItsEntryValue]),
 * where the builder drawing the method as a root has no box for it.
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
        summarise(source, method).pairs().map { "${it.first}>${it.second}".replace(SITE, "@_") }

    private val SITE = Regex("@[^>]*")

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

    /**
     * The arm that did not write arrives with what the caller's field held, so the old value is
     * still an output of its own. Here the summary parts from the builder drawing `f` as a root,
     * which has no box for a field nothing wrote and lets the one write stand alone.
     */
    @Test
    fun aFieldWrittenOnSomePathsKeepsItsEntryValue() =
        assertEquals(
            listOf("P0>this.A.x", "P1>this.A.x", "this.A.x>this.A.x"),
            pairs("class A { int x; void f(boolean c, int a) { if (c) x = a; } }", "A#f")
        )

    /**
     * A fresh object's field starts empty: there is no caller's value for the other arm to keep, so
     * one box arrives and, as in the builder, the join takes no gate.
     */
    @Test
    fun aFieldOfAnObjectAllocatedHereHasNoEntryValue() =
        assertEquals(
            listOf("P1>RET"),
            pairs("class B { int v; } class A { int f(boolean c, int a) { B b = new B(); if (c) b.v = a; return b.v; } }", "A#f")
        )

    /** A receiver from an opaque call is no object this can name, and the summary says so. */
    @Test
    fun aFieldThroughAnUntrackedReceiverIsAGap() {
        val src = "class B { int v; } class A { " +
            "int read(java.util.List<B> l) { return l.get(0).v; } " +
            "void write(java.util.List<B> l, int a) { l.get(0).v = a; } }"
        for (method in listOf("A#read", "A#write")) {
            val gaps = summarise(src, method).gaps
            assertEquals(listOf("unmodelled"), gaps.map { it.kind }, method)
            assertTrue(gaps.single().detail.contains("v"), "$gaps")
        }
    }

    /** An object made here and stored in the caller's field is named through that field. */
    @Test
    fun aFieldOfAnObjectMadeHereIsSpelledThroughTheFieldThatHoldsIt() =
        assertEquals(
            listOf("P0>this.A.b.B.v", "ext:B@_>this.A.b"),
            pairs("class B { int v; } class A { B b; void f(int a) { B x = new B(); x.v = a; this.b = x; } }", "A#f")
        )

    /** Reached only past the cap, the object's field cannot be spelled, and that is said. */
    @Test
    fun aFieldOfAnObjectMadeHereBeyondTheCapIsAGap() {
        val s = summarise(
            "class C { int z; } class B { C c; } class A { B b; " +
                "void f(int a) { B x = new B(); C y = new C(); y.z = a; x.c = y; this.b = x; } }",
            "A#f"
        )
        assertEquals(listOf("depth"), s.gaps.map { it.kind })
        assertEquals("this.A.b.B.c.C.z", s.gaps.single().detail)
    }

    /**
     * A local from an opaque call is a stand-in, not an allocation: the write is to an object from
     * outside, and dropping it with the method's dead objects would lose `a` without a word.
     */
    @Test
    fun aWriteThroughALocalFromAnOpaqueCallIsAGap() {
        val s = summarise("class B { int v; } class A { void f(java.util.List<B> l, int a) { B b = l.get(0); b.v = a; } }", "A#f")
        assertEquals(listOf("unmodelled"), s.gaps.map { it.kind })
        assertTrue(s.gaps.single().detail.startsWith("B.v written"), "${s.gaps}")
    }

    @Test
    fun aWriteThroughALoopElementIsAGap() {
        val s = summarise("class B { int v; } class A { void f(java.util.List<B> bs, int a) { for (B b : bs) b.v = a; } }", "A#f")
        assertEquals(listOf("unmodelled"), s.gaps.map { it.kind })
        assertTrue(s.gaps.single().detail.startsWith("B.v written"), "${s.gaps}")
    }

    /**
     * The arm that did not write keeps what the stand-in's field held, which nobody can name - one
     * gap. The write itself is to an object from outside that the summary cannot hand back - another.
     */
    @Test
    fun aStandInWrittenOnSomePathsIsAGapAtTheJoin() {
        val s = summarise(
            "class B { int v; } class A { int f(java.util.List<B> l, boolean c, int a) { B b = l.get(0); if (c) b.v = a; return b.v; } }",
            "A#f"
        )
        assertEquals(listOf("unmodelled", "unmodelled"), s.gaps.map { it.kind })
        assertEquals(listOf("B.v read", "B.v written"), s.gaps.map { it.detail.substringBefore(" on") }.sorted())
        assertEquals(listOf("P1>RET", "P2>RET"), s.pairs().filter { it.second == "RET" }.map { "${it.first}>${it.second}" })
    }

    /** The builder draws this read as a box with no inputs, so nothing reaches RET - and a gap says why. */
    @Test
    fun aReadThroughALocalFromAnOpaqueCallIsAGap() {
        val s = summarise("class B { int v; } class A { int f(java.util.List<B> l) { B b = l.get(0); return b.v; } }", "A#f")
        assertEquals(listOf("unmodelled"), s.gaps.map { it.kind })
        assertTrue(s.gaps.single().detail.startsWith("B.v read"), "${s.gaps}")
        assertTrue(s.pairs().none { it.second == "RET" }, "${s.pairs()}")
    }

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
