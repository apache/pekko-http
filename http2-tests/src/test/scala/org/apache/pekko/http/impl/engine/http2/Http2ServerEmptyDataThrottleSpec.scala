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
import pekko.util.ByteString

/**
 * Tests that DATA frames carrying no payload can be throttled. They consume no flow-control window, so unlike
 * data-carrying frames their number is not bounded by flow control.
 */
class Http2ServerEmptyDataThrottleSpec extends Http2SpecWithMaterializer("""
    pekko.http.server.http2.log-frames = on
    pekko.http.server.http2.frame-type-throttle.frame-types = ["empty-data"]
  """) {
  override val expectSevereLogsOnlyToMatch: Option[String] = Some(
    "HTTP2 connection failed with error [Maximum throttle throughput exceeded.]. Sending INTERNAL_ERROR and closing connection.")

  "The Http/2 server implementation" should {
    "cancel connection when flooded with empty DATA frames".inAssertAllStagesStopped(
      new TestSetup with RequestResponseProbes {
        val emptyDataFrame = FrameRenderer.renderFrame(FrameType.DATA, ByteFlag.Zero, 1, ByteString.empty)
        network.sendBytes(Seq.fill(1000)(emptyDataFrame).reduce(_ ++ _))
      })
  }
}
