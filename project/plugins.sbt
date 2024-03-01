/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

resolvers += Classpaths.sbtPluginReleases
resolvers += Classpaths.typesafeReleases

addSbtPlugin("com.github.sbt" % "sbt-multi-jvm" % "0.6.0")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "1.1.3")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.2")
addSbtPlugin("com.github.sbt" % "sbt-dynver" % "5.0.1")
addSbtPlugin("com.github.sbt" % "sbt-unidoc" % "0.5.0")
addSbtPlugin("com.thoughtworks.sbt-api-mappings" % "sbt-api-mappings" % "3.0.2")
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.4.7")
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.10.0-RC1") // for advanced PR validation features
addSbtPlugin("com.github.sbt" % "sbt-boilerplate" % "0.7.0")
addSbtPlugin("com.lightbend.sbt" % "sbt-bill-of-materials" % "1.0.2")
addSbtPlugin("com.github.sbt" % "sbt-license-report" % "1.6.1")

// We have to deliberately use older versions of sbt-paradox because current Pekko sbt build
// only loads on JDK 1.8 so we need to bring in older versions of parboiled which support JDK 1.8
resolvers += Resolver.ApacheMavenSnapshotsRepo
addSbtPlugin("org.apache.pekko" % "pekko-sbt-paradox" % "1.0.0+37-5e25566b-SNAPSHOT")
dependencyOverrides += "org.parboiled" % "parboiled-java" % "1.3.1"
dependencyOverrides += "com.lightbend.paradox" %% "paradox" % "0.9.2"

addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.10.0")
addSbtPlugin("net.bzzt" % "sbt-reproducible-builds" % "0.32")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.12.0")
addSbtPlugin("com.github.sbt" % "sbt-dynver" % "5.0.1")
addSbtPlugin("com.github.pjfanning" % "sbt-source-dist" % "0.1.12")
addSbtPlugin("com.github.pjfanning" % "sbt-pekko-build" % "0.3.3")
addSbtPlugin("net.virtual-void" % "sbt-hackers-digest" % "0.1.2")
addSbtPlugin("com.lightbend.sbt" % "sbt-java-formatter" % "0.7.0")

// used in ValidatePullRequest to check github PR comments whether to build all subprojects
libraryDependencies += "org.kohsuke" % "github-api" % "1.319"

// used for @unidoc directive
libraryDependencies += "io.github.lukehutch" % "fast-classpath-scanner" % "3.1.15"
