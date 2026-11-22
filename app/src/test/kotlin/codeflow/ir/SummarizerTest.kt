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

    private fun summarise(source: String, method: String, budget: Int = Summarizer.DEFAULT_BUDGET): Summary =
        summariseInOrder(source, listOf(method), budget).getValue(method)

    /** Each of [methods] asked for in turn of one summarizer, which keeps what the earlier ones solved. */
    private fun summariseInOrder(source: String, methods: List<String>, budget: Int = Summarizer.DEFAULT_BUDGET): Map<String, Summary> {
        val file = dir.resolve("A.java")
        Files.writeString(file, source)
        return AstReader(dir).analyse(listOf(file)).use { analysis ->
            val builder = IrGraphBuilder(analysis.globalCtx)
            val summarizer = Summarizer(analysis.globalCtx, builder::bodyOf, builder::initializersOf, budget)
            methods.associateWith { method -> summarizer.summary(analysis.methods().first { MethodSpec.matches(it, method) }) }
        }
    }

    /**
     * The gaps but `escape`: an object a caller makes and lets die is one, at each field it set
     * ([anObjectThatEscapesWithItsFieldsIsAnEscapeGap]), and a test about another kind asserts the rest.
     */
    private fun Summary.besidesEscape() = gaps.filter { it.kind != "escape" }

    private fun Summary.spelled() = pairs().map { "${it.first}>${it.second}".replace(SITE, "@_") }

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

    /**
     * An object only the result leads to is spelled through `RET`: the builder drawing `make` as a
     * root draws `a -> v`, and a document that listed only `ext:B>RET` sent `P0` nowhere.
     */
    @Test
    fun aFieldOfAnObjectOnlyTheResultReachesIsSpelledThroughRet() {
        val src = "class B { int v; B next; } class A { " +
            "static B make(int a) { B b = new B(); b.v = a; return b; } " +
            "static B two(int a) { B b = new B(); B c = new B(); c.v = a; b.next = c; return b; } " +
            "static B three(int a) { B b = new B(); B c = new B(); B d = new B(); d.v = a; c.next = d; b.next = c; return b; } }"
        assertEquals(listOf("P0>RET.B.v", "ext:B@_>RET"), pairs(src, "A#make"))
        assertEquals(listOf("P0>RET.B.next.B.v", "ext:B@_>RET", "ext:B@_>RET.B.next"), pairs(src, "A#two"))
        val three = summarise(src, "A#three")
        assertEquals(listOf("depth"), three.gaps.map { it.kind })
        assertEquals("RET.B.next.B.next.B.v", three.gaps.single().detail)
    }

    /**
     * An object handed to an opaque call, or thrown, leaves with its fields, and no output spells
     * them: `a` reaches nothing. Not followed, so said - one `escape` gap at the write.
     */
    @Test
    fun anObjectThatEscapesWithItsFieldsIsAnEscapeGap() {
        val src = "class E { int amount; } class MyEx extends RuntimeException { int code; MyEx(int c) { code = c; } } " +
            "class A { java.util.List<E> repo; " +
            "void persist(int a) { E e = new E(); e.amount = a; repo.add(e); } " +
            "static void thrower(int a) { throw new MyEx(a); } }"
        val persist = summarise(src, "A#persist")
        assertEquals(listOf("escape"), persist.gaps.map { it.kind }, "${persist.gaps}")
        assertTrue(persist.gaps.single().detail.startsWith("E.amount"), "${persist.gaps}")
        val thrower = summarise(src, "A#thrower")
        assertEquals(listOf("escape"), thrower.gaps.map { it.kind }, "${thrower.gaps}")
        assertTrue(thrower.gaps.single().detail.startsWith("MyEx.code"), "${thrower.gaps}")
    }

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

    /**
     * A chain on a type outside the sources spells what the same calls spell with a local between
     * them. The receiver `p.a()` comes back from javac as kind `CLASS`, as a type name does, and
     * read as one it left `ext:b>RET` as the whole summary: no parameter, no first call, no gap.
     */
    @Test
    fun aCallChainedOnATypeOutsideTheSourcesKeepsItsReceiver() {
        val chained = pairs("class A { Object f(Unknown p) { return p.a().b(); } }", "A#f")
        assertEquals(
            setOf("P0>RET", "P0>ext-arg:a@_", "P0>ext-arg:b@_", "ext:a@_>RET", "ext:a@_>ext-arg:b@_", "ext:b@_>RET"),
            chained.toSet()
        )
        assertEquals(pairs("class A { Object f(Unknown p) { var x = p.a(); return x.b(); } }", "A#f").toSet(), chained.toSet())
    }

    /** The same receiver as a field, as a construction and in parentheses: each is a value, none a type name. */
    @Test
    fun aFieldOrANewOfATypeOutsideTheSourcesIsAReceiverToo() {
        val direct = setOf("P0>RET", "P0>ext-arg:b@_", "ext:b@_>RET")
        assertEquals(direct, pairs("class A { Object f(Unknown p) { return p.f.b(); } }", "A#f").toSet())
        assertEquals(direct, pairs("class A { Object f(Unknown p) { return (p).b(); } }", "A#f").toSet())
        val made = pairs("class A { Object f(int n) { return new Unknown(n).a().b(); } }", "A#f")
        assertTrue("P0>RET" in made, "$made")
        assertTrue("ext:a@_>ext-arg:b@_" in made, "$made")
        val wrapped = pairs("class A { Object f(Unknown p) { return (p.a()).b(); } }", "A#f")
        assertTrue("ext:a@_>ext-arg:b@_" in wrapped && "P0>RET" in wrapped, "$wrapped")
    }

    @Test
    fun anUnmodelledConstructIsAGap() =
        assertEquals(
            setOf("unmodelled"),
            summarise("record P(int x) {} class A { int f(Object o) { if (o instanceof P(int x)) return x; return 0; } }", "A#f").gaps.map { it.kind }.toSet()
        )

    /** The same in a `case` label whose arm reads the name: the gap, not a body that failed to lower. */
    @Test
    fun anUnmodelledPatternInACaseLabelIsAGap() =
        assertEquals(
            setOf("unmodelled"),
            summarise("record P(int x) {} class A { int f(Object o) { return switch (o) { case P(int x) -> x; default -> 0; }; } }", "A#f").gaps.map { it.kind }.toSet()
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
        assertTrue(summarise("record P(int x) {} class A { int g(Object o) { if (o instanceof P(int x)) return 1; return 0; } int f(Object o) { return g(o); } }", "A#f").gaps.any { it.kind == "unmodelled" })

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
     * Through a receiver that is either of two objects, the callee's write is a strong update on
     * each, as the builder's inlined write is - the known gap `docs/decisions/joins.md` names ("a
     * write through a receiver with several holders is a strong update on each"), which loses the
     * other object's old value `k`. Carried over on purpose, neither better nor worse than the
     * builder - and said, with an `alias` gap at the call.
     */
    @Test
    fun aCalleeEffectOnEitherOfTwoObjectsIsAStrongUpdateOnEachLikeTheBuilder() {
        val s = summarise(
            "class B { int v; } class A { void set(B b, int a) { b.v = a; } " +
                "int f(boolean c, int a, int k) { B x = new B(); B y = new B(); x.v = k; y.v = k; B p = c ? x : y; set(p, a); return x.v; } }",
            "A#f"
        )
        assertEquals(listOf("P1>RET"), s.spelled())
        assertEquals(listOf("alias"), s.besidesEscape().map { it.kind }, "${s.gaps}")
        assertTrue(s.besidesEscape().single().detail.contains("B.v"), "${s.gaps}")
    }

    /** The same write made here: `p.v = a` on either of two objects loses `k` from the other, and says so. */
    @Test
    fun aWriteOnEitherOfTwoObjectsIsAnAliasGap() {
        val s = summarise(
            "class B { int v; } class A { " +
                "int f(boolean c, int a, int k) { B x = new B(); B y = new B(); x.v = k; y.v = k; B p = c ? x : y; p.v = a; return x.v; } }",
            "A#f"
        )
        assertEquals(listOf("P1>RET"), s.spelled())
        assertEquals(listOf("alias"), s.besidesEscape().map { it.kind }, "${s.gaps}")
        val one = summarise("class B { int v; } class A { int f(int a) { B x = new B(); x.v = a; return x.v; } }", "A#f")
        assertEquals(emptyList(), one.besidesEscape())
    }

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
        val handedOn = get + "B f(java.util.List<B> l, int a) { return get(l, a); } int h(java.util.List<B> l, int a) { return f(l, a).v; } }"
        assertEquals(emptyList(), summarise(handedOn, "A#f").gaps.toList())
        assertTrue("P1>RET" in pairs(handedOn, "A#h"), "${pairs(handedOn, "A#h")}")
    }

    /**
     * The callee is read as if its parameters were distinct objects; passed one object twice, a
     * read through one of them of a field written through the other is a flow the summary cannot
     * give, so it says so. The builder, inlining, draws `P0>RET`.
     */
    @Test
    fun oneObjectForTwoParametersIsAnAliasGap() {
        val s = summarise("class B { int v; } class A { int g(B p, B q, int a) { q.v = a; return p.v; } int f(int a) { B b = new B(); return g(b, b, a); } }", "A#f")
        assertEquals(listOf("alias"), s.besidesEscape().map { it.kind }, "${s.gaps}")
        assertTrue(s.besidesEscape().single().detail.contains("B.v"), "${s.gaps}")
    }

    /** The same through `this` and a parameter: `o.g(o, a)` writes `o.x` as `P0` and reads it as `this`. */
    @Test
    fun theReceiverPassedAsAParameterIsAnAliasGap() {
        val s = summarise("class A { int x; int g(A o, int a) { o.x = a; return x; } int f(A o, int a) { return o.g(o, a); } }", "A#f")
        assertEquals(listOf("alias"), s.gaps.map { it.kind }, "${s.gaps}")
    }

    /**
     * Two writes of one field through two parameters, passed one object: which the callee made
     * last decides what the field holds, and the summary does not know the order. Joining both
     * gives `[P0>RET, P1>RET]` where the builder, inlining, draws `P1>RET` only - so it is a gap.
     */
    @Test
    fun twoWritesThroughTwoParametersOnOneObjectAreAnAliasGap() {
        val s = summarise(
            "class B { int v; } class A { void g(B p, B q, int a, int k) { q.v = a; p.v = k; } " +
                "int f(int a, int k) { B b = new B(); g(b, b, a, k); return b.v; } }",
            "A#f"
        )
        assertEquals(listOf("alias"), s.besidesEscape().map { it.kind }, "${s.gaps}")
        assertTrue(s.besidesEscape().single().detail.contains("B.v"), "${s.gaps}")
        val apart = summarise(
            "class B { int v; } class A { void g(B p, B q, int a, int k) { q.v = a; p.v = k; } " +
                "int f(int a, int k) { B b = new B(); B c = new B(); g(b, c, a, k); return b.v; } }",
            "A#f"
        )
        assertEquals(emptyList(), apart.besidesEscape())
        assertEquals(listOf("P1>RET"), apart.pairs().map { "${it.first}>${it.second}" })
    }

    /** Two objects passed for two parameters do not alias: the same callee, no gap. */
    @Test
    fun twoObjectsForTwoParametersAreNoAliasGap() {
        val s = summarise("class B { int v; } class A { int g(B p, B q, int a) { q.v = a; return p.v; } int f(int a) { B b = new B(); B c = new B(); return g(b, c, a); } }", "A#f")
        assertEquals(emptyList(), s.besidesEscape())
    }

    /**
     * Two untracked objects the callee stores in two fields are two objects in the caller too: a
     * write through one is not read back through the other. The builder draws `[]`.
     */
    @Test
    fun standInsAnInstantiatedEffectMakesAreOnePerField() {
        val src = "class B { int v; } class A { B b1; B b2; void set2(B p, B q) { b1 = p; b2 = q; } " +
            "int f(java.util.List<B> l, int a) { set2(l.get(0), l.get(1)); b1.v = a; return b2.v; } }"
        val p = pairs(src, "A#f")
        assertTrue("P1>RET" !in p && "P1>this.A.b2.B.v" !in p, "$p")
        assertTrue("P1>this.A.b1.B.v" in p, "$p")
    }

    /** A callee whose body cannot be lowered is opaque to its caller, and the caller says why. */
    @Test
    fun aCalleeThatCannotBeLoweredIsABodyGap() {
        val s = summarise("class A { int g(int a) { int t; return t; } int f(int a) { return g(a); } }", "A#f")
        assertEquals(listOf("body"), s.gaps.map { it.kind })
        assertTrue("P0>RET" in pairs("class A { int g(int a) { int t; return t; } int f(int a) { return g(a); } }", "A#f"))
    }

    // Recursion and loops.

    @Test fun recursionReachesAFixpoint() =
        assertEquals(listOf("P0>RET", "P1>RET"), pairs("class A { int f(int n, int k) { return n == 0 ? k : f(n - 1, k); } }", "A#f"))
    @Test fun mutualRecursionReachesAFixpoint() =
        assertEquals(listOf("P1>RET"), pairs("class A { int e(int n, int k) { if (n == 0) return k; return o(n - 1, k); } int o(int n, int k) { return e(n - 1, k); } }", "A#o").filter { it == "P1>RET" })

    /**
     * The loop's condition does not reach `t`: a loop's header phi is not gated, since its condition
     * is computed from the phi (`docs/decisions/joins.md`), and the builder draws `P1>RET` alone.
     * Nothing reaches the return on the first pass, so this holds only once the body has re-run.
     */
    @Test fun anAccumulatorLoopIsAFixpoint() =
        assertEquals(listOf("P1>RET"), pairs("class A { int f(int n, int a) { int t = 0; for (int i = 0; i < n; i++) t = t + a; return t; } }", "A#f"))
    /** It terminates because the call-site limit makes every level past the first one object, which is a `depth` gap. */
    @Test fun aRecursiveFactoryTerminates() {
        val s = summarise("class N { N next; } class A { N f(int n) { N x = new N(); if (n > 0) x.next = f(n - 1); return x; } }", "A#f")
        assertTrue(s.gaps.none { it.kind == "fixpoint" }, "${s.gaps}")
        assertTrue(s.gaps.any { it.kind == "depth" && it.detail.contains("two objects one") }, "${s.gaps}")
    }

    /**
     * `m.with(a).with(w)`: both calls start where `m` does, so a call site named by its position
     * alone made the two `new M()` one object, and the second call's write of `v` overwrote the
     * first's - `P2>RET` drawn and `P1>RET` lost. The builder draws `a -> v -> RET`.
     */
    @Test
    fun twoChainedCallsMakeTwoObjects() {
        val src = "class M { int v; M prev; M with(int x) { M n = new M(); n.v = x; n.prev = this; return n; } } " +
            "class A { int f(M m, int a, int w) { M r = m.with(a).with(w); return r.prev.v; } }"
        assertEquals(listOf("P1>RET"), summarise(src, "A#f").spelled().filter { it.endsWith(">RET") })
    }

    /** The same through a factory and a callee that stores another of its objects: `B.of(a).link(w)`. */
    @Test
    fun aChainedFactoryAndCallKeepTheirObjectsApart() {
        val src = "class B { int v; B next; static B of(int v) { B b = new B(); b.v = v; return b; } " +
            "B link(int w) { this.next = of(w); return this; } } " +
            "class A { int chained(int a, int w) { B r = B.of(a).link(w); return r.v; } " +
            "int split(int a, int w) { B r = B.of(a); r = r.link(w); return r.v; } }"
        for (method in listOf("A#chained", "A#split")) {
            assertEquals(listOf("P0>RET"), summarise(src, method).spelled().filter { it.endsWith(">RET") }, method)
        }
    }

    /**
     * `m` reaches the return only through the recursive call, as what the callee returns for its
     * own `k` - so only a second pass, reading the first pass's summary, finds it. The builder,
     * cutting the recursion as an EXTERNAL, draws all three.
     */
    @Test
    fun aRecursionThatSwapsItsArgumentsNeedsASecondPass() {
        val s = summarise("class A { int f(int n, int k, int m) { return n == 0 ? k : f(n - 1, m, k); } }", "A#f")
        assertEquals(listOf("P0>RET", "P1>RET", "P2>RET"), s.spelled())
        assertEquals(emptyList(), s.gaps.toList())
    }

    /** Out of budget, the summary keeps what it last had, and says at the method that it is not done. */
    @Test
    fun aRecursionOverBudgetKeepsItsLastValueAndAFixpointGap() {
        val s = summarise("class A { int f(int n, int k, int m) { return n == 0 ? k : f(n - 1, m, k); } }", "A#f", budget = 1)
        assertEquals(listOf("P0>RET", "P1>RET"), s.spelled())
        assertEquals(listOf("fixpoint"), s.gaps.map { it.kind })
        assertTrue(s.gaps.single().detail.contains("A#f"), "${s.gaps}")
        assertEquals("A.java:1:11", s.gaps.single().at)
    }

    /**
     * A cycle has one answer, whichever of its methods is asked for first. A cut would memoise
     * the second method with the first still unknown - `o` asked after `e` would return nothing.
     */
    @Test
    fun mutualRecursionDoesNotDependOnWhichMethodIsAskedFirst() {
        val src = "class A { int e(int n, int k) { if (n == 0) return k; return o(n - 1, k); } int o(int n, int k) { return e(n - 1, k); } }"
        val oFirst = summariseInOrder(src, listOf("A#o", "A#e")).mapValues { it.value.spelled() }
        val eFirst = summariseInOrder(src, listOf("A#e", "A#o")).mapValues { it.value.spelled() }
        assertEquals(oFirst, eFirst)
        assertEquals(mapOf("A#o" to listOf("P0>RET", "P1>RET"), "A#e" to listOf("P0>RET", "P1>RET")), oFirst)
    }

    /**
     * `a` moves one variable per pass, so `x` has it only on the third; the builder joins every
     * back edge and draws `P1>RET`. Given fewer passes than that, the loop says it stopped short.
     */
    @Test
    fun aLoopIsRunUntilStableWithinTheBudget() {
        val src = "class A { int f(int n, int a) { int x = 0; int y = 0; int z = 0; for (int i = 0; i < n; i++) { x = y; y = z; z = a; } return x; } }"
        val s = summarise(src, "A#f")
        assertEquals(listOf("P1>RET"), s.spelled())
        assertEquals(emptyList(), s.gaps.toList())
        val short = summarise(src, "A#f", budget = 2)
        assertEquals(listOf("fixpoint"), short.gaps.map { it.kind })
    }

    /**
     * Three calls deep, an object is still named by where it was allocated: the cut drops the
     * middle call site, not the allocation, so `make`'s two `new B()` stay two objects in `top` and
     * `top` draws `P1>RET` alone, as the builder does. Dropping the allocation site instead would
     * join their fields into a `P0>RET` the builder does not draw.
     */
    @Test
    fun threeCallsDeepAnObjectKeepsItsAllocationSite() {
        val src = "class B { int v; } class P { B x; B y; } class A { " +
            "P make(int a, int k) { P p = new P(); B x = new B(); B y = new B(); x.v = a; y.v = k; p.x = x; p.y = y; return p; } " +
            "P mid(int a, int k) { return make(a, k); } P outer(int a, int k) { return mid(a, k); } " +
            "int top(int a, int k) { return mid(a, k).y.v; } }"
        val s = summariseInOrder(src, listOf("A#make", "A#outer", "A#top"))
        assertEquals(listOf("P1>RET"), s.getValue("A#top").spelled())
        assertEquals(emptyList(), s.getValue("A#top").besidesEscape())
        val made = s.getValue("A#make").ret.objs.single() as AllocObj
        val outer = s.getValue("A#outer").ret.objs.single() as AllocObj
        assertEquals(2, outer.site.size)
        assertEquals(made.site.single(), outer.site.last())
    }

    /**
     * Two calls to one factory in the middle method are two objects there, told apart only by the
     * middle call sites - which the cut drops one call further out. They are one object in the
     * caller, which joins their fields - `P0>RET` the builder does not draw - so it says so.
     */
    @Test
    fun twoCallsInTheMiddleOneObjectPastTheCutAreADepthGap() {
        val src = "class B { int v; } class Q { B l; B r; } class A { " +
            "B make(int a) { B b = new B(); b.v = a; return b; } " +
            "Q mid(int a, int k) { Q q = new Q(); q.l = make(a); q.r = make(k); return q; } " +
            "int top(int a, int k) { return mid(a, k).r.v; } }"
        val s = summarise(src, "A#top")
        assertEquals(listOf("depth"), s.besidesEscape().map { it.kind })
        assertTrue("P1>RET" in s.spelled(), "${s.spelled()}")
        assertEquals(emptyList(), summarise(src, "A#mid").gaps.toList())
    }

    /**
     * A query that throws bubbles, and leaves nothing behind: asked again, the cycle is solved from
     * scratch, not answered with the nothing it held while it was running.
     */
    @Test
    fun aQueryThatThrowsLeavesNoHalfSolvedSummaries() {
        val src = "class A { int e(int n, int k) { if (n == 0) return k; return o(n - 1, k); } int o(int n, int k) { return e(n - 1, k); } }"
        val file = dir.resolve("A.java")
        Files.writeString(file, src)
        AstReader(dir).analyse(listOf(file)).use { analysis ->
            val builder = IrGraphBuilder(analysis.globalCtx)
            var failures = 1
            val bodyOf = { m: codeflow.graph.Method ->
                if (m.element.simpleName.contentEquals("o") && failures-- > 0) throw IllegalStateException("lowering o")
                builder.bodyOf(m)
            }
            val summarizer = Summarizer(analysis.globalCtx, bodyOf, builder::initializersOf)
            val e = analysis.methods().first { MethodSpec.matches(it, "A#e") }
            assertEquals("lowering o", kotlin.runCatching { summarizer.summary(e) }.exceptionOrNull()?.message)
            assertEquals(listOf("P0>RET", "P1>RET"), summarizer.summary(e).spelled())
            val o = analysis.methods().first { MethodSpec.matches(it, "A#o") }
            assertEquals(listOf("P0>RET", "P1>RET"), summarizer.summary(o).spelled())
        }
    }
}
