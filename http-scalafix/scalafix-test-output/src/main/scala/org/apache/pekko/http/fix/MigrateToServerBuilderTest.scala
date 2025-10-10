/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package org.apache.pekko.http.fix

import org.apache.pekko
import pekko.actor._
import pekko.event.LoggingAdapter
import pekko.http.scaladsl._
import pekko.http.scaladsl.server._
import pekko.http.scaladsl.settings.ServerSettings
import pekko.http.scaladsl.model._
import pekko.stream.Materializer
import pekko.stream.scaladsl.{ Flow, Sink }

import scala.concurrent.Future

object MigrateToServerBuilderTest {
  // Add code that needs fixing here.
  implicit def actorSystem: ActorSystem = ???
  def customMaterializer: Materializer = ???
  def http: HttpExt = ???
  implicit def log: LoggingAdapter = ???
  def settings: ServerSettings = ???
  def httpContext: HttpConnectionContext = ???
  def context: HttpsConnectionContext = ???
  def handler: HttpRequest => Future[HttpResponse] = ???
  def syncHandler: HttpRequest => HttpResponse = ???
  def flow: Flow[HttpRequest, HttpResponse, Any] = ???
  def route: Route = ???
  trait ServiceRoutes {
    def route: Route = ???
  }
  def service: ServiceRoutes = ???

  Http().newServerAt("127.0.0.1", 8080).logTo(log).bind(handler)
  Http().newServerAt("127.0.0.1", 8080).logTo(log).bind(handler)
  Http().newServerAt("127.0.0.1", 0).withSettings(settings).bind(handler)
  Http().newServerAt(interface = "localhost", port = 8443).enableHttps(context).bind(handler)
  Http().newServerAt(interface = "localhost", port = 8080).bind(handler)
  Http().newServerAt(interface = "localhost", port = 8080).bind(handler)
  Http().newServerAt("127.0.0.1", 8080).bindFlow(flow)
  Http().newServerAt("127.0.0.1", 8080).bind(route)
  Http().newServerAt("127.0.0.1", 8080).bind(service.route)
  Http().newServerAt("127.0.0.1", 0).logTo(log).bindSync(syncHandler)

  Http().newServerAt("127.0.0.1", 0).withSettings(settings).connectionSource().runWith(Sink.ignore)

  // format: OFF
  Http().newServerAt("127.0.0.1", 8080).withMaterializer(customMaterializer).bind(route)
  Http().newServerAt("127.0.0.1", 8080).withMaterializer(customMaterializer).bind(handler)
  Http().newServerAt("127.0.0.1", 8080).withMaterializer(customMaterializer).bindSync(syncHandler)
  Http() // needed to appease formatter
  // format: ON

  http.newServerAt("127.0.0.1", 8080).bind(route)
  http.newServerAt("127.0.0.1", 8080).bind(handler)
  http.newServerAt("127.0.0.1", 8080).bindSync(syncHandler)

  Http(actorSystem).newServerAt("127.0.0.1", 8080).bind(route)
  Http(actorSystem).newServerAt("127.0.0.1", 8080).bind(handler)
  Http(actorSystem).newServerAt("127.0.0.1", 8080).bindSync(syncHandler)
}
