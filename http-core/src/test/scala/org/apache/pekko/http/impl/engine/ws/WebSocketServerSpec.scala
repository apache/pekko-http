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

import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.Deflater
import java.util.zip.Inflater

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.http.scaladsl.model.ws._
import pekko.http.scaladsl.model.AttributeKeys.webSocketUpgrade
import pekko.stream.Materializer
import pekko.stream.scaladsl.{ Flow, Keep, Sink, Source }
import pekko.stream.testkit.Utils
import pekko.stream.testkit.scaladsl.TestSink
import pekko.util.ByteString
import pekko.http.impl.engine.server.HttpServerTestSetupBase
import pekko.http.impl.settings.WebSocketCompressionSettingsImpl
import pekko.http.impl.settings.WebSocketSettingsImpl
import pekko.http.impl.util.PekkoSpecWithMaterializer

import scala.concurrent.duration._

class WebSocketServerSpec extends PekkoSpecWithMaterializer("pekko.http.server.websocket.log-frames = on") { spec =>

  private val EmptyDeflateBlock = ByteString(0x00)

  "The server-side WebSocket integration should" should {
    "establish a websocket connection when the user requests it" should {
      "when user handler instantly tries to send messages" in Utils.assertAllStagesStopped {
        new TestSetup {
          send(
            """GET /chat HTTP/1.1
              |Host: server.example.com
              |Upgrade: websocket
              |Connection: Upgrade
              |Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==
              |Origin: http://example.com
              |Sec-WebSocket-Version: 13
              |
              |""")

          val request = expectRequest()
          val upgrade = request.attribute(webSocketUpgrade)
          upgrade.isDefined shouldBe true

          val source =
            Source(List(1, 2, 3, 4, 5)).map(num => TextMessage.Strict(s"Message $num"))
          val handler = Flow.fromSinkAndSourceMat(Sink.ignore, source)(Keep.none)
          val response = upgrade.get.handleMessages(handler)
          responses.sendNext(response)

          expectResponseWithWipedDate(
            """HTTP/1.1 101 Switching Protocols
              |Upgrade: websocket
              |Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
              |Server: pekko-http/test
              |Date: XXXX
              |Connection: upgrade
              |
              |""")

          expectWSFrame(Protocol.Opcode.Text, ByteString("Message 1"), fin = true)
          expectWSFrame(Protocol.Opcode.Text, ByteString("Message 2"), fin = true)
          expectWSFrame(Protocol.Opcode.Text, ByteString("Message 3"), fin = true)
          expectWSFrame(Protocol.Opcode.Text, ByteString("Message 4"), fin = true)
          expectWSFrame(Protocol.Opcode.Text, ByteString("Message 5"), fin = true)
          expectWSCloseFrame(Protocol.CloseCodes.Regular)

          sendWSCloseFrame(Protocol.CloseCodes.Regular, mask = true)
          closeNetworkInput()
          expectNetworkClose()
        }
      }
      "for echoing user handler" in Utils.assertAllStagesStopped {
        new TestSetup {

          send(
            """GET /echo HTTP/1.1
              |Host: server.example.com
              |Upgrade: websocket
              |Connection: Upgrade
              |Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==
              |Origin: http://example.com
              |Sec-WebSocket-Version: 13
              |
              |""")

          val request = expectRequest()
          val upgrade = request.attribute(webSocketUpgrade)
          upgrade.isDefined shouldBe true

          val response = upgrade.get.handleMessages(Flow[Message]) // simple echoing
          responses.sendNext(response)

          expectResponseWithWipedDate(
            """HTTP/1.1 101 Switching Protocols
              |Upgrade: websocket
              |Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
              |Server: pekko-http/test
              |Date: XXXX
              |Connection: upgrade
              |
              |""")

          sendWSFrame(Protocol.Opcode.Text, ByteString("Message 1"), fin = true, mask = true)
          expectWSFrame(Protocol.Opcode.Text, ByteString("Message 1"), fin = true)
          sendWSFrame(Protocol.Opcode.Text, ByteString("Message 2"), fin = true, mask = true)
          expectWSFrame(Protocol.Opcode.Text, ByteString("Message 2"), fin = true)
          sendWSFrame(Protocol.Opcode.Text, ByteString("Message 3"), fin = true, mask = true)
          expectWSFrame(Protocol.Opcode.Text, ByteString("Message 3"), fin = true)
          sendWSFrame(Protocol.Opcode.Text, ByteString("Message 4"), fin = true, mask = true)
          expectWSFrame(Protocol.Opcode.Text, ByteString("Message 4"), fin = true)
          sendWSFrame(Protocol.Opcode.Text, ByteString("Message 5"), fin = true, mask = true)
          expectWSFrame(Protocol.Opcode.Text, ByteString("Message 5"), fin = true)

          sendWSCloseFrame(Protocol.CloseCodes.Regular, mask = true)
          expectWSCloseFrame(Protocol.CloseCodes.Regular)

          closeNetworkInput()
          expectNetworkClose()
        }
      }
    }
    "send Ping keep-alive heartbeat" should {
      "on idle websocket connection" in Utils.assertAllStagesStopped {
        new TestSetup {

          override def settings = {
            val defaults = super.settings.websocketSettings
            super.settings.withWebsocketSettings(defaults
              .withPeriodicKeepAliveMode("ping")
              .withPeriodicKeepAliveMaxIdle(100.millis))
          }

          send(
            """GET /echo HTTP/1.1
              |Host: server.example.com
              |Upgrade: websocket
              |Connection: Upgrade
              |Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==
              |Origin: http://example.com
              |Sec-WebSocket-Version: 13
              |
              |""")

          val request = expectRequest()
          val upgrade = request.attribute(webSocketUpgrade)

          val handler = Flow.fromSinkAndSourceCoupled(Sink.ignore, Source.maybe[Message])

          // since the handler is not doing anything, we expect the server to start sending Ping frames transparently
          val response = upgrade.get.handleMessages(handler)
          responses.sendNext(response)

          expectResponseWithWipedDate(
            """HTTP/1.1 101 Switching Protocols
              |Upgrade: websocket
              |Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
              |Server: pekko-http/test
              |Date: XXXX
              |Connection: upgrade
              |
              |""")

          expectWSFrame(Protocol.Opcode.Ping, ByteString.empty, fin = true)

          sendWSCloseFrame(Protocol.CloseCodes.Regular, mask = true)
          expectWSCloseFrame(Protocol.CloseCodes.Regular)

          closeNetworkInput()
          expectNetworkClose()
        }
      }
    }
    "prevent the selection of an unavailable subprotocol" in pending
    "support permessage-deflate compression" should {
      "negotiate permessage-deflate when requested by the client" in Utils.assertAllStagesStopped {
        new TestSetup {
          sendWebSocketRequest("Sec-WebSocket-Extensions: permessage-deflate\r\n")

          val request = expectRequest()
          val upgrade = request.attribute(webSocketUpgrade)
          val response = upgrade.get.handleMessages(Flow[Message])
          responses.sendNext(response)

          expectResponseWithWipedDate(
            """HTTP/1.1 101 Switching Protocols
              |Upgrade: websocket
              |Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
              |Sec-WebSocket-Extensions: permessage-deflate
              |Server: pekko-http/test
              |Date: XXXX
              |Connection: upgrade
              |
              |""")

          sendWSCloseFrame(Protocol.CloseCodes.Regular, mask = true)
          expectWSCloseFrame(Protocol.CloseCodes.Regular)

          closeNetworkInput()
          expectNetworkClose()
        }
      }

      "negotiate permessage-deflate from repeated extension headers" in Utils.assertAllStagesStopped {
        new TestSetup {
          sendWebSocketRequest(
            "Sec-WebSocket-Extensions: unknown-extension\r\n" +
            "Sec-WebSocket-Extensions: permessage-deflate\r\n")

          val request = expectRequest()
          val upgrade = request.attribute(webSocketUpgrade)
          val response = upgrade.get.handleMessages(Flow[Message])
          responses.sendNext(response)

          expectResponseWithWipedDate(
            """HTTP/1.1 101 Switching Protocols
              |Upgrade: websocket
              |Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
              |Sec-WebSocket-Extensions: permessage-deflate
              |Server: pekko-http/test
              |Date: XXXX
              |Connection: upgrade
              |
              |""")

          sendWSCloseFrame(Protocol.CloseCodes.Regular, mask = true)
          expectWSCloseFrame(Protocol.CloseCodes.Regular)

          closeNetworkInput()
          expectNetworkClose()
        }
      }

      "negotiate permessage-deflate from a fallback offer in the same extension header" in
      Utils.assertAllStagesStopped {
        new TestSetup {
          sendWebSocketRequest(
            "Sec-WebSocket-Extensions: permessage-deflate; server_max_window_bits=14, permessage-deflate\r\n")

          val request = expectRequest()
          val upgrade = request.attribute(webSocketUpgrade)
          val response = upgrade.get.handleMessages(Flow[Message])
          responses.sendNext(response)

          expectResponseWithWipedDate(
            """HTTP/1.1 101 Switching Protocols
              |Upgrade: websocket
              |Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
              |Sec-WebSocket-Extensions: permessage-deflate
              |Server: pekko-http/test
              |Date: XXXX
              |Connection: upgrade
              |
              |""")

          sendWSCloseFrame(Protocol.CloseCodes.Regular, mask = true)
          expectWSCloseFrame(Protocol.CloseCodes.Regular)

          closeNetworkInput()
          expectNetworkClose()
        }
      }

      "inflate inbound permessage-deflate messages before passing them to the application" in
      Utils.assertAllStagesStopped {
        new TestSetup {
          sendWebSocketRequest("Sec-WebSocket-Extensions: permessage-deflate\r\n")

          val request = expectRequest()
          val upgrade = request.attribute(webSocketUpgrade)
          val response = upgrade.get.handleMessages(Flow[Message])
          responses.sendNext(response)

          expectResponseWithWipedDate(
            """HTTP/1.1 101 Switching Protocols
              |Upgrade: websocket
              |Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
              |Sec-WebSocket-Extensions: permessage-deflate
              |Server: pekko-http/test
              |Date: XXXX
              |Connection: upgrade
              |
              |""")

          sendWSFrame(Protocol.Opcode.Text, deflatePerMessage(ByteString("compressed client message")), fin = true,
            mask = true, rsv1 = true)
          expectCompressedTextFrame("compressed client message")

          sendWSCloseFrame(Protocol.CloseCodes.Regular, mask = true)
          expectWSCloseFrame(Protocol.CloseCodes.Regular)

          closeNetworkInput()
          expectNetworkClose()
        }
      }

      "inflate empty inbound permessage-deflate messages before passing them to the application" in
      Utils.assertAllStagesStopped {
        new TestSetup {
          sendWebSocketRequest("Sec-WebSocket-Extensions: permessage-deflate\r\n")

          val request = expectRequest()
          val upgrade = request.attribute(webSocketUpgrade)
          val response = upgrade.get.handleMessages(Flow[Message])
          responses.sendNext(response)

          expectResponseWithWipedDate(
            """HTTP/1.1 101 Switching Protocols
              |Upgrade: websocket
              |Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
              |Sec-WebSocket-Extensions: permessage-deflate
              |Server: pekko-http/test
              |Date: XXXX
              |Connection: upgrade
              |
              |""")

          sendWSFrame(Protocol.Opcode.Text, EmptyDeflateBlock, fin = true, mask = true, rsv1 = true)
          expectCompressedTextFrame("")

          sendWSCloseFrame(Protocol.CloseCodes.Regular, mask = true)
          expectWSCloseFrame(Protocol.CloseCodes.Regular)

          closeNetworkInput()
          expectNetworkClose()
        }
      }

      "pass uncompressed inbound messages through when permessage-deflate is negotiated" in
      Utils.assertAllStagesStopped {
        new TestSetup {
          sendWebSocketRequest("Sec-WebSocket-Extensions: permessage-deflate\r\n")

          val request = expectRequest()
          val upgrade = request.attribute(webSocketUpgrade)
          val response = upgrade.get.handleMessages(Flow[Message])
          responses.sendNext(response)

          expectResponseWithWipedDate(
            """HTTP/1.1 101 Switching Protocols
              |Upgrade: websocket
              |Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
              |Sec-WebSocket-Extensions: permessage-deflate
              |Server: pekko-http/test
              |Date: XXXX
              |Connection: upgrade
              |
              |""")

          sendWSFrame(Protocol.Opcode.Text, ByteString("plain client message"), fin = true, mask = true)
          expectCompressedTextFrame("plain client message")

          sendWSCloseFrame(Protocol.CloseCodes.Regular, mask = true)
          expectWSCloseFrame(Protocol.CloseCodes.Regular)

          closeNetworkInput()
          expectNetworkClose()
        }
      }

      "handle fragmented compressed messages with interleaved control frames" in Utils.assertAllStagesStopped {
        new TestSetup {
          sendWebSocketRequest("Sec-WebSocket-Extensions: permessage-deflate\r\n")

          val request = expectRequest()
          val upgrade = request.attribute(webSocketUpgrade)
          val response = upgrade.get.handleMessages(Flow[Message])
          responses.sendNext(response)

          expectResponseWithWipedDate(
            """HTTP/1.1 101 Switching Protocols
              |Upgrade: websocket
              |Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
              |Sec-WebSocket-Extensions: permessage-deflate
              |Server: pekko-http/test
              |Date: XXXX
              |Connection: upgrade
              |
              |""")

          val (firstPart, secondPart) =
            deflatePerMessageFrames(ByteString("fragmented compressed client message"), splitAt = 16)

          sendWSFrame(Protocol.Opcode.Text, firstPart, fin = false, mask = true, rsv1 = true)
          sendWSFrame(Protocol.Opcode.Ping, ByteString("ping"), fin = true, mask = true)
          val firstResponse = expectCompressedFrame(Protocol.Opcode.Text, fin = false, rsv1 = true)
          expectWSFrame(Protocol.Opcode.Pong, ByteString("ping"), fin = true)
          sendWSFrame(Protocol.Opcode.Continuation, secondPart, fin = true, mask = true)

          val secondResponse = expectCompressedFrame(Protocol.Opcode.Continuation, fin = false, rsv1 = false)
          val finalResponse = expectCompressedFrame(Protocol.Opcode.Continuation, fin = true, rsv1 = false)
          inflatePerMessageFrames(Seq(firstResponse, secondResponse, finalResponse)).utf8String shouldEqual
          "fragmented compressed client message"

          sendWSCloseFrame(Protocol.CloseCodes.Regular, mask = true)
          expectWSCloseFrame(Protocol.CloseCodes.Regular)

          closeNetworkInput()
          expectNetworkClose()
        }
      }

      "not finish fragmented compressed messages at split frame data boundaries" in Utils.assertAllStagesStopped {
        new TestSetup {
          sendWebSocketRequest("Sec-WebSocket-Extensions: permessage-deflate\r\n")

          val request = expectRequest()
          val upgrade = request.attribute(webSocketUpgrade)
          val response = upgrade.get.handleMessages(Flow[Message])
          responses.sendNext(response)

          expectResponseWithWipedDate(
            """HTTP/1.1 101 Switching Protocols
              |Upgrade: websocket
              |Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
              |Sec-WebSocket-Extensions: permessage-deflate
              |Server: pekko-http/test
              |Date: XXXX
              |Connection: upgrade
              |
              |""")

          val (firstFrame, secondFrame) =
            deflatePerMessageFrames(ByteString("split frame data compressed client message"), splitAt = 22)

          sendWSFrameInTwoNetworkChunks(Protocol.Opcode.Text, firstFrame, fin = false, mask = true, rsv1 = true)
          sendWSFrame(Protocol.Opcode.Continuation, secondFrame, fin = true, mask = true)

          val firstResponse = expectCompressedFrame(Protocol.Opcode.Text, fin = false, rsv1 = true)
          val secondResponse = expectCompressedFrame(Protocol.Opcode.Continuation, fin = false, rsv1 = false)
          val finalResponse = expectCompressedFrame(Protocol.Opcode.Continuation, fin = true, rsv1 = false)
          inflatePerMessageFrames(Seq(firstResponse, secondResponse, finalResponse)).utf8String shouldEqual
          "split frame data compressed client message"

          sendWSCloseFrame(Protocol.CloseCodes.Regular, mask = true)
          expectWSCloseFrame(Protocol.CloseCodes.Regular)

          closeNetworkInput()
          expectNetworkClose()
        }
      }

      "finish fragmented compressed messages when the last fragment only completes the inflater" in
      Utils.assertAllStagesStopped {
        new TestSetup {
          sendWebSocketRequest("Sec-WebSocket-Extensions: permessage-deflate\r\n")

          val request = expectRequest()
          val upgrade = request.attribute(webSocketUpgrade)
          val response = upgrade.get.handleMessages(Flow[Message])
          responses.sendNext(response)

          expectResponseWithWipedDate(
            """HTTP/1.1 101 Switching Protocols
              |Upgrade: websocket
              |Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
              |Sec-WebSocket-Extensions: permessage-deflate
              |Server: pekko-http/test
              |Date: XXXX
              |Connection: upgrade
              |
              |""")

          val payload =
            ByteString.fromArrayUnsafe(hexToBytes(
              "677170647a777a737574656b707a787a6f6a7561756578756f6b7868616371716c657a6d64697479766d726f6" +
              "269746c6376777464776f6f72767a726f64667278676764687775786f6762766d776d706b76697773777a7072" +
              "6a6a737279707a7078697a6c69616d7461656d646278626d786f66666e686e776a7a7461746d7a776668776b6" +
              "f6f736e73746575637a6d727a7175707a6e74627578687871767771697a71766c64626d78726d6d7675756877" +
              "62667963626b687a726d676e646263776e67797264706d6c6863626577616967706a78636a72697464756e627" +
              "977616f79736475676f76736f7178746a7a7479626c64636b6b6778637768746c62"))
          val frames = deflatePerMessageFrames(payload, compressionLevel = 9, fragmentCount = 4)
          frames.last.length shouldEqual 1
          frames.last.head shouldEqual 1

          sendWSFrame(Protocol.Opcode.Text, frames(0), fin = false, mask = true, rsv1 = true)
          sendWSFrame(Protocol.Opcode.Continuation, frames(1), fin = false, mask = true)
          sendWSFrame(Protocol.Opcode.Continuation, frames(2), fin = false, mask = true)
          sendWSFrame(Protocol.Opcode.Continuation, frames(3), fin = true, mask = true)

          val firstResponse = expectCompressedFrame(Protocol.Opcode.Text, fin = false, rsv1 = true)
          inflatePerMessageFrames(firstResponse +: expectCompressedContinuationFramesUntilFinal()) shouldEqual payload

          sendWSCloseFrame(Protocol.CloseCodes.Regular, mask = true)
          expectWSCloseFrame(Protocol.CloseCodes.Regular)

          closeNetworkInput()
          expectNetworkClose()
        }
      }

      "deflate outbound messages sent by the application" in Utils.assertAllStagesStopped {
        new TestSetup {
          sendWebSocketRequest("Sec-WebSocket-Extensions: permessage-deflate\r\n")

          val request = expectRequest()
          val upgrade = request.attribute(webSocketUpgrade)
          val response = upgrade.get.handleMessages(
            Flow.fromSinkAndSource(Sink.ignore, Source.single(TextMessage.Strict("compressed server message"))))
          responses.sendNext(response)

          expectResponseWithWipedDate(
            """HTTP/1.1 101 Switching Protocols
              |Upgrade: websocket
              |Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
              |Sec-WebSocket-Extensions: permessage-deflate
              |Server: pekko-http/test
              |Date: XXXX
              |Connection: upgrade
              |
              |""")

          expectCompressedTextFrame("compressed server message")
          expectWSCloseFrame(Protocol.CloseCodes.Regular)

          sendWSCloseFrame(Protocol.CloseCodes.Regular, mask = true)
          closeNetworkInput()
          expectNetworkClose()
        }
      }

      "deflate empty outbound messages sent by the application" in Utils.assertAllStagesStopped {
        new TestSetup {
          sendWebSocketRequest("Sec-WebSocket-Extensions: permessage-deflate\r\n")

          val request = expectRequest()
          val upgrade = request.attribute(webSocketUpgrade)
          val response = upgrade.get.handleMessages(
            Flow.fromSinkAndSource(Sink.ignore, Source.single(TextMessage.Strict(""))))
          responses.sendNext(response)

          expectResponseWithWipedDate(
            """HTTP/1.1 101 Switching Protocols
              |Upgrade: websocket
              |Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
              |Sec-WebSocket-Extensions: permessage-deflate
              |Server: pekko-http/test
              |Date: XXXX
              |Connection: upgrade
              |
              |""")

          val payload = expectCompressedFrame(Protocol.Opcode.Text, fin = true, rsv1 = true)
          payload shouldEqual EmptyDeflateBlock
          inflatePerMessage(payload) shouldEqual ByteString.empty
          expectWSCloseFrame(Protocol.CloseCodes.Regular)

          sendWSCloseFrame(Protocol.CloseCodes.Regular, mask = true)
          closeNetworkInput()
          expectNetworkClose()
        }
      }

      "deflate streamed outbound messages without aggregating the full message" in Utils.assertAllStagesStopped {
        new TestSetup {
          sendWebSocketRequest("Sec-WebSocket-Extensions: permessage-deflate\r\n")

          val request = expectRequest()
          val upgrade = request.attribute(webSocketUpgrade)
          val response = upgrade.get.handleMessages(
            Flow.fromSinkAndSource(
              Sink.ignore,
              Source.single(TextMessage(Source(List("streamed ", "server ", "message"))))))
          responses.sendNext(response)

          expectResponseWithWipedDate(
            """HTTP/1.1 101 Switching Protocols
              |Upgrade: websocket
              |Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
              |Sec-WebSocket-Extensions: permessage-deflate
              |Server: pekko-http/test
              |Date: XXXX
              |Connection: upgrade
              |
              |""")

          val firstResponse = expectCompressedFrame(Protocol.Opcode.Text, fin = false, rsv1 = true)
          val secondResponse = expectCompressedFrame(Protocol.Opcode.Continuation, fin = false, rsv1 = false)
          val thirdResponse = expectCompressedFrame(Protocol.Opcode.Continuation, fin = false, rsv1 = false)
          val finalResponse = expectCompressedFrame(Protocol.Opcode.Continuation, fin = true, rsv1 = false)
          inflatePerMessageFrames(Seq(firstResponse, secondResponse, thirdResponse,
            finalResponse)).utf8String shouldEqual
          "streamed server message"
          expectWSCloseFrame(Protocol.CloseCodes.Regular)

          sendWSCloseFrame(Protocol.CloseCodes.Regular, mask = true)
          closeNetworkInput()
          expectNetworkClose()
        }
      }

      "release compression resources after normal completion" in Utils.assertAllStagesStopped {
        val tracking = new TrackingCompression

        Source.empty[FrameEventOrError].via(inflaterFlow(tracking)).runWith(Sink.ignore).futureValue
        Source.empty[FrameEvent].via(deflaterFlow(tracking)).runWith(Sink.ignore).futureValue

        tracking.awaitAllEnded()
      }

      "release compression resources after upstream failure" in Utils.assertAllStagesStopped {
        val tracking = new TrackingCompression
        val failure = new RuntimeException("test failure")

        Source.failed[FrameEventOrError](failure)
          .via(inflaterFlow(tracking))
          .runWith(Sink.ignore)
          .failed
          .futureValue shouldEqual failure
        Source.failed[FrameEvent](failure)
          .via(deflaterFlow(tracking))
          .runWith(Sink.ignore)
          .failed
          .futureValue shouldEqual failure

        tracking.awaitAllEnded()
      }

      "release compression resources after downstream cancellation" in Utils.assertAllStagesStopped {
        val tracking = new TrackingCompression
        val inflaterProbe =
          Source.maybe[FrameEventOrError].via(inflaterFlow(tracking)).runWith(TestSink[FrameEventOrError]())
        val deflaterProbe = Source.maybe[FrameEvent].via(deflaterFlow(tracking)).runWith(TestSink[FrameEvent]())

        inflaterProbe.cancel()
        deflaterProbe.cancel()

        tracking.awaitAllEnded()
      }

      "release compression resources after protocol errors" in Utils.assertAllStagesStopped {
        val tracking = new TrackingCompression
        val invalidInbound =
          FrameEvent.fullFrame(
            Protocol.Opcode.Text,
            None,
            ByteString(0xFF, 0xFF, 0xFF),
            fin = true,
            rsv1 = true)
        val invalidOutbound =
          FrameEvent.fullFrame(Protocol.Opcode.Text, None, ByteString("reserved"), fin = true, rsv1 = true)

        Source.single[FrameEventOrError](invalidInbound)
          .via(inflaterFlow(tracking))
          .runWith(Sink.ignore)
          .failed
          .futureValue shouldBe a[ProtocolException]
        Source.single[FrameEvent](invalidOutbound)
          .via(deflaterFlow(tracking))
          .runWith(Sink.ignore)
          .failed
          .futureValue shouldBe a[ProtocolException]

        tracking.awaitAllEnded()
      }

      "release compression resources with incomplete compression state" in Utils.assertAllStagesStopped {
        val tracking = new TrackingCompression
        val payload = ByteString("unfinished compressed message")
        val (firstCompressedFragment, _) = deflatePerMessageFrames(payload, splitAt = 12)
        val incompleteInboundMessage =
          FrameEvent.fullFrame(
            Protocol.Opcode.Text,
            None,
            firstCompressedFragment,
            fin = false,
            rsv1 = true)
        val incompleteInboundFrame =
          FrameStart(
            FrameHeader(
              Protocol.Opcode.Text,
              None,
              length = firstCompressedFragment.length + 1,
              fin = true,
              rsv1 = true),
            firstCompressedFragment)
        val incompleteOutboundMessage =
          FrameEvent.fullFrame(Protocol.Opcode.Text, None, payload, fin = false)
        val incompleteOutboundFrame =
          FrameStart(
            FrameHeader(Protocol.Opcode.Text, None, length = payload.length + 1, fin = true),
            payload)

        Source(List[FrameEventOrError](incompleteInboundMessage))
          .via(inflaterFlow(tracking))
          .runWith(Sink.ignore)
          .futureValue
        Source(List[FrameEventOrError](incompleteInboundFrame))
          .via(inflaterFlow(tracking))
          .runWith(Sink.ignore)
          .futureValue
        Source(List[FrameEvent](incompleteOutboundMessage))
          .via(deflaterFlow(tracking))
          .runWith(Sink.ignore)
          .futureValue
        Source(List[FrameEvent](incompleteOutboundFrame))
          .via(deflaterFlow(tracking))
          .runWith(Sink.ignore)
          .futureValue

        tracking.awaitAllEnded()
      }

      "fail invalid compressed messages with a protocol error" in Utils.assertAllStagesStopped {
        new TestSetup {
          sendWebSocketRequest("Sec-WebSocket-Extensions: permessage-deflate\r\n")

          val request = expectRequest()
          val upgrade = request.attribute(webSocketUpgrade)
          val response = upgrade.get.handleMessages(Flow[Message])
          responses.sendNext(response)

          expectResponseWithWipedDate(
            """HTTP/1.1 101 Switching Protocols
              |Upgrade: websocket
              |Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
              |Sec-WebSocket-Extensions: permessage-deflate
              |Server: pekko-http/test
              |Date: XXXX
              |Connection: upgrade
              |
              |""")

          sendWSFrame(Protocol.Opcode.Text, ByteString(0xFF, 0xFF, 0xFF), fin = true, mask = true, rsv1 = true)
          expectWSCloseFrame(Protocol.CloseCodes.ProtocolError)

          closeNetworkInput()
          expectNetworkClose()
        }
      }

      "fail non-continuation frames during fragmented compressed messages with a protocol error" in
      Utils.assertAllStagesStopped {
        new TestSetup {
          sendWebSocketRequest("Sec-WebSocket-Extensions: permessage-deflate\r\n")

          val request = expectRequest()
          val upgrade = request.attribute(webSocketUpgrade)
          val response = upgrade.get.handleMessages(Flow[Message])
          responses.sendNext(response)

          expectResponseWithWipedDate(
            """HTTP/1.1 101 Switching Protocols
              |Upgrade: websocket
              |Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
              |Sec-WebSocket-Extensions: permessage-deflate
              |Server: pekko-http/test
              |Date: XXXX
              |Connection: upgrade
              |
              |""")

          val (firstPart, _) = deflatePerMessageFrames(ByteString("fragmented compressed client message"), splitAt = 16)

          sendWSFrame(Protocol.Opcode.Text, firstPart, fin = false, mask = true, rsv1 = true)
          sendWSFrame(Protocol.Opcode.Text, ByteString("not a continuation"), fin = true, mask = true)
          expectCompressedFrame(Protocol.Opcode.Text, fin = false, rsv1 = true)
          expectWSCloseFrame(Protocol.CloseCodes.ProtocolError)

          closeNetworkInput()
          expectNetworkClose()
        }
      }

      "not negotiate permessage-deflate when websocket compression is disabled" in Utils.assertAllStagesStopped {
        new TestSetup {
          override def settings = {
            val defaults = super.settings.websocketSettings.asInstanceOf[WebSocketSettingsImpl]
            super.settings.withWebsocketSettings(
              defaults.copy(compression = defaults.compression.copy(enabled = false)))
          }

          sendWebSocketRequest("Sec-WebSocket-Extensions: permessage-deflate\r\n")

          val request = expectRequest()
          val upgrade = request.attribute(webSocketUpgrade)
          val response = upgrade.get.handleMessages(Flow[Message])
          responses.sendNext(response)

          expectResponseWithWipedDate(
            """HTTP/1.1 101 Switching Protocols
              |Upgrade: websocket
              |Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
              |Server: pekko-http/test
              |Date: XXXX
              |Connection: upgrade
              |
              |""")

          sendWSCloseFrame(Protocol.CloseCodes.Regular, mask = true)
          expectWSCloseFrame(Protocol.CloseCodes.Regular)

          closeNetworkInput()
          expectNetworkClose()
        }
      }

      "not negotiate permessage-deflate when compression is disabled for the accepted WebSocket" in
      Utils.assertAllStagesStopped {
        new TestSetup {
          sendWebSocketRequest("Sec-WebSocket-Extensions: permessage-deflate\r\n")

          val request = expectRequest()
          val upgrade = request.attribute(webSocketUpgrade)
          val response = upgrade.get.handleMessages(
            Flow.fromSinkAndSource(Sink.ignore, Source.single(TextMessage.Strict("plain server message"))),
            None,
            compressionEnabled = false)
          responses.sendNext(response)

          expectResponseWithWipedDate(
            """HTTP/1.1 101 Switching Protocols
              |Upgrade: websocket
              |Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
              |Server: pekko-http/test
              |Date: XXXX
              |Connection: upgrade
              |
              |""")

          expectWSFrame(Protocol.Opcode.Text, ByteString("plain server message"), fin = true)
          expectWSCloseFrame(Protocol.CloseCodes.Regular)

          sendWSCloseFrame(Protocol.CloseCodes.Regular, mask = true)
          closeNetworkInput()
          expectNetworkClose()
        }
      }

      "negotiate configured permessage-deflate parameters" in Utils.assertAllStagesStopped {
        new TestSetup {
          override def settings = {
            val defaults = super.settings.websocketSettings.asInstanceOf[WebSocketSettingsImpl]
            super.settings.withWebsocketSettings(
              defaults.copy(compression = defaults.compression.copy(
                preferredClientWindowSize = 11,
                allowServerNoContext = true,
                preferredClientNoContext = true)))
          }

          sendWebSocketRequest(
            "Sec-WebSocket-Extensions: permessage-deflate; client_max_window_bits; server_max_window_bits=15; " +
            "client_no_context_takeover; server_no_context_takeover\r\n")

          val request = expectRequest()
          val upgrade = request.attribute(webSocketUpgrade)
          val response = upgrade.get.handleMessages(Flow[Message])
          responses.sendNext(response)

          expectResponseWithWipedDate(
            """HTTP/1.1 101 Switching Protocols
              |Upgrade: websocket
              |Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
              |Sec-WebSocket-Extensions: permessage-deflate; client_max_window_bits=11; server_max_window_bits=15; client_no_context_takeover; server_no_context_takeover
              |Server: pekko-http/test
              |Date: XXXX
              |Connection: upgrade
              |
              |""")

          sendWSCloseFrame(Protocol.CloseCodes.Regular, mask = true)
          expectWSCloseFrame(Protocol.CloseCodes.Regular)

          closeNetworkInput()
          expectNetworkClose()
        }
      }

      "negotiate explicit client_max_window_bits values" in Utils.assertAllStagesStopped {
        new TestSetup {
          sendWebSocketRequest("Sec-WebSocket-Extensions: permessage-deflate; client_max_window_bits=12\r\n")

          val request = expectRequest()
          val upgrade = request.attribute(webSocketUpgrade)
          val response = upgrade.get.handleMessages(Flow[Message])
          responses.sendNext(response)

          expectResponseWithWipedDate(
            """HTTP/1.1 101 Switching Protocols
              |Upgrade: websocket
              |Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
              |Sec-WebSocket-Extensions: permessage-deflate; client_max_window_bits=12
              |Server: pekko-http/test
              |Date: XXXX
              |Connection: upgrade
              |
              |""")

          sendWSCloseFrame(Protocol.CloseCodes.Regular, mask = true)
          expectWSCloseFrame(Protocol.CloseCodes.Regular)

          closeNetworkInput()
          expectNetworkClose()
        }
      }

      "not negotiate invalid client_max_window_bits values" in Utils.assertAllStagesStopped {
        new TestSetup {
          sendWebSocketRequest("Sec-WebSocket-Extensions: permessage-deflate; client_max_window_bits=7\r\n")

          val request = expectRequest()
          val upgrade = request.attribute(webSocketUpgrade)
          val response = upgrade.get.handleMessages(Flow[Message])
          responses.sendNext(response)

          expectResponseWithWipedDate(
            """HTTP/1.1 101 Switching Protocols
              |Upgrade: websocket
              |Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
              |Server: pekko-http/test
              |Date: XXXX
              |Connection: upgrade
              |
              |""")

          sendWSCloseFrame(Protocol.CloseCodes.Regular, mask = true)
          expectWSCloseFrame(Protocol.CloseCodes.Regular)

          closeNetworkInput()
          expectNetworkClose()
        }
      }

      "not negotiate client_no_context_takeover when it was not requested by the client" in
      Utils.assertAllStagesStopped {
        new TestSetup {
          override def settings = {
            val defaults = super.settings.websocketSettings.asInstanceOf[WebSocketSettingsImpl]
            super.settings.withWebsocketSettings(
              defaults.copy(compression = defaults.compression.copy(preferredClientNoContext = true)))
          }

          sendWebSocketRequest("Sec-WebSocket-Extensions: permessage-deflate\r\n")

          val request = expectRequest()
          val upgrade = request.attribute(webSocketUpgrade)
          val response = upgrade.get.handleMessages(Flow[Message])
          responses.sendNext(response)

          expectResponseWithWipedDate(
            """HTTP/1.1 101 Switching Protocols
              |Upgrade: websocket
              |Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
              |Sec-WebSocket-Extensions: permessage-deflate
              |Server: pekko-http/test
              |Date: XXXX
              |Connection: upgrade
              |
              |""")

          sendWSCloseFrame(Protocol.CloseCodes.Regular, mask = true)
          expectWSCloseFrame(Protocol.CloseCodes.Regular)

          closeNetworkInput()
          expectNetworkClose()
        }
      }

      "not negotiate permessage-deflate when server_no_context_takeover is not allowed" in
      Utils.assertAllStagesStopped {
        new TestSetup {
          sendWebSocketRequest("Sec-WebSocket-Extensions: permessage-deflate; server_no_context_takeover\r\n")

          val request = expectRequest()
          val upgrade = request.attribute(webSocketUpgrade)
          val response = upgrade.get.handleMessages(Flow[Message])
          responses.sendNext(response)

          expectResponseWithWipedDate(
            """HTTP/1.1 101 Switching Protocols
              |Upgrade: websocket
              |Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
              |Server: pekko-http/test
              |Date: XXXX
              |Connection: upgrade
              |
              |""")

          sendWSCloseFrame(Protocol.CloseCodes.Regular, mask = true)
          expectWSCloseFrame(Protocol.CloseCodes.Regular)

          closeNetworkInput()
          expectNetworkClose()
        }
      }

      "reset the server compressor when server_no_context_takeover is negotiated" in Utils.assertAllStagesStopped {
        new TestSetup {
          override def settings = {
            val defaults = super.settings.websocketSettings.asInstanceOf[WebSocketSettingsImpl]
            super.settings.withWebsocketSettings(
              defaults.copy(compression = defaults.compression.copy(allowServerNoContext = true)))
          }

          sendWebSocketRequest("Sec-WebSocket-Extensions: permessage-deflate; server_no_context_takeover\r\n")

          val request = expectRequest()
          val upgrade = request.attribute(webSocketUpgrade)
          val message = "same server message same server message same server message"
          val response = upgrade.get.handleMessages(
            Flow.fromSinkAndSource(
              Sink.ignore,
              Source(List(TextMessage.Strict(message), TextMessage.Strict(message)))))
          responses.sendNext(response)

          expectResponseWithWipedDate(
            """HTTP/1.1 101 Switching Protocols
              |Upgrade: websocket
              |Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
              |Sec-WebSocket-Extensions: permessage-deflate; server_no_context_takeover
              |Server: pekko-http/test
              |Date: XXXX
              |Connection: upgrade
              |
              |""")

          val firstPayload = expectCompressedFrame(Protocol.Opcode.Text, fin = true, rsv1 = true)
          val secondPayload = expectCompressedFrame(Protocol.Opcode.Text, fin = true, rsv1 = true)
          firstPayload shouldEqual deflatePerMessage(ByteString(message))
          secondPayload shouldEqual firstPayload
          inflatePerMessage(firstPayload).utf8String shouldEqual message
          inflatePerMessage(secondPayload).utf8String shouldEqual message
          expectWSCloseFrame(Protocol.CloseCodes.Regular)

          sendWSCloseFrame(Protocol.CloseCodes.Regular, mask = true)
          closeNetworkInput()
          expectNetworkClose()
        }
      }

      "reset the server decompressor when client_no_context_takeover is negotiated" in Utils.assertAllStagesStopped {
        new TestSetup {
          override def settings = {
            val defaults = super.settings.websocketSettings.asInstanceOf[WebSocketSettingsImpl]
            super.settings.withWebsocketSettings(
              defaults.copy(compression = defaults.compression.copy(preferredClientNoContext = true)))
          }

          sendWebSocketRequest("Sec-WebSocket-Extensions: permessage-deflate; client_no_context_takeover\r\n")

          val request = expectRequest()
          val upgrade = request.attribute(webSocketUpgrade)
          val response = upgrade.get.handleMessages(Flow[Message])
          responses.sendNext(response)

          expectResponseWithWipedDate(
            """HTTP/1.1 101 Switching Protocols
              |Upgrade: websocket
              |Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
              |Sec-WebSocket-Extensions: permessage-deflate; client_no_context_takeover
              |Server: pekko-http/test
              |Date: XXXX
              |Connection: upgrade
              |
              |""")

          val Seq(firstPayload, secondPayload) = deflatePerMessagesWithContext(
            ByteString("same client message same client message same client message"),
            ByteString("same client message same client message same client message"))

          sendWSFrame(Protocol.Opcode.Text, firstPayload, fin = true, mask = true, rsv1 = true)
          expectCompressedTextFrame("same client message same client message same client message")
          sendWSFrame(Protocol.Opcode.Text, secondPayload, fin = true, mask = true, rsv1 = true)
          expectWSCloseFrame(Protocol.CloseCodes.ProtocolError)

          closeNetworkInput()
          expectNetworkClose()
        }
      }

      "not compress server control frames" in Utils.assertAllStagesStopped {
        new TestSetup {
          sendWebSocketRequest("Sec-WebSocket-Extensions: permessage-deflate\r\n")

          val request = expectRequest()
          val upgrade = request.attribute(webSocketUpgrade)
          val response = upgrade.get.asInstanceOf[UpgradeToWebSocketLowLevel].handleFrames(
            Flow.fromSinkAndSource(
              Sink.ignore,
              Source.single(FrameEvent.fullFrame(Protocol.Opcode.Ping, None, ByteString("server ping"), fin = true))))
          responses.sendNext(response)

          expectResponseWithWipedDate(
            """HTTP/1.1 101 Switching Protocols
              |Upgrade: websocket
              |Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
              |Sec-WebSocket-Extensions: permessage-deflate
              |Server: pekko-http/test
              |Date: XXXX
              |Connection: upgrade
              |
              |""")

          expectWSFrame(Protocol.Opcode.Ping, ByteString("server ping"), fin = true)
          netOut.expectComplete()

          sendWSCloseFrame(Protocol.CloseCodes.Regular, mask = true)
          closeNetworkInput()
        }
      }

      "fail low-level outbound data frames with reserved bits when compression is negotiated" in
      Utils.assertAllStagesStopped {
        new TestSetup {
          sendWebSocketRequest("Sec-WebSocket-Extensions: permessage-deflate\r\n")

          val request = expectRequest()
          val upgrade = request.attribute(webSocketUpgrade)
          val response = upgrade.get.asInstanceOf[UpgradeToWebSocketLowLevel].handleFrames(
            Flow.fromSinkAndSource(
              Sink.ignore,
              Source.single(FrameEvent.fullFrame(
                Protocol.Opcode.Text,
                None,
                ByteString("already compressed"),
                fin = true,
                rsv1 = true))))
          responses.sendNext(response)

          expectResponseWithWipedDate(
            """HTTP/1.1 101 Switching Protocols
              |Upgrade: websocket
              |Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
              |Sec-WebSocket-Extensions: permessage-deflate
              |Server: pekko-http/test
              |Date: XXXX
              |Connection: upgrade
              |
              |""")

          netOut.expectError()

          sendWSCloseFrame(Protocol.CloseCodes.Regular, mask = true)
          closeNetworkInput()
        }
      }

      "fail low-level outbound continuation frames with reserved bits when compression is negotiated" in
      Utils.assertAllStagesStopped {
        new TestSetup {
          sendWebSocketRequest("Sec-WebSocket-Extensions: permessage-deflate\r\n")

          val request = expectRequest()
          val upgrade = request.attribute(webSocketUpgrade)
          val response = upgrade.get.asInstanceOf[UpgradeToWebSocketLowLevel].handleFrames(
            Flow.fromSinkAndSource(
              Sink.ignore,
              Source(List(
                FrameEvent.fullFrame(Protocol.Opcode.Text, None, ByteString("fragmented "), fin = false),
                FrameEvent.fullFrame(
                  Protocol.Opcode.Continuation,
                  None,
                  ByteString("message"),
                  fin = true,
                  rsv1 = true)))))
          responses.sendNext(response)

          expectResponseWithWipedDate(
            """HTTP/1.1 101 Switching Protocols
              |Upgrade: websocket
              |Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
              |Sec-WebSocket-Extensions: permessage-deflate
              |Server: pekko-http/test
              |Date: XXXX
              |Connection: upgrade
              |
              |""")

          expectCompressedFrame(Protocol.Opcode.Text, fin = false, rsv1 = true)
          netOut.expectError()

          sendWSCloseFrame(Protocol.CloseCodes.Regular, mask = true)
          closeNetworkInput()
        }
      }

      "not negotiate unsupported server_max_window_bits values" in Utils.assertAllStagesStopped {
        new TestSetup {
          sendWebSocketRequest("Sec-WebSocket-Extensions: permessage-deflate; server_max_window_bits=14\r\n")

          val request = expectRequest()
          val upgrade = request.attribute(webSocketUpgrade)
          val response = upgrade.get.handleMessages(Flow[Message])
          responses.sendNext(response)

          expectResponseWithWipedDate(
            """HTTP/1.1 101 Switching Protocols
              |Upgrade: websocket
              |Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
              |Server: pekko-http/test
              |Date: XXXX
              |Connection: upgrade
              |
              |""")

          sendWSCloseFrame(Protocol.CloseCodes.Regular, mask = true)
          expectWSCloseFrame(Protocol.CloseCodes.Regular)

          closeNetworkInput()
          expectNetworkClose()
        }
      }

      "fail compressed messages exceeding max-allocation" in Utils.assertAllStagesStopped {
        new TestSetup {
          override def settings = {
            val defaults = super.settings.websocketSettings.asInstanceOf[WebSocketSettingsImpl]
            super.settings.withWebsocketSettings(
              defaults.copy(compression = defaults.compression.copy(maxAllocation = 4)))
          }

          sendWebSocketRequest("Sec-WebSocket-Extensions: permessage-deflate\r\n")

          val request = expectRequest()
          val upgrade = request.attribute(webSocketUpgrade)
          val response = upgrade.get.handleMessages(Flow[Message])
          responses.sendNext(response)

          expectResponseWithWipedDate(
            """HTTP/1.1 101 Switching Protocols
              |Upgrade: websocket
              |Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
              |Sec-WebSocket-Extensions: permessage-deflate
              |Server: pekko-http/test
              |Date: XXXX
              |Connection: upgrade
              |
              |""")

          sendWSFrame(Protocol.Opcode.Text, deflatePerMessage(ByteString("too large")), fin = true, mask = true,
            rsv1 = true)
          expectWSCloseFrame(Protocol.CloseCodes.ProtocolError)

          closeNetworkInput()
          expectNetworkClose()
        }
      }

      "fail fragmented compressed messages exceeding max-allocation cumulatively" in Utils.assertAllStagesStopped {
        new TestSetup {
          override def settings = {
            val defaults = super.settings.websocketSettings.asInstanceOf[WebSocketSettingsImpl]
            super.settings.withWebsocketSettings(
              defaults.copy(compression = defaults.compression.copy(maxAllocation = 4)))
          }

          sendWebSocketRequest("Sec-WebSocket-Extensions: permessage-deflate\r\n")

          val request = expectRequest()
          val upgrade = request.attribute(webSocketUpgrade)
          val response = upgrade.get.handleMessages(Flow[Message].mapConcat(_ => Nil: List[Message]))
          responses.sendNext(response)

          expectResponseWithWipedDate(
            """HTTP/1.1 101 Switching Protocols
              |Upgrade: websocket
              |Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
              |Sec-WebSocket-Extensions: permessage-deflate
              |Server: pekko-http/test
              |Date: XXXX
              |Connection: upgrade
              |
              |""")

          val (firstFrame, secondFrame) = deflatePerMessageFrames(ByteString("too large"), splitAt = 4)

          sendWSFrame(Protocol.Opcode.Text, firstFrame, fin = false, mask = true, rsv1 = true)
          sendWSFrame(Protocol.Opcode.Continuation, secondFrame, fin = true, mask = true)
          expectWSCloseFrame(Protocol.CloseCodes.ProtocolError)

          closeNetworkInput()
          expectNetworkClose()
        }
      }

      "apply compression to low-level frame handlers" in Utils.assertAllStagesStopped {
        new TestSetup {
          sendWebSocketRequest("Sec-WebSocket-Extensions: permessage-deflate\r\n")

          val request = expectRequest()
          val upgrade = request.attribute(webSocketUpgrade)
          val response = upgrade.get.asInstanceOf[UpgradeToWebSocketLowLevel].handleFrames(Flow[FrameEvent].map {
            case start @ FrameStart(header, data) =>
              start.copy(header = header.copy(mask = None), data = FrameEventParser.mask(data, header.mask))
            case other => other
          })
          responses.sendNext(response)

          expectResponseWithWipedDate(
            """HTTP/1.1 101 Switching Protocols
              |Upgrade: websocket
              |Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
              |Sec-WebSocket-Extensions: permessage-deflate
              |Server: pekko-http/test
              |Date: XXXX
              |Connection: upgrade
              |
              |""")

          sendWSFrame(Protocol.Opcode.Text, deflatePerMessage(ByteString("compressed low-level message")), fin = true,
            mask = true, rsv1 = true)
          expectCompressedTextFrame("compressed low-level message")

          sendWSCloseFrame(Protocol.CloseCodes.Regular, mask = true)
          expectWSCloseFrame(Protocol.CloseCodes.Regular)

          closeNetworkInput()
          expectNetworkClose()
        }
      }
    }

    "reject invalid WebSocket handshakes" should {
      "missing `Upgrade: websocket` header" in pending
      "missing `Connection: upgrade` header" in pending
      "missing `Sec-WebSocket-Key header" in pending
      "`Sec-WebSocket-Key` with wrong amount of base64 encoded data" in pending
      "missing `Sec-WebSocket-Version` header" in pending
      "unsupported `Sec-WebSocket-Version`" in pending
    }
  }

  class TestSetup extends HttpServerTestSetupBase with WSTestSetupBase {
    implicit def system: ActorSystem = spec.system
    implicit def materializer: Materializer = spec.materializer

    def expectBytes(length: Int): ByteString = netOut.expectBytes(length)
    def expectBytes(bytes: ByteString): Unit = netOut.expectBytes(bytes)

    def sendWebSocketRequest(extraHeader: String): Unit =
      send(
        s"""GET /chat HTTP/1.1
           |Host: server.example.com
           |Upgrade: websocket
           |Connection: Upgrade
           |Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==
           |Origin: http://example.com
           |Sec-WebSocket-Version: 13
           |$extraHeader
           |""".stripMargin)

    def sendWSFrameInTwoNetworkChunks(
        opcode: Protocol.Opcode,
        data: ByteString,
        fin: Boolean,
        mask: Boolean = false,
        rsv1: Boolean = false): Unit = {
      val maskValue = if (mask) Some(0x12345678) else None
      val payload = maskValue match {
        case Some(value) => WSTestUtils.maskedBytes(data, value)._1
        case None        => data
      }
      val header = WSTestUtils.frameHeader(opcode, data.length, fin, maskValue, rsv1 = rsv1)
      val splitAt = math.max(1, payload.length / 2)
      send(header ++ payload.take(splitAt))
      send(payload.drop(splitAt))
    }

    def expectCompressedTextFrame(message: String): Unit = {
      val payload = expectCompressedFrame(Protocol.Opcode.Text, fin = true, rsv1 = true)
      payload should not be ByteString(message)
      inflatePerMessage(payload).utf8String shouldEqual message
    }

    def expectCompressedFrame(opcode: Protocol.Opcode, fin: Boolean, rsv1: Boolean): ByteString = {
      val header = expectBytes(2)
      ((header(0) & Protocol.FIN_MASK) != 0) shouldEqual fin
      ((header(0) & Protocol.RSV1_MASK) != 0) shouldEqual rsv1
      (header(0) & Protocol.OP_MASK) shouldEqual opcode.code
      (header(1) & Protocol.MASK_MASK) shouldEqual 0
      val length = header(1) & 0x7F
      expectBytes(length)
    }

    def expectCompressedContinuationFramesUntilFinal(): Seq[ByteString] = {
      def read(acc: Vector[ByteString]): Vector[ByteString] = {
        val header = expectBytes(2)
        val fin = (header(0) & Protocol.FIN_MASK) != 0
        ((header(0) & Protocol.RSV1_MASK) != 0) shouldEqual false
        (header(0) & Protocol.OP_MASK) shouldEqual Protocol.Opcode.Continuation.code
        (header(1) & Protocol.MASK_MASK) shouldEqual 0
        val length = header(1) & 0x7F
        val payload = expectBytes(length)
        val next = acc :+ payload
        if (fin) next else read(next)
      }

      read(Vector.empty)
    }
  }

  private def deflatePerMessage(data: ByteString): ByteString = {
    val deflater = new Deflater(6, true)
    try {
      deflater.setInput(data.toArray)
      val output = new ByteArrayOutputStream()
      val buffer = new Array[Byte](256)
      var count = deflater.deflate(buffer, 0, buffer.length, Deflater.SYNC_FLUSH)
      while (count > 0) {
        output.write(buffer, 0, count)
        count = deflater.deflate(buffer, 0, buffer.length, Deflater.SYNC_FLUSH)
      }
      val compressed = ByteString.fromArray(output.toByteArray)
      compressed.dropRight(4)
    } finally {
      deflater.end()
    }
  }

  private def deflatePerMessageFrames(data: ByteString, splitAt: Int): (ByteString, ByteString) = {
    val deflater = new Deflater(6, true)
    try {
      val (first, second) = data.splitAt(splitAt)
      (deflateFrame(deflater, first, removeTail = false), deflateFrame(deflater, second, removeTail = true))
    } finally {
      deflater.end()
    }
  }

  private def deflatePerMessageFrames(
      data: ByteString,
      compressionLevel: Int,
      fragmentCount: Int): Seq[ByteString] = {
    val compressed = deflatePerMessage(data, compressionLevel)
    val fragmentLength = compressed.length / (fragmentCount - 1)
    (0 until fragmentCount).map { index =>
      val offset = index * fragmentLength
      val length = if (index == fragmentCount - 1) compressed.length - offset else fragmentLength
      compressed.slice(offset, offset + length)
    }
  }

  private def deflatePerMessagesWithContext(messages: ByteString*): Seq[ByteString] = {
    val deflater = new Deflater(6, true)
    try messages.map(message => deflateFrame(deflater, message, removeTail = true))
    finally {
      deflater.end()
    }
  }

  private def deflatePerMessage(data: ByteString, compressionLevel: Int): ByteString = {
    val deflater = new Deflater(compressionLevel, true)
    try deflateFrame(deflater, data, removeTail = true)
    finally {
      deflater.end()
    }
  }

  private def deflateFrame(deflater: Deflater, data: ByteString, removeTail: Boolean): ByteString = {
    deflater.setInput(data.toArray)
    val output = new ByteArrayOutputStream()
    val buffer = new Array[Byte](256)
    var count = deflater.deflate(buffer, 0, buffer.length, Deflater.SYNC_FLUSH)
    while (count > 0) {
      output.write(buffer, 0, count)
      count = deflater.deflate(buffer, 0, buffer.length, Deflater.SYNC_FLUSH)
    }
    val compressed = ByteString.fromArray(output.toByteArray)
    if (removeTail) compressed.dropRight(4) else compressed
  }

  private def hexToBytes(hex: String): Array[Byte] =
    hex.grouped(2).map(Integer.parseInt(_, 16).toByte).toArray

  private def inflatePerMessageFrames(frames: Seq[ByteString]): ByteString = {
    val inflater = new Inflater(true)
    try {
      frames.zipWithIndex.foldLeft(ByteString.empty) {
        case (inflated, (frame, index)) =>
          val data =
            if (index == frames.length - 1) frame ++ ByteString(0x00, 0x00, 0xFF.toByte, 0xFF.toByte) else frame
          inflated ++ inflateFrame(inflater, data)
      }
    } finally {
      inflater.end()
    }
  }

  private def inflateFrame(inflater: Inflater, data: ByteString): ByteString = {
    inflater.setInput(data.toArray)
    val output = new ByteArrayOutputStream()
    val buffer = new Array[Byte](256)
    var count = inflater.inflate(buffer)
    while (count > 0) {
      output.write(buffer, 0, count)
      count = inflater.inflate(buffer)
    }
    ByteString.fromArray(output.toByteArray)
  }

  private def inflatePerMessage(data: ByteString): ByteString = {
    val inflater = new Inflater(true)
    try {
      inflater.setInput((data ++ ByteString(0x00, 0x00, 0xFF.toByte, 0xFF.toByte)).toArray)
      val output = new ByteArrayOutputStream()
      val buffer = new Array[Byte](256)
      var count = inflater.inflate(buffer)
      while (count > 0) {
        output.write(buffer, 0, count)
        count = inflater.inflate(buffer)
      }
      ByteString.fromArray(output.toByteArray)
    } finally {
      inflater.end()
    }
  }

  private val compressionSettings = WebSocketCompressionSettingsImpl.Disabled.copy(enabled = true)

  private def inflaterFlow(compressionFactory: PerMessageDeflate.CompressionFactory) =
    PerMessageDeflate.createInflaterFlow(
      noContextTakeover = false,
      compressionSettings,
      compressionFactory)

  private def deflaterFlow(compressionFactory: PerMessageDeflate.CompressionFactory) =
    PerMessageDeflate.createDeflaterFlow(
      noContextTakeover = false,
      compressionSettings,
      compressionFactory)

  private final class TrackingCompression extends PerMessageDeflate.CompressionFactory {
    private val inflaterCreated = new AtomicInteger
    private val deflaterCreated = new AtomicInteger
    private val inflaterEnded = new AtomicInteger
    private val deflaterEnded = new AtomicInteger
    private val inflaterEndLatch = new CountDownLatch(1)
    private val deflaterEndLatch = new CountDownLatch(1)

    override def newInflater(): Inflater = {
      inflaterCreated.incrementAndGet()
      new Inflater(true) {
        override def end(): Unit = {
          inflaterEnded.incrementAndGet()
          inflaterEndLatch.countDown()
          super.end()
        }
      }
    }

    override def newDeflater(level: Int): Deflater = {
      deflaterCreated.incrementAndGet()
      new Deflater(level, true) {
        override def end(): Unit = {
          deflaterEnded.incrementAndGet()
          deflaterEndLatch.countDown()
          super.end()
        }
      }
    }

    def awaitAllEnded(): Unit = {
      inflaterCreated.get() should be > 0
      deflaterCreated.get() should be > 0
      inflaterEndLatch.await(3.seconds.toMillis, TimeUnit.MILLISECONDS) shouldEqual true
      deflaterEndLatch.await(3.seconds.toMillis, TimeUnit.MILLISECONDS) shouldEqual true
      inflaterEnded.get() shouldEqual inflaterCreated.get()
      deflaterEnded.get() shouldEqual deflaterCreated.get()
    }
  }
}
