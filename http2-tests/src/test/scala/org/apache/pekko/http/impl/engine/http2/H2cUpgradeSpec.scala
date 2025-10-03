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
import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko
import pekko.http.impl.engine.http2.Http2Protocol.SettingIdentifier
import pekko.http.impl.engine.http2.Http2Protocol.SettingIdentifier._
import pekko.http.impl.util._
import pekko.http.scaladsl.HttpConnectionContext
import pekko.http.scaladsl.model.{ HttpResponse, StatusCodes }
import pekko.stream.scaladsl.{ Source, Tcp }
import pekko.util.ByteString

class H2cUpgradeSpec extends PekkoSpecWithMaterializer("""
    pekko.http.server.enable-http2 = on
    pekko.http.server.http2.log-frames = on
  """) {

  override implicit val patience: PatienceConfig = PatienceConfig(5.seconds, 5.seconds)

  "An HTTP/1.1 server without TLS that allows upgrading to cleartext HTTP/2" should {
    val binding = Http2().bindAndHandleAsync(
      _ => Future.successful(HttpResponse(status = StatusCodes.ImATeapot)),
      "127.0.0.1",
      port = 0,
      HttpConnectionContext()).futureValue

    // https://tools.ietf.org/html/rfc7540#section-3.2
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

      @tailrec def readToEndOfHeader(currentlyRead: String = ""): String =
        if (currentlyRead.endsWith("\r\n\r\n")) currentlyRead
        else readToEndOfHeader(currentlyRead + frameProbe.plainDataProbe.expectBytes(1).utf8String)

      val headers = readToEndOfHeader()

      headers should include("HTTP/1.1 101 Switching Protocols")
      headers should include("Upgrade: h2c")
      headers should include("Connection: upgrade")

      frameProbe.expectFrameFlagsStreamIdAndPayload(Http2Protocol.FrameType.SETTINGS)
      frameProbe.expectHeaderBlock(1, true)
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
