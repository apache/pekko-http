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
import pekko.event.LoggingAdapter
import pekko.http.impl.engine.http2.FrameEvent.ParsedHeadersFrame
import pekko.http.impl.engine.http2.Http2Protocol.ErrorCode
import pekko.http.scaladsl.model.{ AttributeKey, HttpEntity }
import pekko.http.scaladsl.settings.{ Http2ClientSettings, Http2CommonSettings }
import pekko.stream.ClosedShape
import pekko.stream.scaladsl.Source
import pekko.stream.stage.GraphStageLogic
import pekko.testkit.PekkoSpec
import pekko.util.ByteString

class Http2StreamAdmissionSpec extends PekkoSpec {
  private def unexpectedStageOperation(): Nothing = fail("the admission predicate must not operate the stage")

  private def streamHandling(activeCount: Int): Http2StreamHandling =
    new GraphStageLogic(ClosedShape) with Http2StreamHandling {
      override def settings: Http2CommonSettings = Http2ClientSettings(system)
      override def activeStreamCount(): Int = activeCount
      override def isServer: Boolean = false
      override def isUpgraded: Boolean = false
      override def log: LoggingAdapter = system.log
      override def multiplexer: Http2Multiplexer = unexpectedStageOperation()
      override def pushGOAWAY(errorCode: ErrorCode, debug: String): Unit = unexpectedStageOperation()
      override def dispatchSubstream(initialHeaders: ParsedHeadersFrame, data: Either[ByteString, Source[Any, Any]],
          correlationAttributes: Map[AttributeKey[?], ?]): Unit = unexpectedStageOperation()
      override def shouldSendContinue: ParsedHeadersFrame => Boolean = _ => false
      override def wrapTrailingHeaders(headers: ParsedHeadersFrame): Option[HttpEntity.ChunkStreamPart] = None
      override def tryPullSubStreams(): Unit = ()
      override def onAllStreamsClosed(): Unit = ()
    }

  "HTTP/2 peer stream admission" should {
    "accept legal concurrency limits in the upper half of the unsigned range" in {
      val logic = streamHandling(activeCount = 1)
      for (limit <- Seq(0x80000000L, 0xFFFFFFFEL, 0xFFFFFFFFL)) {
        withClue(s"advertised limit $limit: ") {
          logic.setMaxConcurrentStreams(limit.toInt)
          logic.hasCapacityToCreateStreams shouldBe true
        }
      }
    }

    "continue to enforce zero and small concurrency limits" in {
      val noStreams = streamHandling(activeCount = 0)
      noStreams.setMaxConcurrentStreams(0)
      noStreams.hasCapacityToCreateStreams shouldBe false
      noStreams.setMaxConcurrentStreams(1)
      noStreams.hasCapacityToCreateStreams shouldBe true

      val oneStream = streamHandling(activeCount = 1)
      oneStream.setMaxConcurrentStreams(1)
      oneStream.hasCapacityToCreateStreams shouldBe false
      oneStream.setMaxConcurrentStreams(2)
      oneStream.hasCapacityToCreateStreams shouldBe true
    }
  }
}
