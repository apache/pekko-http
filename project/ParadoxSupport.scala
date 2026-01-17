/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

import java.io.FileNotFoundException

import sbt._
import Keys._
import com.lightbend.paradox.markdown._
import com.lightbend.paradox.sbt.ParadoxPlugin.autoImport._
import org.apache.pekko.PekkoParadoxPlugin.autoImport._
import org.pegdown.Printer
import org.pegdown.ast.{ DirectiveNode, HtmlBlockNode, VerbatimNode, Visitor }
import sbtlicensereport.SbtLicenseReport.autoImportImpl.dumpLicenseReportAggregate

import scala.collection.JavaConverters._
import scala.io.{ Codec, Source }

object ParadoxSupport {
  val paradoxWithCustomDirectives = Seq(
    paradoxDirectives +=
      ((context: Writer.Context) =>
        new SignatureDirective(context.location.tree.label, context.properties, context)),
    pekkoParadoxGithub := Some("https://github.com/apache/pekko-http"),
    Global / pekkoParadoxIncubatorNotice := None,
    Compile / paradoxMarkdownToHtml / sourceGenerators += Def.taskDyn {
      val targetFile = (Compile / paradox / sourceManaged).value / "license-report.md"

      (LocalRootProject / dumpLicenseReportAggregate).map { dir =>
        IO.copy(List(dir / "pekko-http-root-licenses.md" -> targetFile)).toList
      }
    }.taskValue)

  class SignatureDirective(
      page: Page, variables: Map[String, String], ctx: Writer.Context) extends LeafBlockDirective("signature") {
    def render(node: DirectiveNode, visitor: Visitor, printer: Printer): Unit =
      try {
        val labels = node.attributes.values("identifier").asScala.map(_.toLowerCase())
        val source = node.source match {
          case direct: DirectiveNode.Source.Direct => direct.value
          case _                                   => sys.error("Source references are not supported")
        }
        val file = SourceDirective.resolveFile("signature", source, page.file, variables)

        // The following are stupid approximation's to match a signature/s
        val TypeSignature = """\s*(type (\w+)(?=[:(\[]).*)(\s+\=.*)""".r
        // println(s"Looking for signature regex '$Signature'")
        val lines = Source.fromFile(file)(Codec.UTF8).getLines.toList

        val types = lines.collect {
          case line @ TypeSignature(signature, l, definition) if labels contains l.toLowerCase() =>
            // println(s"Found label '$l' with sig '$full' in line $line")
            signature + definition
        }

        val Signature = """.*((def|val) (\w+)(?=[:(\[]).*)""".r

        val other = lines.mkString.split("=").collect {
          case line @ Signature(signature, kind, l) if labels contains l.toLowerCase() =>
            // println(s"Found label '$l' with sig '$full' in line $line")
            signature
              .replaceAll("""\s{2,}""", " ") // Due to formatting with new lines its possible to have excessive whitespace
        }

        val text = (types ++ other).mkString("\n")

        if (text.trim.isEmpty) {
          ctx.error(
            s"Did not find any signatures with one of those names [${labels.mkString(", ")}]", page, node)

          new HtmlBlockNode(s"""<div style="color: red;">[Broken signature inclusion [${labels.mkString(
              ", ")}] to [${node.source}]</div>""").accept(visitor)
        } else {
          val lang = Option(node.attributes.value("type")).getOrElse(Snippet.language(file))
          new VerbatimNode(text, lang).accept(visitor)
        }
      } catch {
        case e: FileNotFoundException =>
          ctx.error(s"Unknown snippet [${e.getMessage}]", node)
      }
  }
}
