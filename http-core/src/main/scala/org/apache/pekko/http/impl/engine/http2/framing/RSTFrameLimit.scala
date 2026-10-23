/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2023 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.impl.engine.http2.framing

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.http.impl.engine.http2.{ FrameEvent, Http2Compliance }
import pekko.http.impl.engine.http2.FrameEvent.RstStreamFrame
import pekko.http.impl.engine.http2.Http2Protocol.ErrorCode
import pekko.http.scaladsl.settings.Http2ServerSettings
import pekko.stream.{ Attributes, FlowShape, Inlet, Outlet }
import pekko.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }

/**
 * INTERNAL API
 *
 * Fails the connection with ENHANCE_YOUR_CALM if the client sends more than `max-resets` RST_STREAM frames
 * within `max-resets-interval`.
 */
@InternalApi
private[http] final class RSTFrameLimit(http2ServerSettings: Http2ServerSettings)
    extends GraphStage[FlowShape[FrameEvent, FrameEvent]] {

  private val maxResets = http2ServerSettings.maxResets
  private val maxResetsIntervalNanos = http2ServerSettings.maxResetsInterval.toNanos

  val in: Inlet[FrameEvent] = Inlet("RSTFrameLimit.in")
  val out: Outlet[FrameEvent] = Outlet("RSTFrameLimit.out")
  override val shape: FlowShape[FrameEvent, FrameEvent] = FlowShape(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler {
      private var rstSeen = false
      private var rstCount = 0
      private var rstSpanStartNanos = 0L

      setHandlers(in, out, this)

      override def onPush(): Unit = {
        grab(in) match {
          case frame: RstStreamFrame =>
            rstCount += 1
            val now = System.nanoTime()
            if (!rstSeen) {
              rstSeen = true
              rstSpanStartNanos = now
              push(out, frame)
            } else if ((now - rstSpanStartNanos) <= maxResetsIntervalNanos) {
              if (rstCount > maxResets) {
                failStage(new Http2Compliance.Http2ProtocolException(
                  ErrorCode.ENHANCE_YOUR_CALM,
                  s"Too many RST frames for this connection. (Configured limit $maxResets/${http2ServerSettings.maxResetsInterval.toCoarsest})"))
              } else {
                push(out, frame)
              }
            } else {
              // outside time window, reset counter
              rstCount = 1
              rstSpanStartNanos = now
              push(out, frame)
            }

          case frame =>
            push(out, frame)
        }
      }

      override def onPull(): Unit = pull(in)
    }
}
