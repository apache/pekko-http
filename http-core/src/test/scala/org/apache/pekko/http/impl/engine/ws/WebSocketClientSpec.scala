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

import java.util.Random

import org.apache.pekko
import pekko.NotUsed
import pekko.http.scaladsl.model.ws.{ InvalidUpgradeResponse, WebSocketUpgradeResponse }
import pekko.stream.ClosedShape
import pekko.stream.TLSProtocol._

import scala.concurrent.duration._
import pekko.http.scaladsl.settings.ClientConnectionSettings
import pekko.http.scaladsl.Http
import pekko.http.scaladsl.model.headers.{
  `Sec-WebSocket-Extensions`,
  `User-Agent`,
  HttpOrigin,
  Origin,
  ProductVersion,
  RawHeader,
  WebSocketExtension
}
import pekko.http.scaladsl.model.ws._
import pekko.http.scaladsl.model.Uri
import pekko.stream.scaladsl._
import pekko.stream.testkit.{ TestPublisher, TestSubscriber, Utils }
import pekko.util.ByteString
import pekko.testkit._
import pekko.http.impl.util._

class WebSocketClientSpec extends PekkoSpecWithMaterializer("pekko.http.client.websocket.log-frames = on") {
  "The client-side WebSocket implementation should" should {
    "establish a websocket connection when the user requests it" in new EstablishedConnectionSetup with ClientEchoes

    "send the caller's Origin header when establishing a WebSocket connection" in new TestSetup with ClientEchoes {
      override def clientLayer: Http.WebSocketClientLayer =
        Http(system).webSocketClientLayer(
          WebSocketRequest(targetUri, extraHeaders = List(Origin(HttpOrigin("https://app.example.com")))),
          settings = settings)

      val endOfHeaders = "\r\n\r\n"
      val request = new StringBuilder
      while (request.length < 4096 && !request.endsWith(endOfHeaders))
        request.append(netOut.expectByte().toChar)
      request.result() should endWith(endOfHeaders)
      request.result().split("\r\n").toList
        .filter(_.regionMatches(true, 0, "Origin:", 0, "Origin:".length))
        .map(_.drop("Origin:".length).trim) shouldEqual List("https://app.example.com")
      sendWireData(UpgradeResponseBytes)

      sendWSFrame(Protocol.Opcode.Text, ByteString("echo"), fin = true)
      expectMaskedFrameOnNetwork(Protocol.Opcode.Text, ByteString("echo"), fin = true)

      sendWSCloseFrame(Protocol.CloseCodes.Regular)
      expectMaskedCloseFrame(Protocol.CloseCodes.Regular)
      closeNetworkInput()
      expectNetworkClose()
    }

    Seq(
      "ws://example.org/path%20with%20spaces?q=two%20words" -> "/path%20with%20spaces?q=two%20words",
      "ws://example.org" -> "/",
      "ws://example.org?q=two%20words" -> "/?q=two%20words").foreach { case (uri, target) =>
      s"render the handshake resource name for $uri" in {
        Utils.assertAllStagesStopped {
          new TestSetup with ClientProbes {
            override def targetUri: Uri = Uri(uri)
            try {
              val expected = UpgradeRequestBytes.utf8String.replace("GET /ws HTTP/1.1", s"GET $target HTTP/1.1")
              expectWireData(ByteString(expected))
              sendWireData(UpgradeResponseBytes)
              response.awaitResult(1.second.dilated) shouldBe a[ValidUpgrade]
            } finally {
              messagesOut.sendComplete()
              messagesIn.cancel()
              netOut.cancel()
              netIn.sendComplete()
            }
          }
        }
      }
    }

    "complete the upgrade result when the peer closes before responding" in {
      Utils.assertAllStagesStopped {
        new TestSetup with ClientProbes {
          try {
            expectWireData(UpgradeRequestBytes)
            messagesIn.request(1)
            closeNetworkInput()
            // The independent application sink proves that clean network EOF has propagated.
            // Keep the application publisher open until after observing the upgrade result.
            messagesIn.expectComplete()
            response.failed.awaitResult(1.second.dilated).getMessage shouldEqual
            "Connection closed before WebSocket upgrade completed."
          } finally {
            messagesOut.sendComplete()
            messagesIn.cancel()
            netOut.cancel()
          }
        }
      }
    }

    "retain a successful upgrade result after the peer closes" in {
      Utils.assertAllStagesStopped {
        new TestSetup with ClientProbes {
          try {
            expectWireData(UpgradeRequestBytes)
            messagesIn.request(1)
            sendWireData(UpgradeResponseBytes)
            val upgrade = response.awaitResult(1.second.dilated)
            upgrade shouldBe a[ValidUpgrade]
            closeNetworkInput()
            messagesIn.expectComplete()
            response.awaitResult(1.second.dilated) shouldEqual upgrade
          } finally {
            messagesOut.sendComplete()
            messagesIn.cancel()
            netOut.cancel()
          }
        }
      }
    }

    "use the configured close wait when the peer delays its close response" in {
      Utils.assertAllStagesStopped {
        new TestSetup with ClientProbes {
          override def settings = {
            val defaults = super.settings
            defaults.withWebsocketSettings(defaults.websocketSettings.withCloseTimeout(100.millis))
          }
          try {
            expectWireData(UpgradeRequestBytes)
            messagesIn.request(1)
            sendWireData(UpgradeResponseBytes)
            response.awaitResult(1.second.dilated) shouldBe a[ValidUpgrade]
            // An idle healthy connection must not start its close wait at materialization.
            netOut.expectNoBytes(200.millis)
            messagesOut.sendComplete()
            expectMaskedFrameOnNetwork(Protocol.Opcode.Close, ByteString(3, 232), fin = true)
            // This comfortably allows the configured wait, but not the historical three seconds.
            netOut.within(1.second) {
              expectNetworkClose()
            }
          } finally {
            messagesOut.sendComplete()
            messagesIn.cancel()
            netIn.sendComplete()
            netOut.cancel()
          }
        }
      }
    }

    "share the close wait between the peer close frame and transport closure" in {
      Utils.assertAllStagesStopped {
        new TestSetup with ClientProbes {
          override def settings = {
            val defaults = super.settings
            defaults.withWebsocketSettings(defaults.websocketSettings.withCloseTimeout(4.seconds.dilated))
          }
          try {
            expectWireData(UpgradeRequestBytes)
            messagesIn.request(1)
            sendWireData(UpgradeResponseBytes)
            response.awaitResult(1.second.dilated) shouldBe a[ValidUpgrade]
            messagesOut.sendComplete()
            expectMaskedFrameOnNetwork(Protocol.Opcode.Close, ByteString(3, 232), fin = true)

            // Spend half the budget waiting for the server's normal close reply.
            netOut.expectNoBytes(2.seconds.dilated)
            sendWSCloseFrame(Protocol.CloseCodes.Regular)
            netOut.within(3.seconds) {
              messagesIn.expectComplete()
              // Keep network input open: the close reply must neither close the transport immediately
              // nor restart a full four-second wait. Allow a second of scheduling margin either way.
              netOut.expectNoBytes(200.millis.dilated)
              expectNetworkClose()
            }
            netIn.expectCancellation()
          } finally {
            messagesOut.sendComplete()
            messagesIn.cancel()
            netIn.sendComplete()
            netOut.cancel()
          }
        }
      }
    }

    "exchange text with a configured graceful-close wait" in new EstablishedConnectionSetup with ClientEchoes {
      override def settings: ClientConnectionSettings = {
        val defaults = super.settings
        defaults.withWebsocketSettings(defaults.websocketSettings.withCloseTimeout(750.millis))
      }
      sendWSFrame(Protocol.Opcode.Text, ByteString("Message 1"), fin = true)
      expectMaskedFrameOnNetwork(Protocol.Opcode.Text, ByteString("Message 1"), fin = true)
    }

    "establish connection with case insensitive header values" in new TestSetup with ClientEchoes {
      expectWireData(UpgradeRequestBytes)
      sendWireData("""HTTP/1.1 101 Switching Protocols
                     |Upgrade: wEbSOckET
                     |Sec-WebSocket-Accept: ujmZX4KXZqjwy6vi1aQFH5p4Ygk=
                     |Server: pekko-http/test
                     |Sec-WebSocket-Version: 13
                     |Connection: upgrade
                     |
                     |""")

      sendWSFrame(Protocol.Opcode.Text, ByteString("Message 1"), fin = true)
      expectMaskedFrameOnNetwork(Protocol.Opcode.Text, ByteString("Message 1"), fin = true)
    }

    Seq(
      "keep-alive, UpGrAdE",
      "upgrade, keep-alive",
      "keep-alive, custom, upgrade",
      "keep-alive, upgrade, custom").foreach { connection =>
      s"establish connection with Connection tokens [$connection]" in new TestSetup with ClientEchoes {
        expectWireData(UpgradeRequestBytes)
        sendWireData(s"""HTTP/1.1 101 Switching Protocols
                       |Upgrade: websocket
                       |Sec-WebSocket-Accept: ujmZX4KXZqjwy6vi1aQFH5p4Ygk=
                       |Connection: $connection
                       |
                       |""")

        sendWSFrame(Protocol.Opcode.Text, ByteString("Message 1"), fin = true)
        expectMaskedFrameOnNetwork(Protocol.Opcode.Text, ByteString("Message 1"), fin = true)
      }
    }

    "establish connection with header values sent in parts" in new TestSetup with ClientEchoes {
      expectWireData(UpgradeRequestBytes)
      sendWireData("""HTTP/1.1 101 """)
      sendWireData("""Switching Protocols
                     |Upgrade: websocket
                     |Sec-WebSocket-Accept: ujmZX4KXZqjwy6vi1aQFH5p4Ygk=
                     |Server: pekko-http/test
                     |Sec-WebSocket-Version: 13
                     |Connection: upgrade
                     |
                     |""")

      sendWSFrame(Protocol.Opcode.Text, ByteString("Message 1"), fin = true)
      expectMaskedFrameOnNetwork(Protocol.Opcode.Text, ByteString("Message 1"), fin = true)
    }

    for ((extensionValue, label) <- Seq("" -> "empty", " \t " -> "whitespace-only")) {
      s"establish a connection when the extension response value is $label" in new TestSetup with ClientEchoes {
        expectWireData(UpgradeRequestBytes)
        sendWireData(s"""HTTP/1.1 101 Switching Protocols
                       |Upgrade: websocket
                       |Sec-WebSocket-Accept: ujmZX4KXZqjwy6vi1aQFH5p4Ygk=
                       |Sec-WebSocket-Extensions: $extensionValue
                       |Connection: upgrade
                       |
                       |""")

        sendWSFrame(Protocol.Opcode.Text, ByteString("Message 1"), fin = true)
        expectMaskedFrameOnNetwork(Protocol.Opcode.Text, ByteString("Message 1"), fin = true)
      }
    }

    for ((offeredHeader, label) <- Seq(
        RawHeader("Sec-WebSocket-Extensions", "x-custom; level=1") -> "raw",
        `Sec-WebSocket-Extensions`(List(WebSocketExtension("x-custom", Map("level" -> "1")))) -> "typed")) {
      s"establish a connection when the server selects an extension offered through $label extraHeaders" in
      new TestSetup
        with ClientEchoes {
        override def clientLayer: Http.WebSocketClientLayer =
          Http(system).webSocketClientLayer(
            WebSocketRequest(targetUri,
              extraHeaders = List(offeredHeader)),
            settings = settings)

        expectWireData(ByteString(UpgradeRequestBytes.utf8String.replace(
          "Host: example.org", "Sec-WebSocket-Extensions: x-custom; level=1\r\nHost: example.org")))
        sendWireData("""HTTP/1.1 101 Switching Protocols
                     |Upgrade: websocket
                     |Sec-WebSocket-Accept: ujmZX4KXZqjwy6vi1aQFH5p4Ygk=
                     |Sec-WebSocket-Extensions: X-Custom; level=1
                     |Connection: upgrade
                     |
                     |""")

        sendWSFrame(Protocol.Opcode.Text, ByteString("Message 1"), fin = true)
        expectMaskedFrameOnNetwork(Protocol.Opcode.Text, ByteString("Message 1"), fin = true)
      }
    }

    "reject invalid handshakes" should {
      for (extension <- List("permessage-deflate", "unknown-extension")) {
        s"an unrequested extension: $extension" in new TestSetup with ClientEchoes {
          expectWireData(UpgradeRequestBytes)
          sendWireData(s"""HTTP/1.1 101 Switching Protocols
                       |Upgrade: websocket
                       |Sec-WebSocket-Accept: ujmZX4KXZqjwy6vi1aQFH5p4Ygk=
                       |Sec-WebSocket-Extensions: $extension
                       |Connection: upgrade
                       |
                       |""")

          expectInvalidUpgradeResponseCause(
            "WebSocket server at ws://example.org/ws returned response with an unsupported WebSocket extension.")
          expectNetworkAbort()
        }

      }

      "an extension other than the one offered through extraHeaders" in new TestSetup with ClientEchoes {
        override def clientLayer: Http.WebSocketClientLayer =
          Http(system).webSocketClientLayer(
            WebSocketRequest(targetUri,
              extraHeaders = List(RawHeader("Sec-WebSocket-Extensions", "x-custom; level=1"))),
            settings = settings)

        expectWireData(ByteString(UpgradeRequestBytes.utf8String.replace(
          "Host: example.org", "Sec-WebSocket-Extensions: x-custom; level=1\r\nHost: example.org")))
        sendWireData("""HTTP/1.1 101 Switching Protocols
                       |Upgrade: websocket
                       |Sec-WebSocket-Accept: ujmZX4KXZqjwy6vi1aQFH5p4Ygk=
                       |Sec-WebSocket-Extensions: permessage-deflate
                       |Connection: upgrade
                       |
                       |""")

        expectInvalidUpgradeResponseCause(
          "WebSocket server at ws://example.org/ws returned response with an unsupported WebSocket extension.")
        expectNetworkAbort()
      }

      Seq("keep-alive", "keep-alive, xupgrade", "upgraded").foreach { connection =>
        s"Connection tokens without upgrade [$connection]" in new TestSetup with ClientEchoes {
          expectWireData(UpgradeRequestBytes)
          sendWireData(s"""HTTP/1.1 101 Switching Protocols
                         |Upgrade: websocket
                         |Sec-WebSocket-Accept: ujmZX4KXZqjwy6vi1aQFH5p4Ygk=
                         |Connection: $connection
                         |
                         |""")

          expectNetworkAbort()
          expectInvalidUpgradeResponseCause(
            s"WebSocket server at ws://example.org/ws returned response that was missing required `Connection: upgrade` header. Found `Connection: $connection` with the wrong value.")
        }
      }
      "other status code" in new TestSetup with ClientEchoes {
        expectWireData(UpgradeRequestBytes)

        sendWireData(
          """HTTP/1.1 404 Not Found
            |Server: pekko-http/test
            |Content-Length: 0
            |
            |""")

        expectNetworkAbort()
        expectInvalidUpgradeResponseCause(
          "WebSocket server at ws://example.org/ws returned unexpected status code: 404 Not Found")
      }
      "missing Sec-WebSocket-Accept hash" in new TestSetup with ClientEchoes {
        expectWireData(UpgradeRequestBytes)

        sendWireData(
          """HTTP/1.1 101 Switching Protocols
            |Upgrade: websocket
            |Sec-WebSocket-Version: 13
            |Server: pekko-http/test
            |Connection: upgrade
            |
            |""")

        expectNetworkAbort()
        expectInvalidUpgradeResponseCause(
          "WebSocket server at ws://example.org/ws returned response that was missing required `Sec-WebSocket-Accept` header.")
      }
      "wrong Sec-WebSocket-Accept hash" in new TestSetup with ClientEchoes {
        expectWireData(UpgradeRequestBytes)

        sendWireData(
          """HTTP/1.1 101 Switching Protocols
            |Upgrade: websocket
            |Sec-WebSocket-Accept: s3pPLMBiTxhZRbK+xOo=
            |Sec-WebSocket-Version: 13
            |Server: pekko-http/test
            |Connection: upgrade
            |
            |""")

        expectNetworkAbort()
        expectInvalidUpgradeResponseCause(
          "WebSocket server at ws://example.org/ws returned response with invalid `Sec-WebSocket-Accept` header.")
      }
      "missing `Upgrade` header" in new TestSetup with ClientEchoes {
        expectWireData(UpgradeRequestBytes)

        sendWireData(
          """HTTP/1.1 101 Switching Protocols
            |Sec-WebSocket-Accept: ujmZX4KXZqjwy6vi1aQFH5p4Ygk=
            |Sec-WebSocket-Version: 13
            |Server: pekko-http/test
            |Connection: upgrade
            |
            |""")

        expectNetworkAbort()
        expectInvalidUpgradeResponseCause(
          "WebSocket server at ws://example.org/ws returned response that was missing required `Upgrade` header.")
      }
      "missing `Connection: upgrade` header" in new TestSetup with ClientEchoes {
        expectWireData(UpgradeRequestBytes)

        sendWireData(
          """HTTP/1.1 101 Switching Protocols
            |Upgrade: websocket
            |Sec-WebSocket-Accept: ujmZX4KXZqjwy6vi1aQFH5p4Ygk=
            |Sec-WebSocket-Version: 13
            |Server: pekko-http/test
            |
            |""")

        expectNetworkAbort()
        expectInvalidUpgradeResponseCause(
          "WebSocket server at ws://example.org/ws returned response that was missing required `Connection` header.")
      }
    }

    "don't send out frames before handshake was finished successfully" in new TestSetup {
      def clientImplementation: Flow[Message, Message, NotUsed] =
        Flow.fromSinkAndSourceMat(Sink.ignore, Source.single(TextMessage("fast message")))(Keep.none)

      expectWireData(UpgradeRequestBytes)
      expectNoWireData()

      sendWireData(UpgradeResponseBytes)
      expectMaskedFrameOnNetwork(Protocol.Opcode.Text, ByteString("fast message"), fin = true)

      expectMaskedCloseFrame(Protocol.CloseCodes.Regular)
      sendWSCloseFrame(Protocol.CloseCodes.Regular)

      closeNetworkInput()
      expectNetworkClose()
    }
    "receive first frame in same chunk as HTTP upgrade response" in new TestSetup with ClientProbes {
      expectWireData(UpgradeRequestBytes)

      val firstFrame = WSTestUtils.frame(Protocol.Opcode.Text, ByteString("fast"), fin = true, mask = false)
      sendWireData(UpgradeResponseBytes ++ firstFrame)

      messagesIn.requestNext(TextMessage("fast"))
    }

    "manual scenario client sends first" in new EstablishedConnectionSetup with ClientProbes {
      messagesOut.sendNext(TextMessage("Message 1"))

      expectMaskedFrameOnNetwork(Protocol.Opcode.Text, ByteString("Message 1"), fin = true)

      sendWSFrame(Protocol.Opcode.Binary, ByteString("Response"), fin = true, mask = false)

      messagesIn.requestNext(BinaryMessage(ByteString("Response")))
    }
    "client echoes scenario" in new EstablishedConnectionSetup with ClientEchoes {
      sendWSFrame(Protocol.Opcode.Text, ByteString("Message 1"), fin = true)
      expectMaskedFrameOnNetwork(Protocol.Opcode.Text, ByteString("Message 1"), fin = true)
      sendWSFrame(Protocol.Opcode.Text, ByteString("Message 2"), fin = true)
      expectMaskedFrameOnNetwork(Protocol.Opcode.Text, ByteString("Message 2"), fin = true)
      sendWSFrame(Protocol.Opcode.Text, ByteString("Message 3"), fin = true)
      expectMaskedFrameOnNetwork(Protocol.Opcode.Text, ByteString("Message 3"), fin = true)
      sendWSFrame(Protocol.Opcode.Text, ByteString("Message 4"), fin = true)
      expectMaskedFrameOnNetwork(Protocol.Opcode.Text, ByteString("Message 4"), fin = true)
      sendWSFrame(Protocol.Opcode.Text, ByteString("Message 5"), fin = true)
      expectMaskedFrameOnNetwork(Protocol.Opcode.Text, ByteString("Message 5"), fin = true)

      sendWSCloseFrame(Protocol.CloseCodes.Regular)
      expectMaskedCloseFrame(Protocol.CloseCodes.Regular)

      closeNetworkInput()
      expectNetworkClose()
    }
    "support subprotocols" should {
      "accept if server supports subprotocol" in new TestSetup with ClientEchoes {
        override protected def requestedSubProtocol: Option[String] = Some("v2")

        expectWireData(
          """GET /ws HTTP/1.1
          |Upgrade: websocket
          |Connection: upgrade
          |Sec-WebSocket-Key: YLQguzhR2dR6y5M9vnA5mw==
          |Sec-WebSocket-Version: 13
          |Sec-WebSocket-Protocol: v2
          |Host: example.org
          |User-Agent: pekko-http/test
          |
          |""")
        sendWireData(
          """HTTP/1.1 101 Switching Protocols
            |Upgrade: websocket
            |Sec-WebSocket-Accept: ujmZX4KXZqjwy6vi1aQFH5p4Ygk=
            |Sec-WebSocket-Version: 13
            |Server: pekko-http/test
            |Connection: upgrade
            |Sec-WebSocket-Protocol: v2
            |
            |""")

        sendWSFrame(Protocol.Opcode.Text, ByteString("Message 1"), fin = true)
        expectMaskedFrameOnNetwork(Protocol.Opcode.Text, ByteString("Message 1"), fin = true)
      }
      "multiple subprotocols" should {
        "accept if server supports subprotocol" in new TestSetup with ClientEchoes {
          override protected def requestedSubProtocol: Option[String] = Some("v2, v3")

          expectWireData(
            """GET /ws HTTP/1.1
              |Upgrade: websocket
              |Connection: upgrade
              |Sec-WebSocket-Key: YLQguzhR2dR6y5M9vnA5mw==
              |Sec-WebSocket-Version: 13
              |Sec-WebSocket-Protocol: v2, v3
              |Host: example.org
              |User-Agent: pekko-http/test
              |
              |""")
          sendWireData(
            """HTTP/1.1 101 Switching Protocols
              |Upgrade: websocket
              |Sec-WebSocket-Accept: ujmZX4KXZqjwy6vi1aQFH5p4Ygk=
              |Sec-WebSocket-Version: 13
              |Server: pekko-http/test
              |Connection: upgrade
              |Sec-WebSocket-Protocol: v3
              |
              |""")

          sendWSFrame(Protocol.Opcode.Text, ByteString("Message 1"), fin = true)
          expectMaskedFrameOnNetwork(Protocol.Opcode.Text, ByteString("Message 1"), fin = true)
        }
      }
      "send error on user flow if server doesn't support subprotocol" should {
        "if no protocol was selected" in new TestSetup with ClientProbes {
          override protected def requestedSubProtocol: Option[String] = Some("v2")

          expectWireData(
            """GET /ws HTTP/1.1
              |Upgrade: websocket
              |Connection: upgrade
              |Sec-WebSocket-Key: YLQguzhR2dR6y5M9vnA5mw==
              |Sec-WebSocket-Version: 13
              |Sec-WebSocket-Protocol: v2
              |Host: example.org
              |User-Agent: pekko-http/test
              |
              |""")
          sendWireData(
            """HTTP/1.1 101 Switching Protocols
              |Upgrade: websocket
              |Sec-WebSocket-Accept: ujmZX4KXZqjwy6vi1aQFH5p4Ygk=
              |Sec-WebSocket-Version: 13
              |Server: pekko-http/test
              |Connection: upgrade
              |
              |""")

          expectNetworkAbort()
          expectInvalidUpgradeResponseCause(
            "WebSocket server at ws://example.org/ws returned response that indicated that the given subprotocol was not supported. (client supported: v2, server supported: None)")
        }
        "if different protocol was selected" in new TestSetup with ClientProbes {
          override protected def requestedSubProtocol: Option[String] = Some("v2")

          expectWireData(
            """GET /ws HTTP/1.1
              |Upgrade: websocket
              |Connection: upgrade
              |Sec-WebSocket-Key: YLQguzhR2dR6y5M9vnA5mw==
              |Sec-WebSocket-Version: 13
              |Sec-WebSocket-Protocol: v2
              |Host: example.org
              |User-Agent: pekko-http/test
              |
              |""")
          sendWireData(
            """HTTP/1.1 101 Switching Protocols
              |Upgrade: websocket
              |Sec-WebSocket-Accept: ujmZX4KXZqjwy6vi1aQFH5p4Ygk=
              |Sec-WebSocket-Protocol: v3
              |Sec-WebSocket-Version: 13
              |Server: pekko-http/test
              |Connection: upgrade
              |
              |""")

          expectNetworkAbort()
          expectInvalidUpgradeResponseCause(
            "WebSocket server at ws://example.org/ws returned response that indicated that the given subprotocol was not supported. (client supported: v2, server supported: Some(v3))")
        }
      }
    }
  }

  def UpgradeRequestBytes = ByteString {
    """GET /ws HTTP/1.1
      |Upgrade: websocket
      |Connection: upgrade
      |Sec-WebSocket-Key: YLQguzhR2dR6y5M9vnA5mw==
      |Sec-WebSocket-Version: 13
      |Host: example.org
      |User-Agent: pekko-http/test
      |
      |""".stripMarginWithNewline("\r\n")
  }

  def UpgradeResponseBytes = ByteString {
    """HTTP/1.1 101 Switching Protocols
      |Upgrade: websocket
      |Sec-WebSocket-Accept: ujmZX4KXZqjwy6vi1aQFH5p4Ygk=
      |Server: pekko-http/test
      |Sec-WebSocket-Version: 13
      |Connection: upgrade
      |
      |""".stripMarginWithNewline("\r\n")
  }

  abstract class EstablishedConnectionSetup extends TestSetup {
    expectWireData(UpgradeRequestBytes)
    sendWireData(UpgradeResponseBytes)
  }

  abstract class TestSetup extends WSTestSetupBase {
    protected def noMsgTimeout: FiniteDuration = 100.millis.dilated
    protected def clientImplementation: Flow[Message, Message, NotUsed]
    protected def requestedSubProtocol: Option[String] = None

    val random = new Random(0)
    def settings = ClientConnectionSettings(system)
      .withUserAgentHeader(Some(`User-Agent`(List(ProductVersion("pekko-http", "test")))))
      .withWebsocketRandomFactory(() => random)

    def targetUri: Uri = "ws://example.org/ws"

    def clientLayer: Http.WebSocketClientLayer =
      Http(system).webSocketClientLayer(
        WebSocketRequest(targetUri, subprotocol = requestedSubProtocol),
        settings = settings)

    val (netOut, netIn, response) = {
      val netOut = ByteStringSinkProbe()
      val netIn = TestPublisher.probe[ByteString]()

      val graph =
        RunnableGraph.fromGraph(GraphDSL.createGraph(clientLayer) { implicit b => client =>
          import GraphDSL.Implicits._
          Source.fromPublisher(netIn) ~> Flow[ByteString].map(SessionBytes(null, _))             ~> client.in2
          client.out1                 ~> Flow[SslTlsOutbound].collect { case SendBytes(x) => x } ~> netOut.sink
          client.out2                 ~> clientImplementation                                    ~> client.in1
          ClosedShape
        })

      val response = graph.run()

      (netOut, netIn, response)
    }
    def expectBytes(length: Int): ByteString = netOut.expectBytes(length)
    def expectBytes(bytes: ByteString): Unit = netOut.expectBytes(bytes)

    def wipeDate(string: String) =
      string.fastSplit('\n').map {
        case s if s.startsWith("Date:") => "Date: XXXX\r"
        case s                          => s
      }.mkString("\n")

    def sendWireData(data: String): Unit = sendWireData(ByteString(data.stripMarginWithNewline("\r\n"), "ASCII"))
    def sendWireData(data: ByteString): Unit = netIn.sendNext(data)

    def send(bytes: ByteString): Unit = sendWireData(bytes)

    def expectWireData(s: String) =
      netOut.expectUtf8EncodedString(s.stripMarginWithNewline("\r\n"))
    def expectWireData(bs: ByteString) = netOut.expectBytes(bs)
    def expectNoWireData() = netOut.expectNoBytes(noMsgTimeout)

    def expectNetworkClose(): Unit = {
      netOut.request(1)
      netOut.expectComplete()
    }
    def expectNetworkAbort(): Unit = netOut.expectError()
    def closeNetworkInput(): Unit = netIn.sendComplete()

    def expectResponse(response: WebSocketUpgradeResponse): Unit =
      expectInvalidUpgradeResponse() shouldEqual response
    def expectInvalidUpgradeResponseCause(expected: String): Unit =
      expectInvalidUpgradeResponse().cause shouldEqual expected

    import pekko.http.impl.util._
    def expectInvalidUpgradeResponse(): InvalidUpgradeResponse =
      response.awaitResult(1.second.dilated).asInstanceOf[InvalidUpgradeResponse]
  }

  trait ClientEchoes extends TestSetup {
    override def clientImplementation: Flow[Message, Message, NotUsed] = echoServer
    def echoServer: Flow[Message, Message, NotUsed] = Flow[Message]
  }
  trait ClientProbes extends TestSetup {
    lazy val messagesOut = TestPublisher.probe[Message]()
    lazy val messagesIn = TestSubscriber.probe[Message]()

    override def clientImplementation: Flow[Message, Message, NotUsed] =
      Flow.fromSinkAndSourceMat(Sink.fromSubscriber(messagesIn), Source.fromPublisher(messagesOut))(Keep.none)
  }
}
