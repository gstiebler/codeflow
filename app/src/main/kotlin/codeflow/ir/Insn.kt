package codeflow.ir

import javax.lang.model.element.Element

/**
 * A value produced inside one method body, named by the position of the instruction that produced
 * it.
 *
 * An index rather than a reference to the instruction, so a body is a flat list that can be read,
 * printed and asserted on without following pointers - and so that a phi (§1) can name a value
 * defined on a path that has not been walked yet.
 */
@JvmInline
value class Val(val index: Int) {
    override fun toString() = index.toString()
}

/**
 * One step of what a method does, with javac's answers already folded in.
 *
 * The point of the split is that everything requiring a *tree* has happened by the time an
 * instruction exists: which declaration a name resolved to, which overload a call selected, whether
 * a value is a primitive or a reference. What is left is dataflow, which is what the graph draws.
 *
 * [source] is `path:line:col` and is required, for the same reason `GraphNode.Base` requires one:
 * the first question about any instruction, and about any box drawn from it, is which line of which
 * file it came from.
 */
sealed class Insn(val source: String) {
    /** How this instruction reads in a test, without its index - see [MethodBody.render]. */
    abstract fun render(): String
}

/** A literal written in the source: `5`, `"x"`, `null`. */
class Const(val text: String, source: String) : Insn(source) {
    override fun render() = "const $text"
}

/**
 * A read of a local or a parameter.
 *
 * [element] is the declaration javac resolved it to, which is what makes two `x`es in two scopes
 * two different variables without anything here comparing names.
 */
class ReadLocal(val name: String, val element: Element?, source: String) : Insn(source) {
    override fun render() = "read $name"
}

/** A write to a local or a parameter: the declaration `int bonus = ...` and the assignment alike. */
class WriteLocal(
    val name: String,
    val element: Element?,
    val value: Val,
    source: String
) : Insn(source) {
    override fun render() = "write $name <- $value"
}

/** `a + b`. [label] is already the display form, since `/` cannot be drawn as itself - see below. */
class BinOp(val label: String, val left: Val, val right: Val, source: String) : Insn(source) {
    override fun render() = "binOp $label $left $right"
}

/**
 * `-x`, `!flag`, `i++`.
 *
 * Its own instruction rather than the operand handed back, which is what a scanner that walks the
 * children and returns one of their results does: `!flag` becomes the read of `flag`, and nothing
 * downstream can tell a value from its negation.
 */
class UnOp(val label: String, val operand: Val, source: String) : Insn(source) {
    override fun render() = "unOp $label $operand"
}

/**
 * One value chosen from several, each of which reaches the result: `c ? a : b`, a `switch`
 * expression, the elements of an array.
 *
 * Which one is chosen is control flow, which is not modelled yet - §1 replaces this with a phi at a
 * join. Until then every input reaches the output, which is coarse rather than wrong: the reader is
 * shown every value the expression can produce, and the condition that decides among them.
 */
class Select(val label: String, val inputs: List<Val>, source: String) : Insn(source) {
    override fun render() = "select $label ${inputs.joinToString(" ")}"
}

/**
 * The object an access or a call happens on.
 *
 * Three cases rather than a nullable value, because "no receiver written" and "no object at all"
 * are different facts and were conflated: an unqualified `record()` inside a method of Gauge runs on
 * the same object the method does, while `Math.abs(x)` runs on nothing. Both were lowered to a
 * missing receiver, so every field the first one wrote landed where nobody could look it up.
 */
sealed class Receiver {
    /** `value`, `this.value`, `record()`, `super.m()` - the object the enclosing method is on. */
    data object Enclosing : Receiver() {
        override fun toString() = "this"
    }

    /** `Math.abs(x)`, `Counter.total` - the receiver is a type name and produces no value. */
    data object TypeName : Receiver() {
        override fun toString() = "static"
    }

    /** Any expression: `counter.advance()`, `of(x).getAmount()`, `charges[0].amount`. */
    class Value(val value: Val) : Receiver() {
        override fun toString() = value.toString()
    }
}

/**
 * A field read. [receiver] says which object it comes off; [element] says which declaration it is.
 *
 * Both halves are needed and neither substitutes for the other: one field declaration lives at a
 * different address in every instance, so the declaration alone would make `a.total` and `b.total`
 * one variable.
 */
class ReadField(
    val receiver: Receiver,
    val name: String,
    val element: Element?,
    source: String
) : Insn(source) {
    override fun render() = "readField $receiver.$name"
}

/** A field write - the same access in the other direction. */
class WriteField(
    val receiver: Receiver,
    val name: String,
    val element: Element?,
    val value: Val,
    source: String
) : Insn(source) {
    override fun render() = "writeField $receiver.$name <- $value"
}

/**
 * `this`, as a value: `return this`, `new Wrapper(this)`.
 *
 * Distinct from a field access *on* this, which names the object as its receiver and produces no
 * value for it. Here the object itself is what the expression produces.
 */
class ThisRef(source: String) : Insn(source) {
    override fun render() = "this"
}

/**
 * A call. What it does not carry is the callee's body.
 *
 * That is the split the IR exists for: [target] is the declaration javac selected, and whether it
 * has a body to draw, how many times it is drawn, and which object it runs on at this particular
 * call site are all the builder's questions. Null when javac resolved nothing of method kind, which
 * means the call leaves the analysed sources.
 */
class Call(
    val name: String,
    val target: Element?,
    val receiver: Receiver,
    val args: List<Val>,
    source: String
) : Insn(source) {
    override fun render() = "call $name on $receiver" + args.joinToString("") { " $it" }
}

/**
 * `super(...)` or `this(...)`: a constructor running on the object already being built.
 *
 * Unlike [New] no object is created, which is what lets a field assigned in a superclass
 * constructor be read from the subclass.
 */
class Delegate(val target: Element, val args: List<Val>, source: String) : Insn(source) {
    override fun render() = "delegate" + args.joinToString("") { " $it" }
}

/** `new X(...)`: one instruction that both creates the object and produces it as a value. */
class New(
    val typeName: String,
    val constructor: Element?,
    val args: List<Val>,
    source: String
) : Insn(source) {
    override fun render() = "new $typeName" + args.joinToString("") { " $it" }
}

/** `return x;`, or `return;` - which carries no value and so joins nothing to the result. */
class Return(val value: Val?, source: String) : Insn(source) {
    override fun render() = "return" + (value?.let { " $it" } ?: "")
}
