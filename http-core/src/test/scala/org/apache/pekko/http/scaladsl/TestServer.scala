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

package org.apache.pekko.http.scaladsl

import scala.concurrent.duration._
import scala.concurrent.Await

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.ActorSystem
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.model.AttributeKeys.webSocketUpgrade
import pekko.http.scaladsl.model.HttpMethods._
import pekko.http.scaladsl.model.ws._
import pekko.stream._
import pekko.stream.scaladsl.{ Flow, Source }

import com.typesafe.config.{ Config, ConfigFactory }

import scala.io.StdIn

object TestServer extends App {
  val testConf: Config = ConfigFactory.parseString("""
    pekko.loglevel = INFO
    pekko.log-dead-letters = off
    pekko.stream.materializer.debug.fuzzing-mode = off
    pekko.actor.serialize-creators = off
    pekko.actor.serialize-messages = off
    pekko.actor.default-dispatcher.throughput = 1000
    """)
  implicit val system: ActorSystem = ActorSystem("ServerTest", testConf)

  val settings = ActorMaterializerSettings(system)
    //    .withSyncProcessingLimit(Int.MaxValue)
    .withInputBuffer(128, 128)
  implicit val fm: ActorMaterializer = ActorMaterializer(settings)
  try {
    val binding = Http().newServerAt("localhost", 9001).bindSync {
      case req @ HttpRequest(GET, Uri.Path("/"), _, _, _) =>
        req.attribute(webSocketUpgrade) match {
          case Some(upgrade) => upgrade.handleMessages(echoWebSocketService) // needed for running the autobahn test suite
          case None          => index
        }
      case HttpRequest(GET, Uri.Path("/ping"), _, _, _)  => HttpResponse(entity = "PONG!")
      case HttpRequest(GET, Uri.Path("/crash"), _, _, _) => sys.error("BOOM!")
      case req @ HttpRequest(GET, Uri.Path("/ws-greeter"), _, _, _) =>
        req.attribute(webSocketUpgrade) match {
          case Some(upgrade) => upgrade.handleMessages(greeterWebSocketService)
          case None          => HttpResponse(400, entity = "Not a valid websocket request!")
        }
      case _: HttpRequest => HttpResponse(404, entity = "Unknown resource!")
    }

    Await.result(binding, 1.second) // throws if binding fails
    println("Server online at http://localhost:9001")
    println("Press RETURN to stop...")
    StdIn.readLine()
  } finally {
    system.terminate()
  }

  ////////////// helpers //////////////

  lazy val index = HttpResponse(
    entity = HttpEntity(
      ContentTypes.`text/html(UTF-8)`,
      """|<html>
         | <body>
         |    <h1>Say hello to <i>pekko-http-core</i>!</h1>
         |    <p>Defined resources:</p>
         |    <ul>
         |      <li><a href="/ping">/ping</a></li>
         |      <li><a href="/crash">/crash</a></li>
         |    </ul>
         |  </body>
         |</html>""".stripMargin))

  def echoWebSocketService: Flow[Message, Message, NotUsed] =
    Flow[Message] // just let message flow directly to the output

  def greeterWebSocketService: Flow[Message, Message, NotUsed] =
    Flow[Message]
      .collect {
        case TextMessage.Strict(name) => TextMessage(s"Hello '$name'")
        case tm: TextMessage          => TextMessage(Source.single("Hello ") ++ tm.textStream)
        // ignore binary messages
      }
}
