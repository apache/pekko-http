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

package org.apache.pekko.http.javadsl.server.directives

import java.util.concurrent.CompletionStage

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.{ ActorSystem, ClassicActorSystemProvider }
import pekko.annotation.InternalApi
import pekko.http.javadsl.model.HttpRequest
import pekko.http.javadsl.model.HttpResponse
import pekko.http.impl.util.JavaMapping.Implicits._
import pekko.http.javadsl.server.{ ExceptionHandler, RejectionHandler, Route }
import pekko.http.scaladsl
import pekko.http.scaladsl.server.RouteConcatenation._
import pekko.japi.function.Function
import pekko.stream.{ javadsl, Materializer }
import pekko.stream.scaladsl.Flow

import scala.concurrent.Future

/** INTERNAL API */
@InternalApi
final class RouteAdapter(val delegate: pekko.http.scaladsl.server.Route) extends Route {

  override def flow(system: ActorSystem, materializer: Materializer): javadsl.Flow[HttpRequest, HttpResponse, NotUsed] =
    scalaFlow(system, materializer).asJava

  override def handler(system: ClassicActorSystemProvider): Function[HttpRequest, CompletionStage[HttpResponse]] = {
    import scala.jdk.FutureConverters._
    val scalaFunction = scaladsl.server.Route.toFunction(delegate)(system)
    request => (scalaFunction(request.asScala): Future[HttpResponse]).asJava
  }

  private def scalaFlow(system: ActorSystem, materializer: Materializer): Flow[HttpRequest, HttpResponse, NotUsed] = {
    implicit val s: ActorSystem = system
    Flow[HttpRequest].map(_.asScala).via(delegate).map(_.asJava)
  }

  override def orElse(alternative: Route): Route =
    alternative match {
      case adapt: RouteAdapter =>
        RouteAdapter(delegate ~ adapt.delegate)
    }

  override def seal(): Route = RouteAdapter(scaladsl.server.Route.seal(delegate))

  override def seal(rejectionHandler: RejectionHandler, exceptionHandler: ExceptionHandler): Route =
    RouteAdapter(scaladsl.server.Route.seal(delegate)(
      rejectionHandler = rejectionHandler.asScala,
      exceptionHandler = exceptionHandler.asScala))

  override def toString = s"org.apache.pekko.http.javadsl.server.Route($delegate)"
}

object RouteAdapter {
  def apply(delegate: pekko.http.scaladsl.server.Route): RouteAdapter = new RouteAdapter(delegate)

  /** Java DSL: Adapt an existing ScalaDSL Route as an Java DSL Route */
  def asJava(delegate: pekko.http.scaladsl.server.Route): Route = new RouteAdapter(delegate)
}
