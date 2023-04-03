/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka

import scala.language.postfixOps
import sbt.{ Def, _ }
import Keys._
import xerial.sbt.Sonatype.autoImport.sonatypeProfileName
import org.mdedetrich.apache.sonatype.SonatypeApachePlugin

/**
 * For projects that are not published.
 */
object NoPublish extends AutoPlugin {
  override def requires = plugins.JvmPlugin

  override def projectSettings = Seq(
    publish / skip := true,
    publishArtifact := false,
    publish := {},
    publishLocal := {})
}

object Publish extends AutoPlugin {
  override def requires = SonatypeApachePlugin
  override def trigger = AllRequirements

  override lazy val projectSettings = Seq(
    startYear := Some(2022),
    developers := List(
      Developer(
        "pekko-http-contributors",
        "Apache Pekko HTTP Contributors",
        "dev@pekko.apache.org",
        url("https://github.com/apache/incubator-pekko-http/graphs/contributors"))))
}
