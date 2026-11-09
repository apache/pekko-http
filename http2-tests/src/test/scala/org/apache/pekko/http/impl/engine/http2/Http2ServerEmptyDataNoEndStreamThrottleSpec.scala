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
import pekko.http.impl.engine.http2.Http2Protocol.FrameType
import pekko.http.impl.engine.http2.framing.FrameRenderer
import pekko.http.scaladsl.model.{ HttpMethods, HttpRequest }
import pekko.util.ByteString

/**
 * Tests that DATA frames which carry no payload and do not end their stream are throttled by default. They consume
 * no flow-control window, so unlike data-carrying frames their number is not bounded by flow control, and unlike the
 * empty DATA frame that carries END_STREAM they do not close anything.
 *
 * The config deliberately does not set `frame-type-throttle.frame-types`, so this covers the default.
 */
class Http2ServerEmptyDataNoEndStreamThrottleSpec extends Http2SpecWithMaterializer("""
    pekko.http.server.http2.log-frames = on
  """) {
  // exceeding the throttle is provoked by the peer, so it must not be logged at ERROR level
  override def failOnSevereMessages: Boolean = true

  "The Http/2 server implementation" should {
    "cancel connection when flooded with empty DATA frames that do not end the stream".inAssertAllStagesStopped(
      new TestSetup with RequestResponseProbes {
        // open the stream first, so the flood arrives on an open stream rather than an idle one
        network.sendRequestHEADERS(1, HttpRequest(HttpMethods.POST), endStream = false)
        val emptyDataFrame = FrameRenderer.renderFrame(FrameType.DATA, ByteFlag.Zero, 1, ByteString.empty)
        network.sendBytes(Seq.fill(1000)(emptyDataFrame).reduce(_ ++ _))
        // the server may send frames, such as WINDOW_UPDATE, for the frames it processed before the throttle failed
        network.expectGOAWAYSkippingOtherFrames() should ===(Http2Protocol.ErrorCode.ENHANCE_YOUR_CALM)
      })
  }
}
