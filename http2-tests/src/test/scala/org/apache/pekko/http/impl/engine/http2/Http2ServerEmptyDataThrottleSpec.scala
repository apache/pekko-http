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
import pekko.http.impl.engine.http2.FrameEvent.HeadersFrame
import pekko.http.impl.engine.http2.Http2Protocol.FrameType
import pekko.http.impl.engine.http2.framing.FrameRenderer
import pekko.http.scaladsl.model.{ HttpMethods, HttpRequest }
import pekko.util.ByteString

/**
 * Tests the opt-in "empty-data" throttle, which charges every DATA frame carrying no payload - including the one
 * with END_STREAM that a client sends to close a request body. The default throttles only the empty DATA frames
 * that do not end their stream, see [[Http2ServerEmptyDataNoEndStreamThrottleSpec]].
 */
class Http2ServerEmptyDataThrottleSpec extends Http2SpecWithMaterializer("""
    pekko.http.server.http2.log-frames = on
    pekko.http.server.http2.frame-type-throttle.frame-types = ["empty-data"]
  """) {
  // exceeding the throttle is provoked by the peer, so it must not be logged at ERROR level
  override def failOnSevereMessages: Boolean = true

  "The Http/2 server implementation" should {
    "cancel connection when flooded with empty DATA frames that end their stream".inAssertAllStagesStopped(
      new TestSetup with RequestResponseProbes {
        // each request is opened with HEADERS and closed with an empty DATA frame carrying END_STREAM, which only
        // "empty-data" charges; 200 streams stays below max-concurrent-streams but exceeds the burst of 100
        val requests = (0 until 200).map { i =>
          val streamId = 2 * i + 1
          val headerBlock = network.encodeRequestHeaders(HttpRequest(HttpMethods.POST))
          FrameRenderer.render(HeadersFrame(streamId, endStream = false, endHeaders = true, headerBlock, None)) ++
          FrameRenderer.renderFrame(FrameType.DATA, Http2Protocol.Flags.END_STREAM, streamId, ByteString.empty)
        }
        network.sendBytes(requests.reduce(_ ++ _))
        // the server may send frames, such as WINDOW_UPDATE, for the frames it processed before the throttle failed
        network.expectGOAWAYSkippingOtherFrames() should ===(Http2Protocol.ErrorCode.ENHANCE_YOUR_CALM)
      })
  }
}
