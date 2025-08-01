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
import scala.language.implicitConversions

object Dependencies {
  import DependencyHelpers._

  val jacksonDatabindVersion = "2.19.2"
  val jacksonXmlVersion = jacksonDatabindVersion
  val junitVersion = "4.13.2"
  val h2specVersion = "2.6.0"
  val h2specName = s"h2spec_${DependencyHelpers.osName}_amd64"
  val h2specExe = "h2spec" + DependencyHelpers.exeIfWindows
  val h2specArtifactExtension = if (h2specExe.endsWith("exe")) "zip" else "tar.gz"
  val h2specUrl =
    s"https://github.com/summerwind/h2spec/releases/download/v$h2specVersion/$h2specName.$h2specArtifactExtension"

  val scalaTestVersion = "3.2.19"
  val scalaCheckVersion = "1.18.0"

  val scalafixVersion = _root_.scalafix.sbt.BuildInfo.scalafixVersion // grab from plugin

  val scala212Version = "2.12.20"
  val scala213Version = "2.13.16"
  val scala3Version = "3.3.6"
  val allScalaVersions = Seq(scala213Version, scala212Version, scala3Version)

  val Versions = Seq(
    crossScalaVersions := allScalaVersions,
    scalaVersion := allScalaVersions.head)

  object Provided {
    val jsr305 = "com.google.code.findbugs" % "jsr305" % "3.0.2" % "provided"

    val scalaReflect = ScalaVersionDependentModuleID.fromPF {
      case v if v.startsWith("2.") => "org.scala-lang" % "scala-reflect" % v % "provided"
    }
  }

  object Compile {
    val scalaXml = "org.scala-lang.modules" %% "scala-xml" % "2.4.0"

    // For pekko-http spray-json support
    val sprayJson = "io.spray" %% "spray-json" % "1.3.6"

    // For pekko-http-jackson support
    val jacksonDatabind = "com.fasterxml.jackson.core" % "jackson-databind" % jacksonDatabindVersion

    // For pekko-http-testkit-java
    val junit = "junit" % "junit" % junitVersion

    val caffeine = "com.github.ben-manes.caffeine" % "caffeine" % "2.9.3"

    val scalafix = "ch.epfl.scala" %% "scalafix-core" % Dependencies.scalafixVersion

    val parboiled = "org.parboiled" %% "parboiled" % "2.5.1"

    object Docs {
      val sprayJson = Compile.sprayJson % "test"
      val gson = "com.google.code.gson" % "gson" % "2.13.1" % "test"
      val jacksonXml = "com.fasterxml.jackson.dataformat" % "jackson-dataformat-xml" % jacksonXmlVersion % "test"
      val reflections = "org.reflections" % "reflections" % "0.10.2" % "test"
    }

    object Test {
      val sprayJson = Compile.sprayJson % "test"
      val junit = Compile.junit % "test"
      val specs2 = "org.specs2" %% "specs2-core" % "4.21.0"
      val munit = "org.scalameta" %% "munit" % "1.1.1"

      val scalacheck = "org.scalacheck" %% "scalacheck" % scalaCheckVersion % "test"
      val junitIntf = "com.github.sbt" % "junit-interface" % "0.13.3" % "test"

      val scalatest = "org.scalatest" %% "scalatest" % scalaTestVersion % "test"
      val scalatestplusScalacheck = "org.scalatestplus" %% "scalacheck-1-18" % (scalaTestVersion + ".0") % "test"
      val scalatestplusJUnit = "org.scalatestplus" %% "junit-4-13" % (scalaTestVersion + ".0") % "test"

      // HTTP/2

      val h2spec = ("io.github.summerwind" % h2specName % h2specVersion % "test").from(h2specUrl)
    }
  }

  import Compile._

  lazy val l = libraryDependencies

  lazy val parsing = Seq(
    DependencyHelpers.versionDependentDeps(
      Dependencies.Provided.scalaReflect))

  lazy val httpCore = l ++= Seq(
    parboiled,
    Test.sprayJson, // for WS Autobahn test metadata
    Test.scalatest, Test.scalatestplusScalacheck, Test.scalatestplusJUnit, Test.junit)

  lazy val httpCaching = l ++= Seq(
    caffeine,
    Provided.jsr305,
    Test.scalatest)

  lazy val httpCors = l ++= Seq(Test.scalatest)

  lazy val http = Seq()

  lazy val http2Tests = l ++= Seq(Test.h2spec)

  lazy val httpTestkit = Seq(
    versionDependentDeps(
      Test.specs2 % "provided; test"),
    l ++= Seq(
      Test.junit, Test.junitIntf, Compile.junit % "provided",
      Test.scalatest.withConfigurations(Some("provided; test"))))

  lazy val httpTestkitMunit =
    l ++= Seq(Test.munit % "provided; test")

  lazy val httpTests = l ++= Seq(Test.junit, Test.scalatest, Test.junitIntf)

  lazy val httpXml = Seq(
    versionDependentDeps(scalaXml),
    libraryDependencies += Test.scalatest)

  lazy val httpSprayJson = Seq(
    versionDependentDeps(sprayJson),
    libraryDependencies += Test.scalatest)

  lazy val httpJackson = l ++= Seq(jacksonDatabind, Test.scalatestplusJUnit, Test.junit, Test.junitIntf)

  lazy val docs = l ++= Seq(Docs.sprayJson, Docs.gson, Docs.jacksonXml, Docs.reflections)
}

object DependencyHelpers {
  case class ScalaVersionDependentModuleID(modules: String => Seq[ModuleID]) {
    def %(config: String): ScalaVersionDependentModuleID =
      ScalaVersionDependentModuleID(version => modules(version).map(_ % config))
  }
  object ScalaVersionDependentModuleID {
    implicit def liftConstantModule(mod: ModuleID): ScalaVersionDependentModuleID = versioned(_ => mod)

    def versioned(f: String => ModuleID): ScalaVersionDependentModuleID = ScalaVersionDependentModuleID(v => Seq(f(v)))
    def fromPF(f: PartialFunction[String, ModuleID]): ScalaVersionDependentModuleID =
      ScalaVersionDependentModuleID(version => if (f.isDefinedAt(version)) Seq(f(version)) else Nil)
  }

  /**
   * Use this as a dependency setting if the dependencies contain both static and Scala-version
   * dependent entries.
   */
  def versionDependentDeps(modules: ScalaVersionDependentModuleID*): Def.Setting[Seq[ModuleID]] =
    libraryDependencies ++= scalaVersion(version => modules.flatMap(m => m.modules(version))).value

  val ScalaVersion = """\d\.\d+\.\d+(?:-(?:M|RC)\d+)?""".r
  val nominalScalaVersion: String => String = {
    // matches:
    // 2.12.0-M1
    // 2.12.0-RC1
    // 2.12.0
    case version @ ScalaVersion() => version
    // transforms 2.12.0-custom-version to 2.12.0
    case version => version.takeWhile(_ != '-')
  }

  // OS name for Go binaries
  def osName: String = {
    val os = System.getProperty("os.name").toLowerCase()
    if (os.startsWith("mac")) "darwin"
    else if (os.startsWith("win")) "windows"
    else "linux"
  }

  def exeIfWindows: String = {
    if (osName.startsWith("win")) ".exe"
    else ""
  }

}
