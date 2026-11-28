/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.http.impl.engine.http2

import org.apache.pekko
import pekko.util.ByteString

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class IncomingFlowControllerSpec extends AnyWordSpec with Matchers {
  "The default incoming flow controller" should {
    "credit discarded padding while keeping the entity bytes reserved" in {
      val body = ByteString("data")
      val frame = FrameEvent.PaddedDataFrame(1, endStream = false, body, paddingSize = 3)
      frame.payload shouldBe body
      frame.sizeInWindow shouldBe 7

      val targetWindow = 32
      val controller = IncomingFlowController.default(targetWindow, targetWindow)
      val remainingWindow = 18 - frame.sizeInWindow

      controller.onStreamDataDispatched(
        outstandingConnectionLevelWindow = remainingWindow,
        totalBufferedData = frame.payload.length,
        outstandingStreamLevelWindow = remainingWindow,
        streamBufferedData = frame.payload.length) shouldBe IncomingFlowController.WindowIncrements(17, 17)

      // The same state after the batch is applied must not advertise the bytes a second time.
      controller.onStreamDataDispatched(
        remainingWindow + 17, body.length, remainingWindow + 17, body.length) shouldBe
      IncomingFlowController.WindowIncrements.NoIncrements
    }

    "account for padding-only DATA without inventing any entity bytes" in {
      val controller = IncomingFlowController.default(32, 32)
      for (endStream <- Seq(false, true)) {
        val frame = FrameEvent.PaddedDataFrame(1, endStream, ByteString.empty, paddingSize = 3)
        frame.payload shouldBe ByteString.empty
        frame.sizeInWindow shouldBe 3
        val remainingWindow = 18 - frame.sizeInWindow
        controller.onConnectionDataReceived(remainingWindow, frame.payload.length) shouldBe 17
        controller.onStreamDataDispatched(remainingWindow, 0, remainingWindow, 0) shouldBe
        IncomingFlowController.WindowIncrements(17, 17)
      }
    }

    "restore shared receive credit after two streams consume data without restoring either stream window" in {
      val windowSize = Http2Protocol.InitialWindowSize
      val controller = IncomingFlowController.default(windowSize, windowSize)
      val bytesPerStream = 20 * 1024
      val outstandingConnectionWindow = windowSize - 2 * bytesPerStream
      val outstandingStreamWindow = windowSize - bytesPerStream

      // The first stream has consumed its data, but the second stream's data is still buffered.
      controller.onStreamDataDispatched(
        outstandingConnectionLevelWindow = outstandingConnectionWindow,
        totalBufferedData = bytesPerStream,
        outstandingStreamLevelWindow = outstandingStreamWindow,
        streamBufferedData = 0) shouldBe IncomingFlowController.WindowIncrements.NoIncrements

      // Consumption by the second stream releases enough shared capacity, but its own credit is still sufficient.
      controller.onStreamDataDispatched(
        outstandingConnectionLevelWindow = outstandingConnectionWindow,
        totalBufferedData = 0,
        outstandingStreamLevelWindow = outstandingStreamWindow,
        streamBufferedData = 0) shouldBe IncomingFlowController.WindowIncrements(2 * bytesPerStream, 0)
    }
  }
}
