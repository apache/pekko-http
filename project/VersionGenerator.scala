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
import sbt.Keys._
import com.github.pjfanning.pekkobuild.PekkoDependency

/**
 * Generate version.conf and pekko/Version.scala files based on the version setting.
 */
object VersionGenerator {

  def versionSettings: Seq[Setting[_]] = inConfig(Compile)(Seq(
    resourceGenerators += generateVersion(resourceManaged, _ / "pekko-http-version.conf",
      """|pekko.http.version = "%s"
         |"""),
    sourceGenerators += generateVersion(sourceManaged, _ / "org" / "apache" / "pekko" / "http" / "Version.scala",
      """|package org.apache.pekko.http
         |
         |import com.typesafe.config.Config
         |
         |object Version {
         |  val current: String = "%s"
         |  val supportedPekkoVersion = "%s"
         |  def check(config: Config): Unit = {
         |    val configVersion = config.getString("pekko.http.version")
         |    if (configVersion != current) {
         |      throw new org.apache.pekko.ConfigurationException(
         |        "Pekko JAR version [" + current + "] does not match the provided " +
         |          "config version [" + configVersion + "]")
         |    }
         |  }
         |}
         |""")))

  def generateVersion(dir: SettingKey[File], locate: File => File, template: String) = Def.task[Seq[File]] {
    val file = locate(dir.value)
    val content = template.stripMargin.format(version.value, PekkoDependency.pekkoVersion)
    if (!file.exists || IO.read(file) != content) IO.write(file, content)
    Seq(file)
  }

}
