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
import pekko.http.impl.engine.http2.Http2Protocol.FrameType
import pekko.http.impl.engine.http2.framing.FrameRenderer
import pekko.util.ByteStringBuilder

import java.nio.ByteOrder

/**
 * This tests the http2 server throttle support for rapid resets.
 */
class Http2ServerEnableFrameTypeThrottleSpec extends Http2SpecWithMaterializer("""
    pekko.http.server.http2.log-frames = on
    pekko.http.server.http2.frame-type-throttle.frame-types = ["reset"]
  """) {
  override val expectSevereLogsOnlyToMatch: Option[String] = Some(
    "HTTP2 connection failed with error [Maximum throttle throughput exceeded.]. Sending INTERNAL_ERROR and closing connection.")

  "The Http/2 server implementation" should {
    "cancel connection during rapid reset attack".inAssertAllStagesStopped(new TestSetup with RequestResponseProbes {
      implicit val bigEndian: ByteOrder = ByteOrder.BIG_ENDIAN
      val bb = new ByteStringBuilder
      bb.putInt(0)
      val rstFrame = FrameRenderer.renderFrame(FrameType.RST_STREAM, ByteFlag.Zero, 1, bb.result())
      val longFrame = Seq.fill(1000)(rstFrame).reduce(_ ++ _)
      network.sendBytes(longFrame)
    })
  }
}
