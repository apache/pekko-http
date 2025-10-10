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

import scala.collection.immutable
import scala.concurrent.Future
import scala.jdk.CollectionConverters._

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.ClassicActorSystemProvider
import pekko.http.javadsl
import pekko.http.scaladsl.model.{ HttpRequest, HttpResponse }
import pekko.stream.scaladsl.Flow

/**
 * The result of handling a request.
 *
 * As a user you typically don't create RouteResult instances directly.
 * Instead, use the methods on the [[RequestContext]] to achieve the desired effect.
 */
sealed trait RouteResult extends javadsl.server.RouteResult

object RouteResult {
  final case class Complete(response: HttpResponse) extends javadsl.server.Complete with RouteResult {
    override def getResponse = response
  }
  final case class Rejected(rejections: immutable.Seq[Rejection]) extends javadsl.server.Rejected with RouteResult {
    override def getRejections = rejections.map(r => r: javadsl.server.Rejection).asJava
  }

  /**
   * Turns a `Route` into a server flow.
   *
   * This implicit conversion is defined here because `Route` is an alias for
   * `RequestContext => Future[RouteResult]`, and the fact that `RouteResult`
   * is in that type means this implicit conversion come into scope whereever
   * a `Route` is given but a `Flow` is expected.
   */
  implicit def routeToFlow(route: Route)(
      implicit system: ClassicActorSystemProvider): Flow[HttpRequest, HttpResponse, NotUsed] =
    Route.toFlow(route)

  /**
   * Turns a `Route` into a server function.
   *
   * This implicit conversion is defined here because `Route` is an alias for
   * `RequestContext => Future[RouteResult]`, and the fact that `RouteResult`
   * is in that type means this implicit conversion come into scope whereever
   * a `Route` is given but a `Function[HttpRequest, Future[HttpResponse]` is expected.
   */
  implicit def routeToFunction(route: Route)(
      implicit system: ClassicActorSystemProvider): HttpRequest => Future[HttpResponse] =
    Route.toFunction(route)
}
