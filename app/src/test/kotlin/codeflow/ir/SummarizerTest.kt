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
 * is the failure it must not have. Two places they part on purpose: a caller's field written on
 * only some paths keeps its entry value here (see [aFieldWrittenOnSomePathsKeepsItsEntryValue]),
 * where the builder drawing the method as a root has no box for it; and a callee's write through
 * a receiver that is either of two objects is weak here, where the builder's is strong on both
 * (see [aCalleeEffectOnEitherOfTwoObjectsKeepsTheirOldValues]).
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

    // Calls, constructors and dispatch.

    @Test
    fun aCalleeSummaryIsInstantiatedPerCallSite() =
        assertEquals(listOf("P0>RET"), pairs("class A { int id(int v) { return v; } int f(int a, int b) { int x = id(a); int y = id(b); return x; } }", "A#f"))

    @Test
    fun anEffectThroughAParameterIsSeenByTheCaller() =
        assertEquals(listOf("P0>RET"), pairs("class B { int v; } class A { void set(B b, int a) { b.v = a; } int f(int a) { B b = new B(); set(b, a); return b.v; } }", "A#f"))

    @Test
    fun aFactoryObjectCarriesItsFieldsBack() =
        assertEquals(listOf("P0>RET"), pairs("class B { int v; B(int v) { this.v = v; } } class A { B make(int a) { return new B(a); } int f(int a) { return make(a).v; } }", "A#f"))

    @Test
    fun initializersRunBeforeTheConstructorBody() =
        assertEquals(listOf("P0>RET"), pairs("class B { int v = 1; int w; B(int a) { w = a + v; } } class A { int f(int a) { return new B(a).w; } }", "A#f"))

    /** An initializer writes onto the object being built, so the constructor reads what it wrote. */
    @Test
    fun anInitializersValueIsReadByTheConstructor() =
        assertEquals(
            listOf("P0>RET", "static.B.s>RET"),
            pairs("class B { static int s; int v = s; int w; B(int a) { w = a + v; } } class A { int f(int a) { return new B(a).w; } }", "A#f")
        )

    /** A constructor's summary carries what its class's initializers wrote, as the builder's `invoke` runs them. */
    @Test
    fun aConstructorSummaryShowsTheInitializersEffect() =
        assertEquals(
            listOf("P0>this.A.v", "static.A.s>this.A.w"),
            pairs("class A { static int s; int v; int w = s; A(int a) { v = a; } }", "A#<init>")
        )

    /**
     * A stand-in an initializer made exists before every fork in the constructor body, so a join
     * there finds an entry nobody can name - which is a gap, whatever the two lists' indices are.
     */
    @Test
    fun aStandInFromAnInitializerHasAnEntryAtABodyJoin() {
        val s = summarise(
            "class B { int v; } class A { java.util.List<B> l; B b = l.get(0 + 0 + 0 + 0 + 0 + 0); A(boolean c, int a) { if (c) b.v = a; } }",
            "A#<init>"
        )
        assertTrue(s.gaps.any { it.detail.startsWith("B.v read") }, "${s.gaps}")
    }

    @Test
    fun superConstructorWritesAreSeenFromTheSubclass() =
        assertEquals(listOf("P0>RET"), pairs("class Base { int v; Base(int v) { this.v = v; } } class Sub extends Base { Sub(int a) { super(a); } } class A { int f(int a) { return new Sub(a).v; } }", "A#f"))

    @Test
    fun dispatchOnThisIsSpecialisedByTheReceiverClass() {
        val src = "abstract class T { int run(int a) { return step(a); } abstract int step(int a); } " +
            "class K extends T { int step(int a) { return 0; } } class I extends T { int step(int a) { return a; } } " +
            "class A { int f(int a) { return new I().run(a); } int g(int a) { return new K().run(a); } }"
        assertEquals(listOf("P0>RET"), pairs(src, "A#f"))
        assertEquals(emptyList(), pairs(src, "A#g"))
    }

    /**
     * Summarised with no receiver class, the template method resolves `step` statically as the
     * builder does for an untyped receiver - and says the answer depends on the class.
     */
    @Test
    fun dispatchOnThisWithNoReceiverClassIsAGap() {
        val src = "abstract class T { int run(int a) { return step(a); } abstract int step(int a); } " +
            "class I extends T { int step(int a) { return a; } }"
        assertEquals(listOf("dispatch"), summarise(src, "T#run").gaps.map { it.kind })
    }

    @Test
    fun dispatchOnAParameterWithSeveralOverridesIsAGap() {
        val s = summarise("class S { int area(int a) { return a; } } class Q extends S { int area(int a) { return 0; } } class A { int f(S s, int a) { return s.area(a); } }", "A#f")
        assertEquals(listOf("dispatch"), s.gaps.map { it.kind })
        assertTrue("P1>RET" in s.pairs().map { "${it.first}>${it.second}" })
    }

    /** A receiver from an opaque call says nothing about its class either, so the call is the same gap. */
    @Test
    fun dispatchOnAnUntrackedReceiverWithSeveralOverridesIsAGap() {
        val s = summarise(
            "class B { int g(int a) { return a; } } class C extends B { int g(int a) { return 0; } } " +
                "class A { int f(java.util.List<B> l, int a) { B b = l.get(0); return b.g(a); } }",
            "A#f"
        )
        assertEquals(listOf("dispatch"), s.gaps.map { it.kind })
        assertTrue("P1>RET" in s.pairs().map { "${it.first}>${it.second}" }, "${s.pairs()}")
    }

    /** The same through a stand-in: the object a write gave the opaque result. */
    @Test
    fun dispatchOnAStandInReceiverWithSeveralOverridesIsAGap() {
        val s = summarise(
            "class B { int v; int g(int a) { return a; } } class C extends B { int g(int a) { return 0; } } " +
                "class A { B b; int f(java.util.List<B> l, int a) { b = l.get(0); return b.g(a); } }",
            "A#f"
        )
        assertEquals(listOf("dispatch"), s.gaps.map { it.kind })
        assertTrue("P1>RET" in s.pairs().map { "${it.first}>${it.second}" }, "${s.pairs()}")
    }

    @Test
    fun varargsCollapseLikeTheBuilder() =
        assertEquals(listOf("P0>RET", "P1>RET"), pairs("class A { int sum(int... v) { return v[0]; } int f(int a, int b) { return sum(a, b); } }", "A#f"))

    @Test
    fun aCalleeGapIsInheritedByTheCaller() =
        assertTrue(summarise("class A { int g(double d) { return (int) d; } int f(double d) { return g(d); } }", "A#f").gaps.any { it.kind == "unmodelled" })

    /**
     * The callee writes the field on one path only, so its effect carries the field's entry value;
     * instantiated, that is the caller's own earlier write, which must still reach the read below.
     */
    @Test
    fun aCalleeWritingAFieldOnSomePathsKeepsTheCallersValue() =
        assertEquals(
            listOf("P0>RET", "P0>this.A.x", "P1>RET", "P1>this.A.x", "P2>RET", "P2>this.A.x"),
            pairs(
                "class A { int x; void set(boolean c, int a) { if (c) x = a; } int f(int a, int b, boolean c) { x = b; set(c, a); return x; } }",
                "A#f"
            )
        )

    /**
     * Through a receiver that is either of two objects, the callee wrote one of them: the other
     * keeps its value. Here the summary parts from the builder, which inlines the write as a strong
     * update on each - the known gap on `docs/decisions/joins.md`.
     */
    @Test
    fun aCalleeEffectOnEitherOfTwoObjectsKeepsTheirOldValues() =
        assertEquals(
            listOf("P1>RET", "P2>RET"),
            pairs(
                "class B { int v; } class A { void set(B b, int a) { b.v = a; } " +
                    "int f(boolean c, int a, int k) { B x = new B(); B y = new B(); x.v = k; y.v = k; B p = c ? x : y; set(p, a); return x.v; } }",
                "A#f"
            )
        )

    /**
     * A stand-in the callee hands back is an object the caller sees: a field it set is read back,
     * kept when the caller hands it on, and a gap when it dies with the caller - never nothing.
     */
    @Test
    fun aStandInReturnedByTheCalleeKeepsItsEffects() {
        val get = "class B { int v; } class A { B get(java.util.List<B> l, int a) { B x = l.get(0); x.v = a; return x; } "
        val read = summarise(get + "int f(java.util.List<B> l, int a) { return get(l, a).v; } }", "A#f")
        assertTrue("P1>RET" in read.pairs().map { "${it.first}>${it.second}" }, "${read.pairs()}")
        assertEquals(listOf("B.v written"), read.gaps.map { it.detail.substringBefore(" on") })
        val dropped = summarise(get + "void f(java.util.List<B> l, int a) { get(l, a); } }", "A#f")
        assertEquals(listOf("B.v written"), dropped.gaps.map { it.detail.substringBefore(" on") })
        val handedOn = summarise(get + "B f(java.util.List<B> l, int a) { return get(l, a); } }", "A#f")
        assertEquals(emptyList(), handedOn.gaps.toList())
    }

    /** A callee whose body cannot be lowered is opaque to its caller, and the caller says why. */
    @Test
    fun aCalleeThatCannotBeLoweredIsABodyGap() {
        val s = summarise("class A { int g(int a) { int t; return t; } int f(int a) { return g(a); } }", "A#f")
        assertEquals(listOf("body"), s.gaps.map { it.kind })
        assertTrue("P0>RET" in pairs("class A { int g(int a) { int t; return t; } int f(int a) { return g(a); } }", "A#f"))
    }

    /** Until the fixpoint lands, a recursive call contributes nothing - and says so. */
    @Test
    fun aRecursiveCallIsAFixpointGap() =
        assertEquals(
            listOf("fixpoint"),
            summarise("class A { int f(int a) { return a + f(a - 1); } }", "A#f").gaps.map { it.kind }
        )
}
