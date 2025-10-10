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

package org.apache.pekko.http.impl.engine.ws

import org.apache.pekko
import pekko.NotUsed

import scala.concurrent.Await
import scala.concurrent.duration._
import pekko.actor.ActorSystem
import pekko.http.scaladsl.Http
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.model.AttributeKeys.webSocketUpgrade
import pekko.http.scaladsl.model.HttpMethods._
import pekko.http.scaladsl.model.ws.Message
import pekko.stream.scaladsl.Flow

import scala.io.StdIn

object WSServerAutobahnTest extends App {
  implicit val system: ActorSystem = ActorSystem("WSServerTest")

  val host = System.getProperty("pekko.ws-host", "127.0.0.1")
  val port = System.getProperty("pekko.ws-port", "9001").toInt
  val mode = System.getProperty("pekko.ws-mode", "read") // read or sleep

  try {
    val binding = Http().newServerAt(host, port).bindSync {
      case req @ HttpRequest(GET, Uri.Path("/"), _, _, _) if req.attribute(webSocketUpgrade).isDefined =>
        req.attribute(webSocketUpgrade) match {
          case Some(upgrade) => upgrade.handleMessages(echoWebSocketService) // needed for running the autobahn test suite
          case None          => HttpResponse(400, entity = "Not a valid websocket request!")
        }
      case _: HttpRequest => HttpResponse(404, entity = "Unknown resource!")
    }

    Await.result(binding, 3.second) // throws if binding fails
    println(s"Server online at http://$host:$port")
    mode match {
      case "sleep" => while (true) Thread.sleep(1.minute.toMillis)
      case "read"  => StdIn.readLine("Press RETURN to stop...")
      case _       => throw new Exception("pekko.ws-mode MUST be sleep or read.")
    }
  } finally {
    system.terminate()
  }

  def echoWebSocketService: Flow[Message, Message, NotUsed] =
    Flow[Message] // just let message flow directly to the output
}
