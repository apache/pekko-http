/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

import sbt._
import sbt.Keys._
import _root_.scalafix.sbt.ScalafixPlugin

object ScalaFixExtraRulesPlugin extends AutoPlugin with ScalafixSupport {
  override lazy val trigger: PluginTrigger = allRequirements

  override lazy val requires: Plugins = ScalafixPlugin

  override lazy val projectSettings = Seq(ignore(Compile), ignore(Test)) ++ Seq(Compile, Test).map { config =>
    config / scalacOptions := {
      val options = (config / scalacOptions).value
      if (semanticdbEnabled.value) {
        // OrganizeImports needs unused-import diagnostics, but must be able to repair them before warnings fail the build.
        options.filterNot(option =>
          Set("-Xfatal-warnings", "-Werror").contains(option) ||
          (option.startsWith("-Wconf:") && option.toLowerCase.contains("unused import"))) :+
        (if (scalaVersion.value.startsWith("2.12.")) "-Ywarn-unused:imports" else "-Wunused:imports")
      } else options
    }
  }
}
