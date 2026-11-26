/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.http.impl.engine.ws

import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.duration._

import org.apache.pekko
import pekko.event.NoLogging
import pekko.http.impl.engine.rendering.{
  DateHeaderRendering,
  HttpResponseRendererFactory,
  ResponseRenderingContext,
  ResponseRenderingOutput
}
import pekko.http.impl.engine.ws.FrameHandler.{ PeerClosed, UserHandlerCompleted, UserHandlerErredOut }
import pekko.http.impl.util.{ AddFutureAwaitResult, PekkoSpecWithMaterializer }
import pekko.http.scaladsl.model.headers.{
  Date,
  WebSocketExtension,
  `Sec-WebSocket-Extensions`,
  `Sec-WebSocket-Key`,
  `Sec-WebSocket-Protocol`
}
import pekko.http.scaladsl.model.ws.{ Message, TextMessage, WebSocketUpgrade }
import pekko.http.scaladsl.model.{ DateTime, HttpResponse, StatusCodes }
import pekko.http.scaladsl.settings.ServerSettings
import pekko.stream.Attributes
import pekko.stream.scaladsl.{ Flow, Keep, Sink, Source }
import pekko.stream.testkit.scaladsl.{ TestSink, TestSource }
import pekko.testkit._

class WebSocketCompletionCloseSpec extends PekkoSpecWithMaterializer {
  private def upgrade(offerCompression: Boolean = false): WebSocketUpgrade =
    Handshake.Server.websocketUpgrade(
      List(
        Handshake.UpgradeHeader,
        Handshake.ConnectionUpgradeHeader,
        Handshake.SecWebSocketVersionHeader,
        `Sec-WebSocket-Key`("dGhlIHNhbXBsZSBub25jZQ=="),
        `Sec-WebSocket-Protocol`(Seq("chat"))) ++
      (if (offerCompression) List(`Sec-WebSocket-Extensions`(Seq(WebSocketExtension("permessage-deflate")))) else Nil),
      hostHeaderPresent = true,
      ServerSettings(system).websocketSettings,
      NoLogging).get

  private def handler =
    Flow.fromSinkAndSource[Message, Message](Sink.ignore, Source.single(TextMessage("ready")))

  private def render(response: HttpResponse): ResponseRenderingOutput =
    Source.single(ResponseRenderingContext(response))
      .via(new HttpResponseRendererFactory(None, 256, NoLogging, DateHeaderRendering()).renderer)
      .runWith(Sink.head)
      .awaitResult(3.seconds.dilated)

  private def assertClose(response: HttpResponse, code: Int, reason: String): Unit = {
    response.status shouldEqual StatusCodes.SwitchingProtocols
    val nativeHandler = render(response) match {
      case ResponseRenderingOutput.SwitchToOtherProtocol(_, upgraded) => upgraded
      case _                                                          => fail("Expected a WebSocket upgrade")
    }
    val frames = Source.maybe[FrameEvent].via(WebSocket.framing.reversed.join(nativeHandler))
      .map {
        case frame: FrameStart => frame
        case _                 => fail("Expected a complete frame")
      }
      .take(2)
      .runWith(Sink.seq)
      .awaitResult(3.seconds.dilated)
    frames.map(_.header.opcode) shouldEqual Seq(Protocol.Opcode.Text, Protocol.Opcode.Close)
    frames.head.header.rsv1 shouldEqual false
    frames.head.data.utf8String shouldEqual "ready"
    FrameEventParser.parseCloseCode(frames.last.data) shouldEqual Some((code, reason))
  }

  "Server WebSocket completion close" should {
    "keep normal closure by default" in {
      assertClose(upgrade().handleMessages(handler), Protocol.CloseCodes.Regular, "")
    }

    "send the selected close after the final application message" in {
      val reason = "Red\u00e9marrage"
      assertClose(
        WebSocketUpgrade.withCompletionClose(
          upgrade().handleMessages(handler), Protocol.CloseCodes.ServiceRestart, reason),
        Protocol.CloseCodes.ServiceRestart,
        reason)
    }

    "preserve an explicit compression opt-out" in {
      val response = WebSocketUpgrade.withCompletionClose(
        upgrade(offerCompression = true).handleMessages(handler, None, compressionEnabled = false), 4200, "done")
      response.header[`Sec-WebSocket-Extensions`] shouldBe empty
      assertClose(response, 4200, "done")
    }

    "preserve the per-message compression predicate and the selected subprotocol" in {
      val seen = new AtomicInteger
      val response = WebSocketUpgrade.withCompletionClose(
        upgrade(offerCompression = true).handleMessages(handler, Some("chat"),
          (_: Message) => {
            seen.incrementAndGet()
            false
          }), 4200, "done")
      response.header[`Sec-WebSocket-Extensions`].get.extensions.map(_.name) shouldEqual Seq("permessage-deflate")
      response.header[`Sec-WebSocket-Protocol`].get.protocols shouldEqual Seq("chat")
      assertClose(response, 4200, "done")
      seen.get() shouldEqual 1
    }

    "leave a response that declines the upgrade unchanged on the wire" in {
      val ordinary = HttpResponse(StatusCodes.NotFound).addHeader(Date(DateTime(0L)))
      val decorated = WebSocketUpgrade.withCompletionClose(ordinary, 4200, "done")
      render(decorated) shouldEqual render(ordinary)
    }

    for (selectProtocol <- List(false, true)) {
      s"expose the same behavior to Java (subprotocol selected = $selectProtocol)" in {
        val response = WebSocketCompletionCloseJavaApi.upgrade(upgrade(), selectProtocol).asInstanceOf[HttpResponse]
        response.header[`Sec-WebSocket-Protocol`].map(_.protocols) shouldEqual (
          if (selectProtocol) Some(Seq("chat")) else None)
        assertClose(response, 4200, "finished")
      }
    }

    "preserve the peer-initiated close and handler-failure paths" in {
      val ownClose = FrameOutHandler.completionClose(Protocol.CloseCodes.ServiceRestart, "restarting")
      def createProbes() =
        TestSource[FrameOutHandler.Input]()
          .via(FrameOutHandler.create(serverSide = true, 5.seconds.dilated, NoLogging).addAttributes(
            Attributes(ownClose)))
          .toMat(TestSink[FrameStart]())(Keep.both)
          .run()

      val (peerFirstIn, peerFirstOut) = createProbes()
      peerFirstOut.request(1)
      peerFirstIn.sendNext(PeerClosed(Some(Protocol.CloseCodes.GoingAway), "away"))
      peerFirstIn.sendNext(UserHandlerCompleted)
      FrameEventParser.parseCloseCode(peerFirstOut.expectNext().data) shouldEqual (
        Some((Protocol.CloseCodes.GoingAway, "away")))
      peerFirstOut.expectComplete()

      val (failedIn, failedOut) = createProbes()
      failedOut.request(1)
      failedIn.sendNext(UserHandlerErredOut(new IllegalStateException("test handler failure")))
      FrameEventParser.parseCloseCode(failedOut.expectNext().data) shouldEqual (
        Some((Protocol.CloseCodes.UnexpectedCondition, "internal error")))
      failedOut.request(1)
      failedIn.sendNext(PeerClosed(Some(Protocol.CloseCodes.Regular), ""))
      failedOut.expectComplete()
    }

    "reject an invalid server status or reason when the application configures it" in {
      val response = upgrade().handleMessages(handler)
      for ((code, reason) <- List(
          (Protocol.CloseCodes.ClientRejectsExtension, ""),
          (4200, "\u00e9" * 62),
          (4200, "\ud800"))) {
        intercept[IllegalArgumentException](WebSocketUpgrade.withCompletionClose(response, code, reason))
      }
    }

    "accept a reason at the UTF-8 byte limit" in {
      val reason = "\u00e9" * 61 + "x"
      assertClose(WebSocketUpgrade.withCompletionClose(upgrade().handleMessages(handler), Protocol.CloseCodes.GoingAway,
        reason),
        Protocol.CloseCodes.GoingAway, reason)
    }
  }
}
