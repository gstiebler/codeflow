package codeflow.ir

import codeflow.graph.GraphNodeId
import codeflow.graph.PosStack
import codeflow.java.Symbols
import com.sun.source.tree.ClassTree
import com.sun.source.util.JavacTask
import com.sun.source.util.Trees
import java.net.URI
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.tools.JavaFileObject
import javax.tools.SimpleJavaFileObject
import javax.tools.ToolProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The alias model on its own: no graph, no instructions. */
class HeapTest {

    private fun id(name: String) = GraphNodeId(PosStack(), name)

    private fun compile(source: String): Pair<Symbols, List<Element>> {
        val file = object : SimpleJavaFileObject(URI.create("string:///A.java"), JavaFileObject.Kind.SOURCE) {
            override fun getCharContent(ignoreEncodingErrors: Boolean) = source
        }
        val task = ToolProvider.getSystemJavaCompiler()
            .getTask(null, null, null, listOf("-proc:none"), null, listOf(file)) as JavacTask
        val units = task.parse()
        task.analyze()
        val trees = Trees.instance(task)
        val fields = units.flatMap { unit ->
            unit.typeDecls.filterIsInstance<ClassTree>().flatMap { it.members }
                .mapNotNull { trees.getElement(trees.getPath(unit, it)) }
        }.filter { it.kind == ElementKind.FIELD }
        return Symbols.collect(trees, task.elements, units) to fields
    }

    @Test
    fun `ids run from zero per heap and heaps are independent`() {
        val (symbols, _) = compile("class A {}")
        val one = Heap(symbols)
        val two = Heap(symbols)
        assertEquals("MemPos(0, 'a')", one.allocate("a").toString())
        assertEquals("MemPos(1, 'b')", one.allocate("b").toString())
        assertEquals("MemPos(0, 'c')", two.allocate("c").toString())
    }

    @Test
    fun `objectsOf is empty until set and a later set replaces`() {
        val (symbols, _) = compile("class A {}")
        val heap = Heap(symbols)
        val x = id("x")
        assertTrue(heap.objectsOf(x).isEmpty())
        val a = heap.allocate("a")
        val b = heap.allocate("b")
        heap.setObjects(x, setOf(a))
        assertEquals(setOf(a), heap.objectsOf(x))
        heap.setObjects(x, setOf(b))
        assertEquals(setOf(b), heap.objectsOf(x))
    }

    @Test
    fun `a static field has one holder per class and an instance field has none`() {
        val (symbols, fields) = compile("class A { static int s; static int t; int i; }")
        val heap = Heap(symbols)
        val (s, t, i) = fields
        val holder = assertNotNull(heap.staticHolder(s, "s"))
        assertSame(holder, heap.staticHolder(t, "t"))
        assertNull(heap.staticHolder(i, "i"))
        assertEquals(listOf(holder), heap.staticHolders().toList())
    }
}
