/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs

import org.apache.pekko.annotation.ApiMayChange
import io.github.classgraph.{ ClassGraph, MethodInfo }
import org.scalatest.Assertion

import scala.io.Source
import scala.jdk.CollectionConverters._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ApiMayChangeDocCheckerSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private val apiMayChangeAnnotation = classOf[ApiMayChange].getName
  private val httpPackagePrefix = "org.apache.pekko.http."

  private lazy val scanResult =
    new ClassGraph()
      .acceptPackages("org.apache.pekko.http")
      .enableClassInfo()
      .enableMethodInfo()
      .enableAnnotationInfo()
      .ignoreClassVisibility()
      .ignoreMethodVisibility()
      .scan()

  def prettifyName(className: String): String = {
    className.replaceAll("\\$minus", "-").split("\\$")(0)
  }

  private def isHttpClass(className: String): Boolean =
    className.startsWith(httpPackagePrefix)

  // As Specs, Directives and HttpApp inherit get all directives methods, we skip those as they are not really bringing any extra info
  def removeClassesToIgnore(method: MethodInfo): Boolean = {
    Seq("Spec", ".Directives", ".HttpApp").exists(method.getClassName.contains)
  }

  def collectMissing(docPage: Seq[String])(set: Set[String], name: String): Set[String] = {
    if (docPage.exists(line => line.contains(name)))
      set
    else
      set + name
  }

  def checkNoMissingCases(missing: Set[String], typeOfUsage: String): Assertion = {
    if (missing.isEmpty) {
      succeed
    } else {
      fail(
        s"Please add the following missing $typeOfUsage annotated with @ApiMayChange to docs/src/main/paradox/compatibility-guidelines.md:\n${missing.map(
            miss => s"* $miss").mkString("\n")}")
    }
  }

  "compatibility-guidelines.md doc page" should {
    val source = Source.fromFile("docs/src/main/paradox/compatibility-guidelines.md")
    try {
      val docPage = source.getLines().toList
      "contain all ApiMayChange references in classes" in {
        val classes = scanResult.getClassesWithAnnotation(apiMayChangeAnnotation).asScala.filter(classInfo =>
          isHttpClass(classInfo.getName))
        val missing = classes
          .map(classInfo => prettifyName(classInfo.getName))
          .foldLeft(Set.empty[String])(collectMissing(docPage))
        checkNoMissingCases(missing, "Types")
      }
      "contain all ApiMayChange references in methods" in {
        val methods =
          scanResult.getClassesWithMethodAnnotation(apiMayChangeAnnotation).asScala.flatMap { classInfo =>
            classInfo.getDeclaredMethodInfo.asScala.filter(method =>
              isHttpClass(method.getClassName) && method.hasAnnotation(apiMayChangeAnnotation))
          }
        val missing = methods
          .filterNot(removeClassesToIgnore)
          .map(method => prettifyName(method.getClassName) + "#" + method.getName)
          .foldLeft(Set.empty[String])(collectMissing(docPage))
        checkNoMissingCases(missing, "Methods")
      }
    } finally source.close()

  }

  override protected def afterAll(): Unit =
    try scanResult.close()
    finally super.afterAll()
}
