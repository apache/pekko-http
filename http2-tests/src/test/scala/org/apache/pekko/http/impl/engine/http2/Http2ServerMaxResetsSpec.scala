/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.impl.engine.http2

import scala.concurrent.{ Future, Promise }
import scala.concurrent.duration._

import org.apache.pekko
import pekko.NotUsed
import pekko.http.impl.engine.http2.FrameEvent.{ HeadersFrame, RstStreamFrame }
import pekko.http.impl.engine.http2.Http2Protocol.ErrorCode
import pekko.http.impl.engine.http2.framing.FrameRenderer
import pekko.http.scaladsl.model.{ HttpProtocols, HttpRequest, HttpResponse }
import pekko.http.scaladsl.settings.ServerSettings
import pekko.stream.scaladsl.Flow
import pekko.util.ByteString

import com.typesafe.config.ConfigFactory

object Http2ServerMaxResetsSpec {
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

  /** A handler that never responds, so that only the frames triggered by the client resets are sent */
  def neverRespond: Flow[HttpRequest, HttpResponse, NotUsed] =
    Flow[HttpRequest].mapAsyncUnordered(1000)(_ => Promise[HttpResponse]().future: Future[HttpResponse])
}

/**
 * This tests the http2 server `max-resets` limit for rapid resets, on its own and together with the frame type throttle.
 */
class Http2ServerMaxResetsSpec extends Http2SpecWithMaterializer("""
    pekko.http.server.http2.log-frames = on
    pekko.http.server.http2.frame-type-throttle.frame-types = []
    pekko.http.server.http2.max-resets = 100
    pekko.http.server.http2.max-resets-interval = 10s
  """) {
  import Http2ServerMaxResetsSpec._

  override def failOnSevereMessages: Boolean = true

  "The Http/2 server implementation" should {
    "disable max-resets by default" in {
      ServerSettings(ConfigFactory.defaultReference()).http2Settings.maxResets should ===(0)
    }

    "close the connection with ENHANCE_YOUR_CALM when the client sends more than max-resets RST_STREAM frames"
      .inAssertAllStagesStopped(new TestSetup {
        override def handlerFlow = neverRespond

        network.sendBytes(rapidResets(101, network.encodeRequestHeaders))
        network.expectGOAWAY()._2 should ===(ErrorCode.ENHANCE_YOUR_CALM)
      })

    "not close the connection when the client sends max-resets RST_STREAM frames"
      .inAssertAllStagesStopped(new TestSetup {
        override def handlerFlow = neverRespond

        network.sendBytes(rapidResets(100, network.encodeRequestHeaders))
        network.toNet.expectNoBytes(200.millis)
      })

    "close the connection with ENHANCE_YOUR_CALM when max-resets is exceeded before the frame type throttle"
      .inAssertAllStagesStopped(new TestSetup {
        override def handlerFlow = neverRespond
        // the throttle allows a burst of 100 RST_STREAM frames, so only max-resets is exceeded here
        override def settings: ServerSettings =
          super.settings.mapHttp2Settings(_.withFrameTypeThrottleFrameTypes(Set("reset")).withMaxResets(20))

        network.sendBytes(rapidResets(30, network.encodeRequestHeaders))
        network.expectGOAWAY()._2 should ===(ErrorCode.ENHANCE_YOUR_CALM)
      })
  }
}
