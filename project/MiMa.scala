/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2020 Lightbend Inc. <https://www.lightbend.com>
 */

import sbt._
import sbt.Keys._
import com.typesafe.tools.mima.core.ProblemFilter
import com.typesafe.tools.mima.plugin.MimaPlugin
import com.typesafe.tools.mima.plugin.MimaPlugin.autoImport._

import scala.util.Try

object MiMa extends AutoPlugin {
  private val latestPatchOf10 = 0

  override def requires = MimaPlugin
  override def trigger = allRequirements

  val checkMimaFilterDirectories =
    taskKey[Unit]("Check that the mima directories are correct compared to latest version")

  override val projectSettings = Seq(
    mimaPreviousArtifacts := {
      val versions = Set("1.0.0")

      versions.collect { case version =>
        organization.value %% name.value % version
      }
    },
    mimaBackwardIssueFilters := {
      val filters = mimaBackwardIssueFilters.value
      val allVersions = (mimaPreviousArtifacts.value.map(_.revision) ++ filters.keys).toSeq

      /**
       * Collect filters for all versions of a fork and add them as filters for the latest version of the fork.
       * Otherwise, new versions in the fork that are listed above will reintroduce issues that were already filtered
       * out before. We basically rebase this release line on top of the fork from the view of Mima.
       */
      def forkFilter(fork: String): Option[(String, Seq[ProblemFilter])] = {
        val forkVersions = filters.keys.filter(_.startsWith(fork)).toSeq
        val collectedFilterOption = forkVersions.map(filters).reduceOption(_ ++ _)
        collectedFilterOption.map(latestForkVersion(fork, allVersions) -> _)
      }

      Map.empty
    },
    checkMimaFilterDirectories := checkFilterDirectories(baseDirectory.value))

  def checkFilterDirectories(moduleRoot: File): Unit = {
    val nextVersionFilterDir =
      moduleRoot / "src" / "main" / "mima-filters" / s"1.0.${latestPatchOf10 + 1}.backwards.excludes"
    if (nextVersionFilterDir.exists()) {
      throw new IllegalArgumentException(s"Incorrect mima filter directory exists: '$nextVersionFilterDir' " +
        s"should be with number from current release '${moduleRoot / "src" / "main" / "mima-filters" / s"1.0.$latestPatchOf10.backwards.excludes"}")
    }
  }

  def latestForkVersion(fork: String, allVersions: Seq[String]): String =
    allVersions
      .filter(_.startsWith(fork))
      .sorted(versionOrdering)
      .last

  // copied from https://github.com/lightbend/migration-manager/blob/e54f3914741b7f528da5507e515cc84db60abdd5/core/src/main/scala/com/typesafe/tools/mima/core/ProblemReporting.scala#L14-L19
  private lazy val versionOrdering = Ordering[(Int, Int, Int)].on { version: String =>
    val ModuleVersion = """(\d+)\.?(\d+)?\.?(.*)?""".r
    val ModuleVersion(epoch, major, minor) = version
    val toNumeric =
      (revision: String) => Try(revision.replace("x", Short.MaxValue.toString).filter(_.isDigit).toInt).getOrElse(0)
    (toNumeric(epoch), toNumeric(major), toNumeric(minor))
  }
}
