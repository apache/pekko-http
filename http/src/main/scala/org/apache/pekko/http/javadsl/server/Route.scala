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

import java.util.concurrent.CompletionStage

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.ActorSystem
import pekko.actor.ClassicActorSystemProvider
import pekko.annotation.{ DoNotInherit, InternalApi }
import pekko.http.javadsl.HandlerProvider
import pekko.http.javadsl.model.HttpRequest
import pekko.http.javadsl.model.HttpResponse
import pekko.http.scaladsl
import pekko.http.scaladsl.server
import pekko.japi.function.Function
import pekko.stream.Materializer
import pekko.stream.SystemMaterializer
import pekko.stream.javadsl.Flow

/**
 * In the Java DSL, a Route can only consist of combinations of the built-in directives. A Route can not be
 * instantiated directly.
 *
 * However, the built-in directives may be combined methods like:
 *
 * <pre>
 * Route myDirective(String test, Supplier<Route> inner) {
 *   return
 *     path("fixed", () ->
 *       path(test),
 *         inner
 *       )
 *     );
 * }
 * </pre>
 *
 * The above example will invoke [inner] whenever the path "fixed/{test}" is matched, where "{test}"
 * is the actual String that was given as method argument.
 */
@DoNotInherit
trait Route extends HandlerProvider {

  /** Converts to the Scala DSL form of an Route. */
  def asScala: server.Route = delegate

  /** INTERNAL API */
  @InternalApi
  private[http] def delegate: scaladsl.server.Route

  def flow(system: ActorSystem, materializer: Materializer): Flow[HttpRequest, HttpResponse, NotUsed]

  def flow(system: ClassicActorSystemProvider): Flow[HttpRequest, HttpResponse, NotUsed] =
    flow(system.classicSystem, SystemMaterializer(system).materializer)

  def function(system: ClassicActorSystemProvider): Function[HttpRequest, CompletionStage[HttpResponse]] =
    handler(system)
  def handler(system: ClassicActorSystemProvider): Function[HttpRequest, CompletionStage[HttpResponse]]

  /**
   * Seals a route by wrapping it with default exception handling and rejection conversion.
   *
   * A sealed route has these properties:
   *  - The result of the route will always be a complete response, i.e. the result of the future is a
   *    `Success(RouteResult.Complete(response))`, never a failed future and never a rejected route. These
   *    will be already be handled using the default [[RejectionHandler]] and [[ExceptionHandler]].
   *  - Consequently, no route alternatives will be tried that were combined with this route.
   */
  def seal(): Route

  /**
   * Seals a route by wrapping it with explicit exception handling and rejection conversion.
   *
   * A sealed route has these properties:
   *  - The result of the route will always be a complete response, i.e. the result of the future is a
   *    `Success(RouteResult.Complete(response))`, never a failed future and never a rejected route. These
   *    will be already be handled using the given [[RejectionHandler]] and [[ExceptionHandler]].
   *  - Consequently, no route alternatives will be tried that were combined with this route.
   */
  def seal(
      rejectionHandler: RejectionHandler,
      exceptionHandler: ExceptionHandler): Route

  def orElse(alternative: Route): Route
}
