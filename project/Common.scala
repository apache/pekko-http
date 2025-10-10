/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2017-2020 Lightbend Inc. <https://www.lightbend.com>
 */

import sbt._
import Keys._
import com.lightbend.paradox.projectinfo.ParadoxProjectInfoPluginKeys._
import com.typesafe.tools.mima.plugin.MimaKeys._

object Common extends AutoPlugin {
  override def trigger: PluginTrigger = allRequirements
  override lazy val projectSettings = Seq(
    projectInfoVersion := (if (isSnapshot.value) "snapshot" else version.value),
    scalacOptions ++= Seq(
      "-deprecation",
      "-encoding", "UTF-8", // yes, this is 2 args
      "-unchecked",
      "-Ywarn-dead-code",
      "-Wconf:msg=reached max recursion depth:s",
      "-Wconf:msg=Prefer the Scala annotation over Java's `@Deprecated`:s",
      "-release:" + javacTarget),
    scalacOptions ++= onlyOnScala2(Seq(
      "-Xlint",
      // Exhaustivity checking is only useful for simple sealed hierarchies and matches without filters.
      // In all other cases, the warning is non-actionable: you get spurious warnings that need to be suppressed
      // verbosely. So, opt out of those in general.
      "-Wconf:cat=other-match-analysis&msg=match may not be exhaustive:s")).value,
    scalacOptions ++= onlyOnScala3(Seq("-Wconf:cat=deprecation:s")).value,
    Test / scalacOptions ++= Seq(
      // we sometimes use pattern definitions in tests just for the side-effects and don't care about the extra member
      "-Wconf:msg=Pattern definition introduces Unit-valued member:s"),
    javacOptions ++=
      Seq("-encoding", "UTF-8", "--release", javacTarget),
    mimaReportSignatureProblems := true,
    Global / parallelExecution := sys.props.getOrElse("pekko.http.parallelExecution", "true") != "false")

  val javacTarget: String = "17"
  def onlyOnScala2[T](values: Seq[T]): Def.Initialize[Seq[T]] = Def.setting {
    if (scalaVersion.value.startsWith("3")) Seq.empty[T] else values
  }
  def onlyOnScala3[T](values: Seq[T]): Def.Initialize[Seq[T]] = Def.setting {
    if (scalaVersion.value.startsWith("3")) values else Seq.empty[T]
  }

  def scalaMinorVersion: Def.Initialize[Long] = Def.setting { CrossVersion.partialVersion(scalaVersion.value).get._2 }

}
