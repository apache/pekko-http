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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class IncomingFlowControllerSpec extends AnyWordSpec with Matchers {
  "The default incoming flow controller" should {
    "restore shared receive credit once when local cleanup releases buffered data" in {
      val windowSize = 16
      val controller = IncomingFlowController.default(windowSize, windowSize)
      val outstanding = 4

      controller.onConnectionDataReceived(outstanding, totalBufferedData = 8) shouldBe 0

      // Other streams still retain some buffered data after the cleanup.
      val stillBuffered = 2
      val increment = controller.onConnectionDataReceived(outstanding, stillBuffered)
      outstanding + stillBuffered + increment shouldBe windowSize

      // Applying the new credit must prevent advertising it a second time.
      controller.onConnectionDataReceived(outstanding + increment, stillBuffered) shouldBe 0
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
