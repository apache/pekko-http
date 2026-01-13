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

package org.apache.pekko.http.scaladsl.server

import scala.concurrent.{ ExecutionContextExecutor, Future }

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.ClassicActorSystemProvider
import pekko.http.scaladsl.model.{ HttpRequest, HttpResponse }
import pekko.http.scaladsl.server.directives.BasicDirectives
import pekko.http.scaladsl.settings.{ ParserSettings, RoutingSettings }
import pekko.http.scaladsl.util.FastFuture._
import pekko.stream.{ Materializer, SystemMaterializer }
import pekko.stream.scaladsl.Flow

object Route {

  /**
   * Helper for constructing a Route from a function literal.
   */
  def apply(f: Route): Route = f

  /**
   * "Seals" a route by wrapping it with default exception handling and rejection conversion.
   *
   * A sealed route has these properties:
   *  - The result of the route will always be a complete response, i.e. the result of the future is a
   *    ``Success(RouteResult.Complete(response))``, never a failed future and never a rejected route. These
   *    will be already be handled using the implicitly given [[RejectionHandler]] and [[ExceptionHandler]] (or
   *    the default handlers if none are given or can be found implicitly).
   *  - Consequently, no route alternatives will be tried that were combined with this route
   *    using the ``~`` on routes or the [[Directive.|]] operator on directives.
   */
  def seal(route: Route)(
      implicit
      routingSettings: RoutingSettings = null,
      rejectionHandler: RejectionHandler = RejectionHandler.default,
      exceptionHandler: ExceptionHandler = null): Route = {
    import directives.ExecutionDirectives._
    // optimized as this is the root handler for all pekko-http applications
    BasicDirectives.extractSettings { theSettings =>
      val effectiveRoutingSettings = if (routingSettings eq null) theSettings else routingSettings

      {
        implicit val routingSettings: RoutingSettings = effectiveRoutingSettings
        (handleExceptions(ExceptionHandler.seal(exceptionHandler)) & handleRejections(rejectionHandler.seal))
          .tapply(_ => route) // execute above directives eagerly, avoiding useless laziness of Directive.addByNameNullaryApply
      }
    }
  }

  /**
   * Turns a `Route` into a server flow.
   *
   * This conversion is also implicitly available wherever a `Route` is used through [[RouteResult#routeToFlow]].
   */
  def toFlow(route: Route)(implicit system: ClassicActorSystemProvider): Flow[HttpRequest, HttpResponse, NotUsed] =
    Flow[HttpRequest].mapAsync(1)(toFunction(route))

  def toFunction(route: Route)(implicit system: ClassicActorSystemProvider): HttpRequest => Future[HttpResponse] = {
    val routingLog = RoutingLog(system.classicSystem.log)
    val routingSettings = RoutingSettings(system)
    val parserSettings = ParserSettings.forServer

    // This is essentially the same as `seal(route)` but a bit more efficient because we know we have no inherited settings:
    val sealedRoute = {
      import directives.ExecutionDirectives._
      (handleExceptions(ExceptionHandler.default(routingSettings)) & handleRejections(RejectionHandler.default))
        .tapply(_ => route)
    }

    createAsyncHandler(sealedRoute, routingLog, routingSettings, parserSettings)(system.classicSystem.dispatcher,
      SystemMaterializer(system).materializer)
  }

  private def createAsyncHandler(sealedRoute: Route, routingLog: RoutingLog, routingSettings: RoutingSettings,
      parserSettings: ParserSettings)(
      implicit ec: ExecutionContextExecutor, mat: Materializer): HttpRequest => Future[HttpResponse] = {
    request =>
      sealedRoute(new RequestContextImpl(request, routingLog.requestLog(request), routingSettings, parserSettings)).fast
        .map {
          case RouteResult.Complete(response) => response
          case RouteResult.Rejected(rejected) =>
            throw new IllegalStateException(s"Unhandled rejections '$rejected', unsealed RejectionHandler?!")
        }
  }
}
