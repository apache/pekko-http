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

package org.apache.pekko.http.javadsl

import scala.jdk.CollectionConverters._
import scala.util.control.NoStackTrace

import io.github.classgraph.{ ClassGraph, MethodInfo => ClassGraphMethodInfo }
import org.apache.pekko

import org.scalatest.BeforeAndAfterAll
import org.scalatest.exceptions.TestPendingException
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

object ClassGraphMembers {
  final case class MethodInfo(name: String, isStatic: Boolean, correspondsTo: Option[String])

  private val correspondsToAnnotation = "org.apache.pekko.http.javadsl.server.directives.CorrespondsTo"

  private lazy val scanResult =
    new ClassGraph()
      .acceptPackages("org.apache.pekko.http")
      .enableClassInfo()
      .enableMethodInfo()
      .enableAnnotationInfo()
      .ignoreClassVisibility()
      .ignoreMethodVisibility()
      .scan()

  private lazy val publicMethodsCache =
    collection.concurrent.TrieMap.empty[String, Vector[MethodInfo]]
  private lazy val declaredMethodsCache =
    collection.concurrent.TrieMap.empty[String, Vector[MethodInfo]]
  private lazy val interfacesCache =
    collection.concurrent.TrieMap.empty[String, Vector[String]]
  private lazy val superclassesCache =
    collection.concurrent.TrieMap.empty[String, Vector[String]]

  def publicMethods(clazz: Class[?]): Array[MethodInfo] =
    publicMethodsCache
      .getOrElseUpdate(clazz.getName,
        classInfo(clazz.getName).getMethodInfo.asScala.filter(_.isPublic).map(toMethod).toVector)
      .toArray

  def declaredMethods(className: String): Vector[MethodInfo] =
    declaredMethodsCache.getOrElseUpdate(
      className,
      classInfo(className).getDeclaredMethodInfo.asScala.map(toMethod).toVector)

  def allInterfaces(clazz: Class[?]): Vector[String] =
    interfacesCache.getOrElseUpdate(clazz.getName,
      classInfo(clazz.getName).getInterfaces.directOnly.asScala.map(_.getName).toVector)

  def superclasses(clazz: Class[?]): Vector[String] =
    superclassesCache.getOrElseUpdate(
      clazz.getName,
      (classInfo(clazz.getName) +: classInfo(clazz.getName).getSuperclasses.asScala.toVector)
        .map(_.getName)
        .filterNot(_ == "java.lang.Object"))

  private def classInfo(className: String) =
    Option(scanResult.getClassInfo(className))
      .getOrElse(throw new IllegalArgumentException(s"Could not find class metadata for [$className]"))

  def close(): Unit =
    scanResult.close()

  private def toMethod(method: ClassGraphMethodInfo): MethodInfo =
    MethodInfo(
      method.getName,
      method.isStatic,
      Option(method.getAnnotationInfo(correspondsToAnnotation))
        .flatMap(annotation => Option(annotation.getParameterValues.getValue("value")).map(_.toString)))
}

class DirectivesConsistencySpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {
  import ClassGraphMembers.MethodInfo

  val scalaDirectivesClazz = classOf[pekko.http.scaladsl.server.Directives]
  val javaDirectivesClazz = classOf[pekko.http.javadsl.server.AllDirectives]

  val ignore =
    Set("equals", "hashCode", "notify", "notifyAll", "wait", "toString", "getClass") ++
    Set("productArity", "canEqual", "productPrefix", "copy", "productIterator", "productElement",
      "concat", "route") ++ // TODO this fails on jenkins but not locally, no idea why, disabling to get Java DSL in
    // param extractions in ScalaDSL
    Set("not", "DoubleNumber", "HexIntNumber", "HexLongNumber", "IntNumber", "JavaUUID", "LongNumber",
      "Neutral", "PathEnd", "Remaining", "Segment", "Segments", "Slash", "RemainingPath") // TODO do we cover these?

  def prepareDirectivesList(in: Array[MethodInfo]): List[MethodInfo] = {
    in.toSet
      .toList
      .foldLeft[List[MethodInfo]](Nil) {
        (l, s) =>
          {
            val test = l.find { _.name.toLowerCase == s.name.toLowerCase }
            if (test.isEmpty) s :: l else l
          }
      }
      .sortBy(_.name)
      .iterator
      .filterNot(_.isStatic)
      .filterNot(m => ignore(m.name))
      .filterNot(m => m.name.contains("$"))
      .filterNot(m => m.name.startsWith("_"))
      .toList
  }

  val scalaDirectives = {
    prepareDirectivesList(ClassGraphMembers.publicMethods(scalaDirectivesClazz))
  }
  val javaDirectives = {
    prepareDirectivesList(ClassGraphMembers.publicMethods(javaDirectivesClazz))
  }

  val correspondingScalaMethods = {
    val javaToScalaMappings =
      for {
        // using Scala annotations - Java annotations were magically not present in certain places...
        d <- javaDirectives
        correspondent <- d.correspondsTo
      } yield d.name -> correspondent

    Map(javaToScalaMappings.toList: _*)
  }

  val correspondingJavaMethods = Map() ++ correspondingScalaMethods.map(_.swap)

  /** Left(@CorrespondsTo(...) or Right(normal name) */
  def correspondingScalaMethodName(m: MethodInfo): Either[String, String] =
    correspondingScalaMethods.get(m.name) match {
      case Some(correspondent) => Left(correspondent)
      case _                   => Right(m.name)
    }

  /** Left(@CorrespondsTo(...) or Right(normal name) */
  def correspondingJavaMethodName(m: MethodInfo): Either[String, String] =
    correspondingJavaMethods.get(m.name) match {
      case Some(correspondent) => Left(correspondent)
      case _                   => Right(m.name)
    }

  val allowMissing: Map[Class[?], Set[String]] = Map(
    scalaDirectivesClazz -> Set(
      "route", "request",
      "completeOK", // solved by raw complete() in Scala
      "defaultDirectoryRenderer", "defaultContentTypeResolver" // solved by implicits in Scala
    ),
    javaDirectivesClazz -> Set(
      "as",
      "instanceOf",
      "pass",

      // TODO PENDING ->
      "extractRequestContext", "nothingMatcher", "separateOnSlashes",
      "textract", "tprovide", "withExecutionContext", "withRequestTimeoutResponse",
      "withSettings",
      "provide", "withMaterializer", "recoverRejectionsWith",
      "mapSettings", "mapRequestContext", "mapInnerRoute", "mapRouteResultFuture",
      "mapRouteResultWith",
      "mapRouteResult", "handleWith",
      "mapRouteResultWithPF", "mapRouteResultPF",
      "route", "request",
      "completeOrRecoverWith", "completeWith",
      // TODO <- END OF PENDING
      "parameters", "formFields", // since we can't do magnet-style "arbitrary arity"

      "authenticateOAuth2PF", "authenticateOAuth2PFAsync",
      "authenticateBasicPF", "authenticateBasicPFAsync"))

  def assertHasMethod(c: Class[?], name: String, alternativeName: String): Unit = {
    // include class name to get better error message
    if (!allowMissing.getOrElse(c, Set.empty).exists(n => n == name || n == alternativeName)) {
      val methods =
        ClassGraphMembers.publicMethods(c).collect { case m if !ignore(m.name) => c.getName + "." + m.name }

      def originClazz = {
        // look in the "opposite" class
        // traversal is different in scala/java - in scala its traits, so we need to look at interfaces
        // in hava we have a huge inheritance chain so we unfold it
        c match {
          case `javaDirectivesClazz` =>
            (for {
              i <- ClassGraphMembers.allInterfaces(scalaDirectivesClazz)
              m <- ClassGraphMembers.declaredMethods(i)
              if m.name == name || m.name == alternativeName
            } yield i).headOption
              .getOrElse(throw new Exception(s"Unable to locate method [$name] on source class $scalaDirectivesClazz"))

          case `scalaDirectivesClazz` =>
            (for {
              i <- ClassGraphMembers.superclasses(javaDirectivesClazz)
              m <- ClassGraphMembers.declaredMethods(i)
              if m.name == name || m.name == alternativeName
            } yield i).headOption
              .getOrElse(throw new Exception(s"Unable to locate method [$name] on source class $javaDirectivesClazz"))
        }
      }

      if (methods.contains(c.getName + "." + name) && name == alternativeName) ()
      else if (methods.contains(c.getName + "." + alternativeName)) ()
      else throw new AssertionError(s"Method [$originClazz#$name] was not defined on class: ${c.getName}")
        with NoStackTrace
    } else {
      // allowed missing - we mark as pending, perhaps we'll want that method eventually
      throw new TestPendingException
    }
  }

  "DSL Stats" should {
    info("Scala Directives: ~" + scalaDirectives.map(_.name).filterNot(ignore).size)
    info("Java Directives: ~" + javaDirectives.map(_.name).filterNot(ignore).size)
  }

  "Directive aliases" should {
    info("Aliases: ")
    correspondingScalaMethods.foreach { case (k, v) => info(s"  $k => $v") }
  }

  "Consistency scaladsl -> javadsl" should {
    for {
      m <- scalaDirectives
      name = m.name
      targetName = correspondingJavaMethodName(m) match {
        case Left(l)  => l
        case Right(r) => r
      }
      text = if (name == targetName) name else s"$name (alias: $targetName)"
    } s"""define Scala directive [$text] for JavaDSL too""" in {
      assertHasMethod(javaDirectivesClazz, name, targetName)
    }
  }

  "Consistency javadsl -> scaladsl" should {
    for {
      m <- javaDirectives
      name = m.name
      targetName = correspondingScalaMethodName(m) match {
        case Left(l)  => l
        case Right(r) => r
      }
      text = if (name == targetName) name else s"$name (alias for: $targetName)"
    } s"""define Java directive [$text] for ScalaDSL too""" in {
      assertHasMethod(scalaDirectivesClazz, name, targetName)
    }
  }

  override protected def afterAll(): Unit =
    try ClassGraphMembers.close()
    finally super.afterAll()
}
