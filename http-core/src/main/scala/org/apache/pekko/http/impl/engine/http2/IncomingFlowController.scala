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

  final case class DiscardedDataReceipt(remainingConnectionWindow: Int, connectionWindowIncrement: Int)

  /** Accounts for discarded DATA without reserving body bytes or changing any stream-level window. */
  def onDiscardedData(
      data: FrameEvent.DataFrameEvent,
      outstandingConnectionLevelWindow: Int,
      totalBufferedData: Int,
      flowController: IncomingFlowController): DiscardedDataReceipt = {
    val remainingWindow = outstandingConnectionLevelWindow - data.sizeInWindow
    val increment =
      if (remainingWindow < 0) 0
      else flowController.onConnectionDataReceived(remainingWindow, totalBufferedData)
    DiscardedDataReceipt(remainingWindow, increment)
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
