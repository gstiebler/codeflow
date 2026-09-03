// Dump a CPG as the JSON that spike/src/types.ts describes. Nothing else.
//
// This script is deliberately stupid. It filters nothing, decides nothing, and knows nothing about
// endpoints, repositories or exceptions - every one of those judgements lives in TypeScript, where
// it runs in milliseconds against a hand-written fixture. A Joern run costs about two and a half
// minutes and the Scala compiles first, so a typo here costs a full run; a typo in a boundary rule
// costs a second. That asymmetry is the whole reason for the split. If a rule feels tempting to add
// here, it belongs in spike/src/boundaries.ts.
//
// Run: joern -J-Xmx10g --script export.sc --param cpgFile=x.cpg --param out=x.json --param commit=abc

import java.io.PrintWriter

@main def main(cpgFile: String, out: String, commit: String) = {
  importCpg(cpgFile)

  def q(s: String): String = {
    val b = new StringBuilder("\"")
    s.foreach {
      case '"'          => b ++= "\\\""
      case '\\'         => b ++= "\\\\"
      case '\n'         => b ++= "\\n"
      case '\r'         => b ++= "\\r"
      case '\t'         => b ++= "\\t"
      case c if c < ' ' => b ++= f"\\u${c.toInt}%04x"
      case c            => b += c
    }
    b += '"'
    b.toString
  }

  // `typeFullName` lives on the concrete node classes, not on the Expression trait, so it has to be
  // asked for by case. Getting this wrong costs a compile round trip.
  def typeOf(n: nodes.AstNode): String = n match {
    case x: nodes.Call            => x.typeFullName
    case x: nodes.Identifier      => x.typeFullName
    case x: nodes.Literal         => x.typeFullName
    case x: nodes.MethodParameterIn => x.typeFullName
    case x: nodes.MethodRef       => x.typeFullName
    case x: nodes.TypeRef         => x.typeFullName
    case x: nodes.Block           => x.typeFullName
    case _                        => ""
  }

  /** The simple name at the end of a dotted or nested type name. */
  def simple(t: String): String = t.split("[.$]").filter(_.nonEmpty).lastOption.getOrElse(t)

  /** `a.b.C.m:sig(...)` to `a.b.C`. Derived rather than looked up, because it has to agree exactly
    * with the fullName of the TypeDecl this method is emitted under. */
  def ownerOf(fullName: String): String = {
    val head = fullName.indexOf(':') match {
      case -1 => fullName
      case i  => fullName.substring(0, i)
    }
    head.lastIndexOf('.') match {
      case -1 => ""
      case j  => head.substring(0, j)
    }
  }

  // The first string literal in an annotation's source text: `@Path("/loans")` gives `/loans`. Read
  // off `code` rather than walking parameterAssign nodes - fewer API surfaces to be wrong about, and
  // annotation values in this codebase are literals.
  val LITERAL = """"([^"]*)"""".r
  def anns(xs: List[String], codes: List[String]): String =
    xs.zip(codes)
      .map { case (name, code) =>
        val value = LITERAL.findFirstMatchIn(code).map(_.group(1))
        "{\"name\":" + q(name) + value.map(v => ",\"value\":" + q(v)).getOrElse("") + "}"
      }
      .mkString("[", ",", "]")

  val methods = cpg.method.filterNot(_.isExternal).filter(_.filename.nonEmpty).l
  val emitted = scala.collection.mutable.HashSet[Long]()

  val KEEP = Set(
    "CALL", "IDENTIFIER", "LITERAL", "METHOD_PARAMETER_IN", "RETURN",
    "FIELD_IDENTIFIER", "CONTROL_STRUCTURE", "TYPE_REF", "METHOD_REF", "UNKNOWN",
  )

  val nodeJson = new StringBuilder
  val methodJson = new StringBuilder

  for (m <- methods) {
    val file = m.filename
    val fullName = m.fullName

    val params = m.parameter.l.map { p =>
      "{" + List(
        "\"id\":" + q(p.id.toString),
        "\"name\":" + q(p.name),
        "\"index\":" + p.index,
        "\"line\":" + p.lineNumber.getOrElse(-1),
        "\"type\":" + q(p.typeFullName),
      ).mkString(",") + "}"
    }
    val returns = m.ast.isReturn.l.map(r => q(r.id.toString))
    val annotations = m.annotation.l
    if (methodJson.nonEmpty) methodJson ++= ","
    methodJson ++= "{" + List(
      "\"fullName\":" + q(fullName),
      "\"name\":" + q(m.name),
      "\"typeName\":" + q(simple(ownerOf(fullName))),
      "\"typeFullName\":" + q(ownerOf(fullName)),
      "\"file\":" + q(file),
      "\"line\":" + m.lineNumber.getOrElse(-1),
      "\"annotations\":" + anns(annotations.map(_.name), annotations.map(_.code)),
      "\"params\":" + params.mkString("[", ",", "]"),
      "\"returns\":" + returns.mkString("[", ",", "]"),
    ).mkString(",") + "}"

    for (n <- m.ast.l if KEEP.contains(n.label) && n.lineNumber.isDefined) {
      emitted += n.id
      val fields = scala.collection.mutable.ListBuffer(
        "\"id\":" + q(n.id.toString),
        "\"label\":" + q(n.label),
        "\"code\":" + q(n.code),
        "\"file\":" + q(file),
        "\"line\":" + n.lineNumber.getOrElse(-1),
        "\"method\":" + q(fullName),
      )

      n match {
        case c: nodes.Call =>
          fields += "\"name\":" + q(c.name)
          fields += "\"methodFullName\":" + q(c.methodFullName)

          // Argument 0 is the receiver. Emitted as a dense array so that `args[i]` is the argument
          // Joern numbered `i`: a static call has no receiver, and without the empty slot every
          // parameter of every static callee would be fed the wrong argument.
          val recv = c.receiver.l.headOption.map(_.id.toString)
          val positional = c.argument.l.filter(_.argumentIndex >= 0).map(a => (a.argumentIndex, a.id.toString))
          val top = (0 :: positional.map(_._1)).max
          val dense = (0 to top).toList.map { i =>
            if (i == 0) recv.orElse(positional.find(_._1 == 0).map(_._2)).getOrElse("")
            else positional.find(_._1 == i).map(_._2).getOrElse("")
          }
          fields += "\"args\":" + dense.map(q).mkString("[", ",", "]")

          val receiverType = c.receiver.l.headOption
            .map(typeOf)
            .filter(t => t.nonEmpty && t != "ANY")
            .orElse(Some(ownerOf(c.methodFullName)).filter(_.nonEmpty))
          receiverType.foreach(t => fields += "\"receiverType\":" + q(t))

          if (c.name == "<operator>.fieldAccess") {
            val field = c.astChildren.isFieldIdentifier.l.headOption
            val base = c.astChildren.l.filterNot(_.isInstanceOf[nodes.FieldIdentifier]).headOption
            for (f <- field) {
              val owner = base.map(b => simple(typeOf(b))).filter(_.nonEmpty).getOrElse("?")
              fields += "\"member\":" + q(owner + "." + f.canonicalName)
            }
          }

        case r: nodes.Return =>
          fields += "\"args\":" + r.astChildren.l.map(x => q(x.id.toString)).mkString("[", ",", "]")

        case _ => ()
      }

      val t = typeOf(n)
      if (t.nonEmpty) fields += "\"typeFullName\":" + q(t)

      if (nodeJson.nonEmpty) nodeJson ++= ","
      nodeJson ++= fields.mkString("{", ",", "}")
    }
  }

  val typeJson = cpg.typeDecl
    .filterNot(_.isExternal)
    .filter(_.filename.nonEmpty)
    .l
    .map { t =>
      val annotations = t.annotation.l
      val members = t.member.l.map { mem =>
        val memberAnns = mem.astChildren.isAnnotation.l
        "{" + List(
          "\"name\":" + q(mem.name),
          "\"type\":" + q(mem.typeFullName),
          "\"annotations\":" + anns(memberAnns.map(_.name), memberAnns.map(_.code)),
        ).mkString(",") + "}"
      }
      "{" + List(
        "\"fullName\":" + q(t.fullName),
        "\"name\":" + q(t.name),
        "\"file\":" + q(t.filename),
        "\"annotations\":" + anns(annotations.map(_.name), annotations.map(_.code)),
        "\"members\":" + members.mkString("[", ",", "]"),
      ).mkString(",") + "}"
    }
    .mkString("[", ",", "]")

  val edgeJson = new StringBuilder
  def edges(kind: String, label: String): Unit =
    for {
      m <- methods
      e <- m.ast.outE(label).l
      if emitted.contains(e.src.asInstanceOf[nodes.StoredNode].id)
      if emitted.contains(e.dst.asInstanceOf[nodes.StoredNode].id)
    } {
      if (edgeJson.nonEmpty) edgeJson ++= ","
      edgeJson ++= "{\"kind\":" + q(kind) +
        ",\"from\":" + q(e.src.asInstanceOf[nodes.StoredNode].id.toString) +
        ",\"to\":" + q(e.dst.asInstanceOf[nodes.StoredNode].id.toString) + "}"
    }
  edges("rd", "REACHING_DEF")
  edges("cdg", "CDG")

  val w = new PrintWriter(out, "UTF-8")
  w.print("{\"version\":1,\"commit\":" + q(commit))
  w.print(",\"types\":" + typeJson)
  w.print(",\"methods\":[" + methodJson.toString + "]")
  w.print(",\"nodes\":[" + nodeJson.toString + "]")
  w.print(",\"edges\":[" + edgeJson.toString + "]}")
  w.close()

  println(s"wrote $out: ${methods.size} methods, ${emitted.size} nodes")
}
