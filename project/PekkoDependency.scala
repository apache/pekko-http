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

import scala.util.matching.Regex.Groups

object PekkoDependency {

  sealed trait Pekko {
    def version: String
    // The version to use in api/japi/docs links,
    // so 'x.y', 'x.y.z', 'current' or 'snapshot'
    def link: String
  }
  case class Artifact(version: String, isSnapshot: Boolean) extends Pekko {
    override def link = VersionNumber(version) match { case VersionNumber(Seq(x, y, _*), _, _) => s"$x.$y" }
  }
  object Artifact {
    def apply(version: String): Artifact = {
      val isSnap = version.endsWith("-SNAPSHOT")
      new Artifact(version, isSnap)
    }
  }
  case class Sources(uri: String, link: String = "current") extends Pekko {
    def version = link
  }

  def pekkoDependency(defaultVersion: String): Pekko = {
    Option(System.getProperty("pekko.sources")) match {
      case Some(pekkoSources) =>
        Sources(pekkoSources)
      case None =>
        Option(System.getProperty("pekko.http.build.pekko.version")) match {
          case Some("main")           => mainSnapshot
          case Some("snapshot-1.0.x") => snapshot10x
          case Some("default") | None => Artifact(defaultVersion)
          case Some(other)            => Artifact(other, true)
        }
    }
  }

  // Default version updated only when needed, https://pekko.apache.org/docs/pekko/current/project/downstream-upgrade-strategy.html
  val minimumExpectedPekkoVersion = "1.0.0"
  val default = pekkoDependency(defaultVersion = minimumExpectedPekkoVersion)
  def docs = default

  lazy val snapshot10x = Artifact(determineLatestSnapshot("1.0", false), true)
  lazy val mainSnapshot = Artifact(determineLatestSnapshot(), true)

  val pekkoVersion: String = default match {
    case Artifact(version, _) => version
    case Sources(uri, _)      => uri
  }

  implicit class RichProject(project: Project) {

    /** Adds either a source or a binary dependency, depending on whether the above settings are set */
    def addPekkoModuleDependency(module: String,
        config: String = "",
        pekko: Pekko = default): Project =
      pekko match {
        case Sources(sources, _) =>
          // as a little hacky side effect also disable aggregation of samples
          System.setProperty("pekko.build.aggregateSamples", "false")

          val moduleRef = ProjectRef(uri(sources), module)
          val withConfig: ClasspathDependency =
            if (config == "") moduleRef
            else moduleRef % config

          project.dependsOn(withConfig)
        case Artifact(pekkoVersion, pekkoSnapshot) =>
          project.settings(
            libraryDependencies += {
              if (config == "")
                "org.apache.pekko" %% module % pekkoVersion
              else
                "org.apache.pekko" %% module % pekkoVersion % config
            },
            resolvers ++= (if (pekkoSnapshot)
                             Seq(Resolver.ApacheMavenSnapshotsRepo)
                           else Nil))
      }
  }

  private def determineLatestSnapshot(prefix: String = "", allowRCVersions: Boolean = true): String = {
    import sbt.librarymanagement.Http.http
    import gigahorse.GigahorseSupport.url
    import scala.concurrent.Await
    import scala.concurrent.duration._

    lazy val snapshotRCVersionR = """href=".*/((\d+)\.(\d+)\.(\d+)(-(M|RC)(\d+))?\+(\d+)-[0-9a-f]+-SNAPSHOT)/"""".r
    lazy val snapshotVersionR = """href=".*/((\d+)\.(\d+)\.(\d+)\+(\d+)-[0-9a-f]+-SNAPSHOT)/"""".r

    // pekko-cluster-sharding-typed_2.13 seems to be the last nightly published by `pekko-publish-nightly` so if that's there then it's likely the rest also made it
    val body = Await.result(http.run(url(
        s"${Resolver.ApacheMavenSnapshotsRepo.root}org/apache/pekko/pekko-cluster-sharding-typed_2.13/")),
      10.seconds).bodyAsString

    val allVersions = if (allowRCVersions) {
      snapshotRCVersionR.findAllMatchIn(body)
        .map {
          case Groups(full, ep, maj, min, _, _, tagNumber, offset) =>
            (
              ep.toInt,
              maj.toInt,
              min.toInt,
              Option(tagNumber).map(_.toInt),
              offset.toInt) -> full
        }
    } else {
      snapshotVersionR.findAllMatchIn(body)
        .map {
          case Groups(full, ep, maj, min, offset) =>
            (
              ep.toInt,
              maj.toInt,
              min.toInt,
              None,
              offset.toInt) -> full
        }
    }
    allVersions
      .filter(_._2.startsWith(prefix))
      .toVector.sortBy(_._1)
      .last._2
  }
}
