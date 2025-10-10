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

package org.apache.pekko.http.javadsl.testkit

import scala.annotation.varargs
import scala.concurrent.{ ExecutionContextExecutor, Future }
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.http.impl.util.JavaMapping.Implicits.AddAsScala
import pekko.http.javadsl.model.HttpRequest
import pekko.http.javadsl.model.headers.Host
import pekko.http.javadsl.server.{ AllDirectives, Directives, Route, RouteResult, RouteResults }
import pekko.http.scaladsl
import pekko.http.scaladsl.server
import pekko.http.scaladsl.server.{ ExceptionHandler, Route => ScalaRoute }
import pekko.http.scaladsl.settings.ParserSettings
import pekko.http.scaladsl.settings.RoutingSettings
import pekko.http.scaladsl.settings.ServerSettings
import pekko.http.scaladsl.util.FastFuture
import pekko.stream.Materializer
import pekko.testkit.TestDuration

/**
 * A base class to create route tests for testing libraries. An implementation needs to provide
 * code to provide and shutdown an [[pekko.actor.ActorSystem]], [[pekko.stream.Materializer]], and [[scala.concurrent.ExecutionContextExecutor]].
 *
 * See `JUnitRouteTest` for an example of a concrete implementation.
 */
abstract class RouteTest extends AllDirectives with WSTestRequestBuilding {
  implicit def system: ActorSystem
  implicit def materializer: Materializer
  implicit def executionContext: ExecutionContextExecutor = system.dispatcher

  protected def defaultAwaitDuration = 3.seconds
  protected def awaitDuration: FiniteDuration = defaultAwaitDuration.dilated

  protected def defaultHostInfo: DefaultHostInfo = DefaultHostInfo(Host.create("example.com"), false)

  def runRoute(route: Route, request: HttpRequest): TestRouteResult =
    runRoute(route, request, defaultHostInfo)

  def runRoute(route: Route, request: HttpRequest, defaultHostInfo: DefaultHostInfo): TestRouteResult =
    runScalaRoute(route.seal().delegate, request, defaultHostInfo)

  def runRouteClientServer(route: Route, request: HttpRequest): TestRouteResult = {
    val response =
      scaladsl.testkit.RouteTest.runRouteClientServer(request.asScala, route.delegate, ServerSettings(system))
    createTestRouteResultAsync(request, response.map(scalaResponse => RouteResults.complete(scalaResponse)))
  }

  def runRouteUnSealed(route: Route, request: HttpRequest): TestRouteResult =
    runRouteUnSealed(route, request, defaultHostInfo)

  def runRouteUnSealed(route: Route, request: HttpRequest, defaultHostInfo: DefaultHostInfo): TestRouteResult =
    runScalaRoute(route.delegate, request, defaultHostInfo)

  private def runScalaRoute(
      scalaRoute: ScalaRoute, request: HttpRequest, defaultHostInfo: DefaultHostInfo): TestRouteResult = {
    val effectiveRequest = request.asScala
      .withEffectiveUri(
        securedConnection = defaultHostInfo.isSecuredConnection(),
        defaultHostHeader = defaultHostInfo.getHost().asScala)

    // this will give us the default exception handler
    val sealedExceptionHandler = ExceptionHandler.seal(null)

    val semiSealedRoute = // sealed for exceptions but not for rejections
      pekko.http.scaladsl.server.Directives.handleExceptions(sealedExceptionHandler)(scalaRoute)

    val result = semiSealedRoute(new server.RequestContextImpl(effectiveRequest, system.log, RoutingSettings(system),
      ParserSettings.forServer(system)))
    createTestRouteResultAsync(request, result)
  }

  /**
   * Wraps a list of route alternatives with testing support.
   */
  @varargs
  def testRoute(first: Route, others: Route*): TestRoute =
    new TestRoute {
      val underlying: Route = Directives.concat(first, others: _*)

      def run(request: HttpRequest): TestRouteResult = runRoute(underlying, request)
      def runWithRejections(request: HttpRequest): TestRouteResult = runRouteUnSealed(underlying, request)
      def runClientServer(request: HttpRequest): TestRouteResult = runRouteClientServer(underlying, request)
    }

  protected def createTestRouteResult(request: HttpRequest, result: RouteResult): TestRouteResult =
    createTestRouteResultAsync(request, FastFuture.successful(result))
  protected def createTestRouteResultAsync(request: HttpRequest, result: Future[RouteResult]): TestRouteResult
}
