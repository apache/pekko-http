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

import scala.language.postfixOps
import sbt._
import Keys._
import org.mdedetrich.apache.sonatype.ApacheSonatypePlugin
import sbtdynver.DynVerPlugin
import sbtdynver.DynVerPlugin.autoImport.dynverSonatypeSnapshots

/**
 * For projects that are not published.
 */
object NoPublish extends AutoPlugin {
  override def requires = plugins.JvmPlugin && DynVerPlugin

  override def projectSettings = Seq(
    publish / skip := true,
    publishArtifact := false,
    publish := {},
    publishLocal := {})
}

object Publish extends AutoPlugin {
  override def requires = ApacheSonatypePlugin
  override def trigger = AllRequirements

  override lazy val projectSettings = Seq(
    startYear := Some(2022),
    developers := List(
      Developer(
        "pekko-http-contributors",
        "Apache Pekko HTTP Contributors",
        "dev@pekko.apache.org",
        url("https://github.com/apache/pekko-http/graphs/contributors"))))

  override lazy val buildSettings = Seq(
    dynverSonatypeSnapshots := true)
}
