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
    /**
     * The values this instruction consumes, whichever field they sit in.
     *
     * Uniform access to the operands is what lets a pass walk a body without knowing every
     * instruction: the forward-reference check in the tests, and the reachability and phi placement
     * §1 will need. Every one of them is produced by an earlier instruction.
     */
    abstract val inputs: List<Val>

    /** How this instruction reads in a test, without its index - see [MethodBody.render]. */
    abstract fun render(): String
}

/** A literal written in the source: `5`, `"x"`, `null`. */
class Const(val text: String, source: String) : Insn(source) {
    override val inputs get() = emptyList<Val>()
    override fun render() = "const $text"
}

/**
 * A parameter, as a definition: whatever the caller bound to it.
 *
 * There is no matching *read* instruction, and that is the point. A use of a local is lowered to the
 * instruction that defined it, so `binOp + 0 3` names the parameter and the multiply rather than
 * naming a variable twice and leaving which write it meant to be settled downstream. Parameters are
 * definitions like any other, which is what makes that total: every use in a body resolves to an
 * instruction, including the ones that arrive from outside it.
 *
 * [index] is the position in the declaration, since that is what binds it to an argument.
 */
class Param(val name: String, val element: Element?, val index: Int, source: String) : Insn(source) {
    override val inputs get() = emptyList<Val>()
    override fun render() = "param $name"
}

/** A write to a local or a parameter: the declaration `int bonus = ...` and the assignment alike. */
class WriteLocal(
    val name: String,
    val element: Element?,
    val isPrimitive: Boolean,
    val value: Val,
    source: String
) : Insn(source) {
    override val inputs get() = listOf(value)
    override fun render() = "write $name <- $value"
}

/**
 * What decided which path arrived at a [Phi], when that is a value the source computed.
 *
 * A classic phi carries no condition: its operands are matched positionally against a control-flow
 * graph's incoming edges, and the test lives in the block that branches. There is no such graph
 * here, so nothing recorded the association at all - the `==` of an `if` was drawn with both
 * operands flowing in and no edge leaving it, the value the whole branch turns on rendered as
 * though nothing consumed it. Naming it here is what SSA calls a *gated* phi, and it is the form
 * [Select] has always had for the same choice written as `?:`.
 *
 * [label] is what the box is captioned - `if`, `switch` - since a join that is a choice should say
 * which construct made it rather than repeat the variable's name a third time.
 *
 * [arms] names the edge each path arrives on, for the paths where that can be said honestly: a
 * value reaching the join down exactly one path of an `if` is the true one or the false one, and a
 * value reaching it down several is neither. Keyed by the value rather than held as a list
 * alongside the paths because [Lowering] collapses the reaching values with `distinct`, which
 * loses any positional correspondence a parallel list would depend on.
 */
class Gate(val label: String, val value: Val, val arms: Map<Val, String> = emptyMap())

/**
 * The value a variable holds where two paths come back together.
 *
 * One instruction per variable per join, taking the value that reaches it from each path, so a use
 * below the join still resolves to a single instruction. Without it there is one slot per variable
 * and the branch walked last wins - `if (c) { b = 13; }` followed by `use(b)` was drawn as taking
 * only the 13, which is the diagram asserting the other value cannot arrive.
 *
 * It is drawn, as a box of its own, because it is a place in the program: the two values really do
 * meet there, and the alternative - joining every path straight onto every use - loses where that
 * happened and fans out with every nested branch.
 *
 * [gate] is what chose, where anything did. An `if` and a `switch` statement have theirs lowered
 * before the join and so carry one; a loop's condition is computed *from* its header phi and lowered
 * after it, and which `throw` reached a handler is control flow rather than a value, so both of
 * those are ungated and the box falls back to the variable's name.
 */
class Phi(
    val name: String,
    val element: Element?,
    val isPrimitive: Boolean,
    entry: Val,
    source: String,
    val gate: Gate? = null
) : Insn(source) {
    private val arrived = arrayListOf(entry)

    /**
     * The value from each path, which is what the variable can hold.
     *
     * Deliberately not [inputs]: the gate is consumed here too, but the variable is never *equal*
     * to the thing that chose it. Unioning the objects over the inputs instead would make a
     * `switch (name)` over a String point the joined variable at the selector's object, filing one
     * object's fields under another's name - the mistake [Select.alternatives] exists to prevent,
     * made in the other half of the model.
     */
    val paths: List<Val> get() = arrived

    override val inputs: List<Val> get() = arrived + listOfNotNull(gate?.value)

    /**
     * A path arriving after this instruction: the bottom of a loop body, back to its header.
     *
     * The one place an instruction names a value produced later in the list, and the reason [Val]
     * is an index rather than a reference. A loop's header has to be lowered before its body -
     * every use inside the body resolves to it - and what the body leaves behind is only known
     * once the body has been walked, so the phi is completed rather than built in one go.
     */
    fun addPath(value: Val) {
        arrived.add(value)
    }

    override fun render() = "phi $name" + arrived.joinToString("") { " $it" } +
            (gate?.let { " ? ${it.value}" } ?: "")
}

/** `a + b`. [label] is already the display form, since `/` cannot be drawn as itself - see below. */
class BinOp(val label: String, val left: Val, val right: Val, source: String) : Insn(source) {
    override val inputs get() = listOf(left, right)
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
    override val inputs get() = listOf(operand)
    override fun render() = "unOp $label $operand"
}

/**
 * One value chosen from several, each of which reaches the result: `c ? a : b`, a `switch`
 * expression, the elements of an array.
 *
 * Every input reaches the output, which is coarse rather than wrong: the reader is shown every value
 * the expression can produce, and the condition that decides among them.
 *
 * Not a [Phi], although both stand for a choice and both now name what chose. This is an expression
 * the source wrote and its node stands for the value that expression produced; a phi is a variable
 * at a place where two paths meet, and exists whether or not anything was written there. `c ? a : b`
 * and `if (c) x = a; else x = b;` should come out the same shape, which is why [Gate] exists - they
 * are the same choice, and drawing one with its condition and the other without was the tool
 * disagreeing with itself about the same program.
 *
 * [alternatives] is which of the inputs the value can *be*, which is not all of them and is not
 * something the graph could work out from the list. `c ? a : b` can be `a` or `b` and never `c`,
 * and `new Holder[]{a, b}` is an array and neither of the objects in it. Only the alternatives pass
 * on which objects the result could be, so getting this wrong makes an array be its own elements -
 * one object's fields filed under another's, which is the diagram being confidently about the wrong
 * thing. Empty when nothing the expression produces is one of its inputs.
 *
 * [condition] and [arms] are the same pair [Gate] carries, and are used the same way: the condition
 * is which input decided, and the arms name the edge each alternative arrives on. Only a `?:` has
 * arms - a two-case `switch` expression also has two alternatives and a condition, and calling
 * those true and false would be a label the source never wrote.
 */
class Select(
    val label: String,
    override val inputs: List<Val>,
    source: String,
    val alternatives: List<Val> = emptyList(),
    val condition: Val? = null,
    val arms: Map<Val, String> = emptyMap()
) : Insn(source) {
    override fun render() = "select $label ${inputs.joinToString(" ")}"
}

/**
 * The object an access or a call happens on.
 *
 * Four cases rather than a nullable value, because "no receiver written", "no object at all" and
 * "this object, but the implementation the written class names" are different facts. The first two
 * were conflated once already: an unqualified `record()` inside a method of Gauge runs on the same
 * object the method does, while `Math.abs(x)` runs on nothing, and both were lowered to a missing
 * receiver, so every field the first one wrote landed where nobody could look it up.
 */
sealed class Receiver {
    /** The value it consumes, which is none unless an expression was written. */
    open val inputs: List<Val> get() = emptyList()

    /** `value`, `this.value`, `record()` - the object the enclosing method is on. */
    data object Enclosing : Receiver() {
        override fun toString() = "this"
    }

    /** `super.m()` - the same object, but the implementation the written class names, not the one it is. */
    data object Super : Receiver() {
        override fun toString() = "super"
    }

    /** `Math.abs(x)`, `Counter.total` - the receiver is a type name and produces no value. */
    data object TypeName : Receiver() {
        override fun toString() = "static"
    }

    /** Any expression: `counter.advance()`, `of(x).getAmount()`, `charges[0].amount`. */
    class Value(val value: Val) : Receiver() {
        override val inputs get() = listOf(value)
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
    val isPrimitive: Boolean,
    source: String
) : Insn(source) {
    override val inputs get() = receiver.inputs
    override fun render() = "readField $receiver.$name"
}

/** A field write - the same access in the other direction. */
class WriteField(
    val receiver: Receiver,
    val name: String,
    val element: Element?,
    val isPrimitive: Boolean,
    val value: Val,
    source: String
) : Insn(source) {
    override val inputs get() = receiver.inputs + value
    override fun render() = "writeField $receiver.$name <- $value"
}

/**
 * `this`, as a value: `return this`, `new Wrapper(this)`.
 *
 * Distinct from a field access *on* this, which names the object as its receiver and produces no
 * value for it. Here the object itself is what the expression produces.
 */
class ThisRef(source: String) : Insn(source) {
    override val inputs get() = emptyList<Val>()
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
    override val inputs get() = receiver.inputs + args
    override fun render() = "call $name on $receiver" + args.joinToString("") { " $it" }
}

/**
 * `super(...)` or `this(...)`: a constructor running on the object already being built.
 *
 * Unlike [New] no object is created, which is what lets a field assigned in a superclass
 * constructor be read from the subclass.
 */
class Delegate(val target: Element, val args: List<Val>, source: String) : Insn(source) {
    override val inputs get() = args
    override fun render() = "delegate" + args.joinToString("") { " $it" }
}

/** `new X(...)`: one instruction that both creates the object and produces it as a value. */
class New(
    val typeName: String,
    val constructor: Element?,
    val args: List<Val>,
    source: String
) : Insn(source) {
    override val inputs get() = args
    override fun render() = "new $typeName" + args.joinToString("") { " $it" }
}

/**
 * A name introduced by something other than a declaration or an assignment.
 *
 * `for (String name : names)`, `catch (IOException failure)`, `value instanceof String text`,
 * `case String text ->`. Each binds a name that later statements read, and each was missing a
 * visitor at some point - so the read found nothing, and the failure surfaced several lines below
 * the construct that was supposed to declare it, blaming a line that was not at fault.
 *
 * [value] is what flows in, and is null when nothing does: which `throw` reached a handler is
 * control flow, and a lambda's parameters are filled in by whoever calls it, so a value with no
 * source is the honest drawing rather than a guess.
 */
class Bind(
    val name: String,
    val element: Element?,
    val isPrimitive: Boolean,
    val value: Val?,
    val identity: Identity,
    source: String
) : Insn(source) {
    override val inputs get() = listOfNotNull(value)
    override fun render() = "bind $name" + (value?.let { " <- $it" } ?: "")
}

/**
 * Which object a bound name stands for, which is not always the one the value flowing in is.
 *
 * Kept apart because getting it wrong files a whole object's fields under another object's name,
 * and the diagram that comes out of that is complete, readable and about the wrong thing.
 */
sealed class Identity {
    /** The same object: `value instanceof String text` names what it matched. */
    data object OfValue : Identity()

    /** One of its own: a loop element is not its collection, and a caught exception has no value. */
    data object Fresh : Identity()

    /** None to be had: a lambda's parameter is filled in by a caller not visible from here. */
    data object Unknown : Identity()
}

/**
 * A value from something codeflow does not look inside: a method reference, an enum constant
 * declared elsewhere.
 *
 * Distinct from [Unmodelled], which is a construct codeflow *could* have modelled and has not. This
 * one has nothing more to show - whoever invokes `DisbursementData::disbursementDate` decides what
 * runs in it, and that is not visible from here.
 */
class Opaque(val label: String, override val inputs: List<Val>, source: String) : Insn(source) {
    override fun render() = "opaque $label" + inputs.joinToString("") { " $it" }
}

/**
 * A construct codeflow does not model, drawn as itself rather than as one of its children.
 *
 * The distinction from an unresolved call is deliberate and the two must not merge: a call outside
 * the sources is a limit of the *corpus*, and this is a limit of *codeflow* - code sitting in the
 * directory that the diagram is not showing. Its operands still flow in, because a value drawn as
 * arriving from nowhere is the failure this whole area exists to prevent.
 */
class Unmodelled(val kind: String, override val inputs: List<Val>, source: String) : Insn(source) {
    override fun render() = "unmodelled $kind" + inputs.joinToString("") { " $it" }
}

/** `return x;`, or `return;` - which carries no value and so joins nothing to the result. */
class Return(val value: Val?, source: String) : Insn(source) {
    override val inputs get() = listOfNotNull(value)
    override fun render() = "return" + (value?.let { " $it" } ?: "")
}
