/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.impl.engine.http2

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.concurrent.{ Future, Promise }

import org.apache.pekko
import pekko.http.impl.engine.http2.FrameEvent.{ Setting, SettingsAckFrame, SettingsFrame }
import pekko.http.impl.engine.http2.Http2Protocol.SettingIdentifier
import pekko.http.impl.engine.http2.Http2Protocol.SettingIdentifier._
import pekko.http.impl.engine.http2.framing.FrameRenderer
import pekko.http.impl.util._
import pekko.http.scaladsl.HttpConnectionContext
import pekko.http.scaladsl.model.{ HttpMethods, HttpRequest, HttpResponse, StatusCodes }
import pekko.stream.scaladsl.{ Source, Tcp }
import pekko.stream.testkit.TestPublisher
import pekko.util.ByteString

class H2cUpgradeSpec extends PekkoSpecWithMaterializer("""
    pekko.http.server.http2.log-frames = on
  """) {

  override implicit val patience: PatienceConfig = PatienceConfig(5.seconds, 5.seconds)

  "An HTTP/1.1 server without TLS that allows upgrading to cleartext HTTP/2" should {
    val binding = Http2().bindAndHandleAsync(
      _ => Future.successful(HttpResponse(status = StatusCodes.ImATeapot)),
      "127.0.0.1",
      port = 0,
      HttpConnectionContext()).futureValue

    // Legacy h2c Upgrade from RFC 7540 section 3.2. RFC 9113 sections 11.1 and 11.2 mark it obsolete.
    "respond with HTTP 101 and no initial settings" in {
      val settings = encode(Nil)
      // Settings are placed directly in the HTTP header, without any framing,
      // so 'no settings' just yields the empty string:
      settings shouldBe ""
      testWith(settings)
    }

    "respond with HTTP 101 and some initial settings" in {
      // real-world settings example as used by curl
      val settings = encode(Seq(
        (SETTINGS_MAX_CONCURRENT_STREAMS, 100),
        (SETTINGS_INITIAL_WINDOW_SIZE, 33554432),
        (SETTINGS_ENABLE_PUSH, 0)))
      settings shouldBe "AAMAAABkAAQCAAAAAAIAAAAA"
      testWith(settings)
    }

    for ((name, connectionFields) <- Seq(
        "a combined field" -> Seq("Connection: Upgrade, HTTP2-Settings"),
        "separate fields" -> Seq("Connection: Upgrade", "Connection: HTTP2-Settings"))) {
      s"accept h2c upgrade with required Connection options in $name" in {
        val settings = encode(Seq(SETTINGS_ENABLE_PUSH -> 0))
        val receivedRequest = Promise[HttpRequest]()
        val applicationResponse = Promise[HttpResponse]()
        val binding = Http2().bindAndHandleAsync(
          request => {
            val _ = receivedRequest.trySuccess(request)
            applicationResponse.future
          },
          "127.0.0.1",
          port = 0,
          HttpConnectionContext()).awaitResult(5.seconds)

        val outgoing = TestPublisher.probe[ByteString]()
        val network = new Http2FrameHpackSupport {
          override val frameProbeDelegate: Http2FrameProbe = Http2FrameProbe()
          override def sendBytes(bytes: ByteString): Unit = outgoing.sendNext(bytes)
        }
        try {
          Source.fromPublisher(outgoing)
            .via(Tcp(system).outgoingConnection(binding.localAddress.getHostString, binding.localAddress.getPort))
            .runWith(network.sink)

          try {
            val host = s"${binding.localAddress.getHostString}:${binding.localAddress.getPort}"
            val request = Seq("GET /upgrade HTTP/1.1", s"Host: $host", "Upgrade: h2c",
              s"HTTP2-Settings: $settings") ++ connectionFields ++ Seq("", "")
            network.sendBytes(ByteString(request.mkString("\r\n")))
            expectSwitchingProtocols(network)

            network.sendBytes(Http2Protocol.ClientConnectionPreface ++
              FrameRenderer.render(SettingsFrame(Seq(Setting(SETTINGS_ENABLE_PUSH, 0)))))
            network.expectSETTINGS()
            network.sendFrame(SettingsAckFrame(Nil))
            network.expectSettingsAck()

            val handled = receivedRequest.future.awaitResult(5.seconds)
            handled.method shouldBe HttpMethods.GET
            handled.uri.path.toString shouldBe "/upgrade"
            applicationResponse.success(HttpResponse())
            network.expectDecodedResponseHEADERSPairs(streamId = 1) should contain(":status" -> "200")
          } finally network.plainDataProbe.cancel()
        } finally binding.terminate(1.second).awaitResult(5.seconds)
      }
    }

    def testWith(settings: String) = {
      val upgradeRequest =
        s"""GET / HTTP/1.1
Host: localhost
Upgrade: h2c
HTTP2-Settings: $settings

"""
      val frameProbe = Http2FrameProbe()

      Source.single(ByteString(upgradeRequest)).concat(Source.maybe)
        .via(Tcp(system).outgoingConnection(binding.localAddress.getHostName, binding.localAddress.getPort))
        .runWith(frameProbe.sink)

      expectSwitchingProtocols(frameProbe)
      frameProbe.expectFrameFlagsStreamIdAndPayload(Http2Protocol.FrameType.SETTINGS)
      frameProbe.expectHeaderBlock(1, true)
    }

    def expectSwitchingProtocols(frameProbe: Http2FrameProbe): Unit = {
      @tailrec def readToEndOfHeader(currentlyRead: String = ""): String =
        if (currentlyRead.endsWith("\r\n\r\n")) currentlyRead
        else readToEndOfHeader(currentlyRead + frameProbe.plainDataProbe.expectBytes(1).utf8String)

      val headers = readToEndOfHeader()

      headers should include("HTTP/1.1 101 Switching Protocols")
      headers should include("Upgrade: h2c")
      headers should include("Connection: upgrade")
    }
  }

  def encode(settings: Seq[(SettingIdentifier, Int)]): String = {
    val bytes = settings.flatMap {
      case (id, value) => Seq(
          0.toByte,
          id.id.toByte,
          (value >> 24).toByte,
          (value >> 16).toByte,
          (value >> 8).toByte,
          value.toByte)
    }
    ByteString.fromArrayUnsafe(bytes.toArray).encodeBase64.utf8String
  }
}
