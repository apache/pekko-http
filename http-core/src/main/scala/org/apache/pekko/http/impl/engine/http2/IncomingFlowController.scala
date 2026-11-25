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

package org.apache.pekko.http.impl.engine.http2

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.http.scaladsl.settings.Http2CommonSettings

/** INTERNAL API */
@InternalApi
private[http2] trait IncomingFlowController {
  def onConnectionDataReceived(outstandingConnectionLevelWindow: Int, totalBufferedData: Int): Int

  def onStreamDataDispatched(outstandingConnectionLevelWindow: Int, totalBufferedData: Int,
      outstandingStreamLevelWindow: Int, streamBufferedData: Int): IncomingFlowController.WindowIncrements
}

/** INTERNAL API */
@InternalApi
private[http2] object IncomingFlowController {
  case class WindowIncrements(connectionLevel: Int, streamLevel: Int) {
    require(connectionLevel >= 0, s"Connection-level window increment must be >= 0 but was $connectionLevel")
    require(streamLevel >= 0, s"Stream-level window increment must be >= 0 but was $streamLevel")
  }
  object WindowIncrements {
    val NoIncrements = WindowIncrements(0, 0)
  }

  def apply(settings: Http2CommonSettings): IncomingFlowController =
    if (settings.incomingFlowControlIsolatedStreams == 0)
      default(settings)
    else
      isolated(settings.incomingConnectionLevelBufferSize, Http2CommonSettings.isolatedReceiveStreamBudget(settings))

  /** Refill a share of free connection space, without turning retained bytes into new peer credit. */
  def isolated(connectionBudget: Int, streamBudget: Int): IncomingFlowController =
    new IncomingFlowController {
      def onConnectionDataReceived(outstandingConnectionLevelWindow: Int, totalBufferedData: Int): Int =
        refillFreeSpace(connectionBudget, outstandingConnectionLevelWindow, totalBufferedData)

      def onStreamDataDispatched(outstandingConnectionLevelWindow: Int, totalBufferedData: Int,
          outstandingStreamLevelWindow: Int, streamBufferedData: Int): WindowIncrements =
        WindowIncrements(
          onConnectionDataReceived(outstandingConnectionLevelWindow, totalBufferedData),
          refillFreeSpace(streamBudget, outstandingStreamLevelWindow, streamBufferedData))

      private def refillFreeSpace(budget: Int, outstanding: Int, buffered: Int): Int = {
        val free = budget.toLong - buffered
        // Do not issue credit for space already advertised to the peer. <= also permits progress
        // when only one byte is free and the remaining window has reached zero. The subtractions
        // use Long; nonnegative inputs ensure that the increment is at most the configured Int budget.
        if (outstanding >= 0 && buffered >= 0 && outstanding < free && outstanding <= free / 2)
          (free - outstanding).toInt
        else 0
      }
    }

  def default(settings: Http2CommonSettings): IncomingFlowController =
    default(settings.incomingConnectionLevelBufferSize, settings.incomingStreamLevelBufferSize)

  /** The default scheme sends out WINDOW_UPDATE frames when buffered + outstanding data falls below half of the maximum configured size */
  def default(maximumConnectionLevelWindow: Int, maximumStreamLevelWindow: Int): IncomingFlowController =
    new IncomingFlowController {
      def onConnectionDataReceived(outstandingConnectionLevelWindow: Int, totalBufferedData: Int): Int =
        ifMoreThanHalfUsed(maximumConnectionLevelWindow, outstandingConnectionLevelWindow, totalBufferedData)

      def onStreamDataDispatched(outstandingConnectionLevelWindow: Int, totalBufferedData: Int,
          outstandingStreamLevelWindow: Int, streamBufferedData: Int): WindowIncrements =
        WindowIncrements(
          onConnectionDataReceived(outstandingConnectionLevelWindow, totalBufferedData),
          ifMoreThanHalfUsed(maximumStreamLevelWindow, outstandingStreamLevelWindow, streamBufferedData))

      private def ifMoreThanHalfUsed(max: Int, outstanding: Int, buffered: Int): Int = {
        val totalReservedSpace = outstanding + buffered
        if (totalReservedSpace < max / 2) max - totalReservedSpace
        else 0
      }
    }
}
