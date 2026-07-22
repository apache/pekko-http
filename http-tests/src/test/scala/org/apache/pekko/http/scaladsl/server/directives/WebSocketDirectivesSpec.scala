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

package org.apache.pekko.http.scaladsl.server.directives

import java.util.function.Predicate

import scala.collection.immutable

import org.apache.pekko
import pekko.http.impl.engine.server.InternalCustomHeader
import pekko.http.scaladsl.model.AttributeKeys.webSocketUpgrade
import pekko.http.scaladsl.model.{ HttpRequest, HttpResponse, StatusCodes }
import pekko.http.scaladsl.model.headers.{ `Sec-WebSocket-Protocol`, Upgrade, UpgradeProtocol }
import pekko.http.scaladsl.model.ws._
import pekko.http.scaladsl.server.{
  ExpectedWebSocketRequestRejection,
  Route,
  RoutingSpec,
  UnsupportedWebSocketSubprotocolRejection
}
import pekko.http.scaladsl.testkit.WSProbe
import pekko.stream.{ FlowShape, Graph, OverflowStrategy }
import pekko.stream.scaladsl.{ Flow, Sink, Source }
import pekko.util.ByteString

class WebSocketDirectivesSpec extends RoutingSpec {
  "the handleWebSocketMessages directive" should {
    "handle websocket requests" in {
      val wsClient = WSProbe()

      WS("http://localhost/", wsClient.flow) ~> websocketRoute ~>
      check {
        isWebSocketUpgrade shouldEqual true
        wsClient.sendMessage("Peter")
        wsClient.expectMessage("Hello Peter!")

        wsClient.sendMessage(BinaryMessage(ByteString("abcdef")))
        // wsClient.expectNoMessage() // will be checked implicitly by next expectation

        wsClient.sendMessage("John")
        wsClient.expectMessage("Hello John!")

        wsClient.sendCompletion()
        wsClient.expectCompletion()
      }
    }
    "pass the outbound compression selector to the WebSocket upgrade" in {
      var selectedSubprotocol = Option.empty[Option[String]]
      var compressionDecision = Option.empty[Boolean]
      val request = recordingWebSocketRequest() { (subprotocol, shouldCompress) =>
        selectedSubprotocol = Some(subprotocol)
        compressionDecision = Some(shouldCompress(TextMessage("compress")))
      }

      request ~> handleWebSocketMessages(Flow[Message],
        {
          case TextMessage.Strict("compress") => true
          case _                              => false
        }) ~> check {
        isWebSocketUpgrade shouldEqual true
      }

      selectedSubprotocol shouldEqual Some(None)
      compressionDecision shouldEqual Some(true)
    }
    "pass the Java outbound compression predicate and selected subprotocol to the WebSocket upgrade" in {
      var selectedSubprotocol = Option.empty[Option[String]]
      var compressionDecision = Option.empty[Boolean]
      val request = recordingWebSocketRequest("echo") { (subprotocol, shouldCompress) =>
        selectedSubprotocol = Some(subprotocol)
        compressionDecision = Some(shouldCompress(TextMessage("compress")))
      }
      val shouldCompress = new Predicate[pekko.http.javadsl.model.ws.Message] {
        override def test(message: pekko.http.javadsl.model.ws.Message): Boolean = message.isText
      }
      val javaRoute = pekko.http.javadsl.server.Directives.handleWebSocketMessagesForProtocol(
        Flow[pekko.http.javadsl.model.ws.Message].asJava,
        "echo",
        shouldCompress)

      request ~> javaRoute.asScala ~> check {
        isWebSocketUpgrade shouldEqual true
        header[`Sec-WebSocket-Protocol`].get.protocols shouldEqual immutable.Seq("echo")
      }

      selectedSubprotocol shouldEqual Some(Some("echo"))
      compressionDecision shouldEqual Some(true)
    }
    "choose subprotocol from offered ones" in {
      val wsClient = WSProbe()

      WS("http://localhost/", wsClient.flow, List("other", "echo", "greeter")) ~> websocketMultipleProtocolRoute ~>
      check {
        expectWebSocketUpgradeWithProtocol { protocol =>
          protocol shouldEqual "echo"

          wsClient.sendMessage("Peter")
          wsClient.expectMessage("Peter")

          wsClient.sendMessage(BinaryMessage(ByteString("abcdef")))
          wsClient.expectMessage(ByteString("abcdef"))

          wsClient.sendMessage("John")
          wsClient.expectMessage("John")

          wsClient.sendCompletion()
          wsClient.expectCompletion()
        }
      }
    }
    "reject websocket requests if no subprotocol matches" in {
      WS("http://localhost/", Flow[Message], List("other")) ~> websocketMultipleProtocolRoute ~> check {
        rejections.collect {
          case UnsupportedWebSocketSubprotocolRejection(p) => p
        }.toSet shouldEqual Set("greeter", "echo")
      }

      WS("http://localhost/", Flow[Message], List("other")) ~> Route.seal(websocketMultipleProtocolRoute) ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[String] shouldEqual
        "None of the websocket subprotocols offered in the request are supported. Supported are 'echo','greeter'."
        header[`Sec-WebSocket-Protocol`].get.protocols.toSet shouldEqual Set("greeter", "echo")
      }
    }
    "reject non-websocket requests" in {
      Get("http://localhost/") ~> websocketRoute ~> check {
        rejection shouldEqual ExpectedWebSocketRequestRejection
      }

      Get("http://localhost/") ~> Route.seal(websocketRoute) ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[String] shouldEqual "Expected WebSocket Upgrade request"
      }
    }
  }

  def websocketRoute = handleWebSocketMessages(greeter)
  def websocketMultipleProtocolRoute =
    handleWebSocketMessagesForProtocol(echo, "echo") ~
    handleWebSocketMessagesForProtocol(greeter, "greeter")

  def greeter: Flow[Message, Message, Any] =
    Flow[Message].mapConcat {
      case tm: TextMessage   => TextMessage(Source.single("Hello ") ++ tm.textStream ++ Source.single("!")) :: Nil
      case bm: BinaryMessage => // ignore binary messages
        bm.dataStream.runWith(Sink.ignore)
        Nil
    }

  def echo: Flow[Message, Message, Any] =
    Flow[Message]
      .buffer(1, OverflowStrategy.backpressure) // needed because a noop flow hasn't any buffer that would start processing

  private def recordingWebSocketRequest(offeredProtocols: String*)(
      onHandle: (Option[String], Message => Boolean) => Unit): HttpRequest = {
    val upgrade =
      new InternalCustomHeader("UpgradeToWebSocketTestHeader") with WebSocketUpgrade {
        override def requestedProtocols: immutable.Seq[String] = offeredProtocols.toList

        override def handleMessages(
            handlerFlow: Graph[FlowShape[Message, Message], Any],
            subprotocol: Option[String]): HttpResponse =
          throw new AssertionError("The selective-compression overload was not called")

        override def handleMessages(
            handlerFlow: Graph[FlowShape[Message, Message], Any],
            subprotocol: Option[String],
            shouldCompress: Message => Boolean): HttpResponse = {
          onHandle(subprotocol, shouldCompress)
          HttpResponse(
            StatusCodes.SwitchingProtocols,
            headers =
              Upgrade(UpgradeProtocol("websocket") :: Nil) ::
              subprotocol.map(protocol => `Sec-WebSocket-Protocol`(protocol :: Nil)).toList)
        }
      }

    HttpRequest()
      .addAttribute(webSocketUpgrade, upgrade)
      .addHeader(upgrade)
  }
}
