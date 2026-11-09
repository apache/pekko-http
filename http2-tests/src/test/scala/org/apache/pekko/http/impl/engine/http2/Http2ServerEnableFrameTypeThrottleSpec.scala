/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.impl.engine.http2

import scala.concurrent.{ Future, Promise }

import org.apache.pekko
import pekko.NotUsed
import pekko.http.impl.engine.http2.FrameEvent.{ HeadersFrame, RstStreamFrame }
import pekko.http.impl.engine.http2.Http2Protocol.ErrorCode
import pekko.http.impl.engine.http2.Http2Protocol.FrameType
import pekko.http.impl.engine.http2.framing.FrameRenderer
import pekko.http.scaladsl.model.{ HttpProtocols, HttpRequest, HttpResponse }
import pekko.stream.impl.io.ByteStringParser.ByteReader
import pekko.stream.scaladsl.Flow
import pekko.util.ByteString

object Http2ServerEnableFrameTypeThrottleSpec {
  private val request = HttpRequest(protocol = HttpProtocols.`HTTP/2.0`, uri = "/foo")

  /**
   * `count` new streams, each opened with a HEADERS frame and immediately reset by the client (CVE-2023-44487).
   * The header blocks are encoded one at a time, since the HPACK encoder is stateful.
   */
  def rapidResets(count: Int, encodeRequestHeaders: HttpRequest => ByteString): ByteString =
    (1 until count * 2 by 2).map { streamId =>
      FrameRenderer.render(
        HeadersFrame(streamId, endStream = true, endHeaders = true, encodeRequestHeaders(request), None)) ++
      FrameRenderer.render(RstStreamFrame(streamId, ErrorCode.CANCEL))
    }.reduce(_ ++ _)

  /** A handler that never responds, so that the server sends no frames of its own for the reset streams */
  def neverRespond: Flow[HttpRequest, HttpResponse, NotUsed] =
    Flow[HttpRequest].mapAsyncUnordered(1000)(_ => Promise[HttpResponse]().future: Future[HttpResponse])
}

/**
 * This tests the http2 server throttle support for rapid resets.
 */
class Http2ServerEnableFrameTypeThrottleSpec extends Http2SpecWithMaterializer("""
    pekko.http.server.http2.log-frames = on
    pekko.http.server.http2.frame-type-throttle.frame-types = ["reset"]
  """) {
  import Http2ServerEnableFrameTypeThrottleSpec._

  // exceeding the throttle is provoked by the peer, so it must not be logged at ERROR level
  override def failOnSevereMessages: Boolean = true

  "The Http/2 server implementation" should {
    "close the connection with ENHANCE_YOUR_CALM during a rapid reset attack".inAssertAllStagesStopped(
      new TestSetup {
        override def handlerFlow = neverRespond

        // more than the default burst of 100, fewer than max-concurrent-streams
        network.sendBytes(rapidResets(200, network.encodeRequestHeaders))
        network.expectGOAWAY()._2 should ===(ErrorCode.ENHANCE_YOUR_CALM)
      })
    "include the configured rate in the GOAWAY debug data when the throttle is exceeded".inAssertAllStagesStopped(
      new TestSetup {
        override def handlerFlow = neverRespond

        network.sendBytes(rapidResets(200, network.encodeRequestHeaders))
        val payload = network.expectFramePayload(FrameType.GOAWAY, ByteFlag.Zero, streamId = 0)
        val reader = new ByteReader(payload)
        reader.readIntBE() // last stream id
        ErrorCode.byId(reader.readIntBE()) should ===(ErrorCode.ENHANCE_YOUR_CALM)
        reader.takeAll().utf8String should include("rate of 100 per 1 second (burst 100)")
      })
  }
}
