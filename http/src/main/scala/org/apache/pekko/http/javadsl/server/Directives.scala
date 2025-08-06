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

package org.apache.pekko.http.javadsl.server

import java.util.function.{ BiFunction, Function, Supplier }

import org.apache.pekko
import pekko.annotation.ApiMayChange
import pekko.http.javadsl.server.directives.FramedEntityStreamingDirectives
import scala.annotation.nowarn

import scala.annotation.varargs

abstract class AllDirectives extends FramedEntityStreamingDirectives

/**
 * Collects all default directives into one class for simple importing of static functions.
 *
 * See [[pekko.http.scaladsl.server.Directives]] for ScalaDSL equivalent of this class.
 */
object Directives extends AllDirectives {

  // These are repeated here since sometimes (?) the Scala compiler won't actually generate java-compatible
  // signatures for varargs methods, making them show up as Seq<Object> instead of T... in Java.

  // deprecated in https://github.com/apache/pekko-http/commit/fffca8de9f6a6237788021a2e3fa49a95463a8c6
  /**
   * @deprecated since Akka HTTP 10.2.0, use RouteDirectives.concat instead.
   */
  @deprecated("Use the RouteDirectives.concat method instead.", since = "Akka HTTP 10.2.0")
  @varargs
  @nowarn("msg=route in class RouteDirectives is deprecated")
  override def route(alternatives: Route*): Route =
    super.route(alternatives: _*)

  @varargs override def getFromBrowseableDirectories(directories: String*): Route =
    super.getFromBrowseableDirectories(directories: _*)

  /**
   * Composes two 0-argument directives which share the same inner Route. This is equivalent to
   * `first(inner).orElse(second(inner))`
   * Usage example:
   * `anyOf(this::get, this::post, () -> complete("hi")))`
   * @param first the first 0-argument directive
   * @param second the second 0-argument directive
   * @param inner the inner route Producer
   * @return the resulting route
   */
  @ApiMayChange
  def anyOf(first: Function[Supplier[Route], Route], second: Function[Supplier[Route], Route], inner: Supplier[Route])
      : Route = {
    first.apply(inner).orElse(second.apply(inner))
  }

  /**
   * Composes two 1-argument directives (directives whose inner route takes a parameter) which share the same inner route.
   * This is the same as `first(inner).orElse(second(inner))`.
   * This can be used also for directives that take an additional parameter if the directive is partially applied, ie. parameter
   * Usage example:
   * `anyOf(bindParameter(this::path, "bar"), bindParameter(this::path, "baz"), () -> complete("bar or baz"))`
   * @param first the first 1-argument directive
   * @param second the second 1-argument directive
   * @param inner the inner route that takes one argument
   * @tparam A the type of the parameter the directives extract and the inner route takes
   * @return the resulting route
   */
  @ApiMayChange
  def anyOf[A](first: Function[Function[A, Route], Route], second: Function[Function[A, Route], Route],
      inner: Function[A, Route]): Route = {
    first.apply(inner).orElse(second.apply(inner))
  }

  /**
   * Nests two 0-argument directives together
   * Usage example:
   * `allOf(bindParameter(this::pathPrefix, "alice"), bindParameter(this::path, "bob"), () -> complete("Charlie!"))`
   * @param first the 0-argument directive (outer one)
   * @param second the 0-argument directive (inner one)
   * @param inner the inner route function
   * @return the resulting route
   */
  @ApiMayChange
  def allOf(first: Function[Supplier[Route], Route], second: Function[Supplier[Route], Route], inner: Supplier[Route])
      : Route = {
    first.apply(new Supplier[Route] {
      override def get(): Route =
        second.apply(inner)
    })
  }

  /**
   * Nests two 1-argument directives together
   * Usage example:
   * `allOf(this::extractScheme, this::extractMethod, (scheme, method) -> complete("You did a " + method.name() + " using " + scheme))`
   * @param first the first 1-argument directive (outer one)
   * @param second the second 1-argument directive (inner one)
   * @param inner the inner route function that takes 2 different parameters
   * @tparam A the type extracted from the first directive
   * @tparam B the type extracted from the second directive
   * @return the resulting route
   */
  @ApiMayChange
  def allOf[A, B](first: Function[Function[A, Route], Route], second: Function[Function[B, Route], Route],
      inner: BiFunction[A, B, Route]): Route = {
    first.apply(new Function[A, Route] {
      override def apply(a: A): Route =
        second.apply(new Function[B, Route] {
          override def apply(b: B): Route =
            inner.apply(a, b)
        })
    })
  }

  /**
   * Nests one 0-argument and 1-argument directives together
   * Usage example:
   * `allOf(bindParameter(this::pathPrefix, "guess"), this::extractMethod, method -> complete("You did a " + method.name()))`
   * @param first the 0-argument directive (outer one)
   * @param second the 1-argument directive (inner one)
   * @param inner the inner route function that takes 1 parameter
   * @tparam A the type extracted from the second directive
   * @return the resulting route
   */
  @ApiMayChange
  def allOf[A](first: Function[Supplier[Route], Route], second: Function[Function[A, Route], Route],
      inner: Function[A, Route]): Route = {
    first.apply(new Supplier[Route] {
      override def get(): Route =
        second.apply(new Function[A, Route] {
          override def apply(a: A): Route =
            inner.apply(a)
        })
    })
  }
}
