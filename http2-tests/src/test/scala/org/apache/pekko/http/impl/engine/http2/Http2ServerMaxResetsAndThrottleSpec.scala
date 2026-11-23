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

import org.apache.pekko
import pekko.http.impl.engine.http2.Http2Protocol.{ ErrorCode, FrameType }
import pekko.stream.impl.io.ByteStringParser.ByteReader

/**
 * This tests the http2 server when both the `max-resets` limit and the frame type throttle for RST_STREAM frames are
 * enabled, and the throttle is exceeded first.
 */
class Http2ServerMaxResetsAndThrottleSpec extends Http2SpecWithMaterializer("""
    pekko.http.server.http2.log-frames = on
    pekko.http.server.http2.frame-type-throttle.frame-types = ["reset"]
    pekko.http.server.http2.max-resets = 400
  """) {
  import Http2ServerMaxResetsSpec._

  // exceeding either limit is provoked by the peer, so it must not be logged at ERROR level
  override def failOnSevereMessages: Boolean = true

  "The Http/2 server implementation" should {
    "close the connection with the throttle's ENHANCE_YOUR_CALM when the frame type throttle is exceeded before max-resets"
      .inAssertAllStagesStopped(new TestSetup {
        override def handlerFlow = neverRespond

        // the throttle allows a burst of 100 RST_STREAM frames, max-resets allows 400
        network.sendBytes(rapidResets(300, network.encodeRequestHeaders))
        // both limits answer with ENHANCE_YOUR_CALM, so the debug data tells which one closed the connection
        val payload = network.expectFramePayload(FrameType.GOAWAY, ByteFlag.Zero, streamId = 0)
        val reader = new ByteReader(payload)
        reader.readIntBE() // last stream id
        ErrorCode.byId(reader.readIntBE()) should ===(ErrorCode.ENHANCE_YOUR_CALM)
        reader.takeAll().utf8String should include("frame-type-throttle rate")
      })
  }
}
