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
import pekko.http.impl.engine.http2.FrameEvent._
import pekko.http.impl.engine.http2.Http2Protocol.ErrorCode
import pekko.util.ByteString
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class Http2BlueprintSpec extends AnyWordSpec with Matchers {
  "Http2Blueprint" should {
    "match frame type alias (empty-data)" in {
      Http2Blueprint.frameTypeAliasToFrameTypeName("empty-data") shouldEqual
      Some(Http2Blueprint.EmptyDataFrameThrottleName)
    }
    "match frame type alias (empty-data-no-end-stream)" in {
      Http2Blueprint.frameTypeAliasToFrameTypeName("empty-data-no-end-stream") shouldEqual
      Some(Http2Blueprint.EmptyDataFrameNoEndStreamThrottleName)
    }
    "match frame type alias (reset)" in {
      Http2Blueprint.frameTypeAliasToFrameTypeName("reset") shouldEqual
      Some(RstStreamFrame(0, ErrorCode.PROTOCOL_ERROR).frameTypeName)
    }
    "match frame type alias (headers)" in {
      Http2Blueprint.frameTypeAliasToFrameTypeName("headers") shouldEqual
      Some(HeadersFrame(0, true, true, ByteString.empty, None).frameTypeName)
    }
    "match frame type alias (continuation)" in {
      Http2Blueprint.frameTypeAliasToFrameTypeName("continuation") shouldEqual
      Some(ContinuationFrame(0, true, ByteString.empty).frameTypeName)
    }
    "match frame type alias (go-away)" in {
      Http2Blueprint.frameTypeAliasToFrameTypeName("go-away") shouldEqual
      Some(GoAwayFrame(0, ErrorCode.PROTOCOL_ERROR).frameTypeName)
    }
    "match frame type alias (priority)" in {
      Http2Blueprint.frameTypeAliasToFrameTypeName("priority") shouldEqual
      Some(PriorityFrame(0, true, 0, 0).frameTypeName)
    }
    "match frame type alias (ping)" in {
      val rnd = new java.util.Random()
      val bytes = new Array[Byte](8)
      rnd.nextBytes(bytes)
      Http2Blueprint.frameTypeAliasToFrameTypeName("ping") shouldEqual
      Some(PingFrame(true, ByteString(bytes)).frameTypeName)
    }
    "match frame type alias (push-promise)" in {
      Http2Blueprint.frameTypeAliasToFrameTypeName("push-promise") shouldEqual
      Some(PushPromiseFrame(0, true, 0, ByteString.empty).frameTypeName)
    }
    "match frame type alias (window-update)" in {
      Http2Blueprint.frameTypeAliasToFrameTypeName("window-update") shouldEqual
      Some(WindowUpdateFrame(0, 0).frameTypeName)
    }
    "not match empty frame type alias" in {
      Http2Blueprint.frameTypeAliasToFrameTypeName("") shouldEqual None
    }
    "not match unknown frame type alias" in {
      Http2Blueprint.frameTypeAliasToFrameTypeName("unknown") shouldEqual None
    }

    "charge nothing when no frame type is throttled" in {
      Http2Blueprint.frameCost(Set.empty, emptyDataFrame(endStream = false)) shouldEqual 0
      Http2Blueprint.frameCost(Set.empty, rstStreamFrame) shouldEqual 0
    }
    "charge a frame matched by its own frame type name" in {
      val throttled = Set(rstStreamFrame.frameTypeName)
      Http2Blueprint.frameCost(throttled, rstStreamFrame) shouldEqual 1
      Http2Blueprint.frameCost(throttled, emptyDataFrame(endStream = false)) shouldEqual 0
    }
    "charge only the empty DATA frames that do not end the stream (empty-data-no-end-stream)" in {
      val throttled = Set(Http2Blueprint.EmptyDataFrameNoEndStreamThrottleName)
      Http2Blueprint.frameCost(throttled, emptyDataFrame(endStream = false)) shouldEqual 1
      // an empty DATA frame with END_STREAM is how a client closes a request body, so it is left uncharged
      Http2Blueprint.frameCost(throttled, emptyDataFrame(endStream = true)) shouldEqual 0
      Http2Blueprint.frameCost(throttled, dataFrame(endStream = false)) shouldEqual 0
      Http2Blueprint.frameCost(throttled, dataFrame(endStream = true)) shouldEqual 0
    }
    "charge every empty DATA frame (empty-data)" in {
      val throttled = Set(Http2Blueprint.EmptyDataFrameThrottleName)
      Http2Blueprint.frameCost(throttled, emptyDataFrame(endStream = false)) shouldEqual 1
      Http2Blueprint.frameCost(throttled, emptyDataFrame(endStream = true)) shouldEqual 1
      Http2Blueprint.frameCost(throttled, dataFrame(endStream = false)) shouldEqual 0
      Http2Blueprint.frameCost(throttled, dataFrame(endStream = true)) shouldEqual 0
    }
    "charge an empty DATA frame once when both empty DATA aliases are throttled" in {
      val throttled =
        Set(Http2Blueprint.EmptyDataFrameThrottleName, Http2Blueprint.EmptyDataFrameNoEndStreamThrottleName)
      Http2Blueprint.frameCost(throttled, emptyDataFrame(endStream = false)) shouldEqual 1
      Http2Blueprint.frameCost(throttled, emptyDataFrame(endStream = true)) shouldEqual 1
    }
  }

  private def emptyDataFrame(endStream: Boolean) = DataFrame(1, endStream, ByteString.empty)
  private def dataFrame(endStream: Boolean) = DataFrame(1, endStream, ByteString("payload"))
  private def rstStreamFrame = RstStreamFrame(1, ErrorCode.PROTOCOL_ERROR)
}
