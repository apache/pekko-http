/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package org.apache.pekko

import sbt.Keys._
import sbt._
import org.mdedetrich.apache.sonatype.SonatypeApachePlugin
import org.mdedetrich.apache.sonatype.SonatypeApachePlugin.autoImport._

/**
 * Copies LICENSE and NOTICE files into jar META-INF dir
 */
object AddMetaInfLicenseFiles extends AutoPlugin {

  override lazy val projectSettings = Seq(
    apacheSonatypeDisclaimerFile := Some((LocalRootProject / baseDirectory).value / "DISCLAIMER"))

  override def trigger = allRequirements

  override def requires = SonatypeApachePlugin

}
