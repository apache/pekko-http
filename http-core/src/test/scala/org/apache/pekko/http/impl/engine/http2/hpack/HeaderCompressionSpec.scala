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

package org.apache.pekko.http.impl.engine.http2.hpack

import scala.concurrent.Await
import scala.concurrent.duration._

import org.apache.pekko.http.impl.engine.http2.FrameEvent
import org.apache.pekko.http.impl.engine.http2.FrameEvent._
import org.apache.pekko.http.impl.engine.http2.Http2Protocol.SettingIdentifier
import org.apache.pekko.stream.scaladsl.{ Sink, Source }
import org.apache.pekko.testkit.PekkoSpec
import org.apache.pekko.util.ByteString

class HeaderCompressionSpec extends PekkoSpec {
  private def setting(size: Int): SettingsAckFrame =
    SettingsAckFrame(Seq(Setting(SettingIdentifier.SETTINGS_HEADER_TABLE_SIZE, size)))

  private def headers(streamId: Int = 1, values: Seq[(String, AnyRef)] = Seq(":method" -> "GET")) =
    ParsedHeadersFrame(streamId, endStream = false, values, None, None)

  private def compress(events: FrameEvent*): Seq[FrameEvent] =
    Await.result(Source(events.toList).via(HeaderCompression).runWith(Sink.seq), 3.seconds)

  private def hex(frame: FrameEvent): String =
    frame.asInstanceOf[HeadersFrame].headerBlockFragment.map(b => f"$b%02x").mkString

  "Header compression" should {
    "emit only the smallest and final table sizes while preserving every settings acknowledgement" in {
      val updates = Seq(setting(2048), setting(1024), setting(2048))
      val result = compress((updates :+ headers()): _*)
      result.take(3) shouldEqual updates
      // RFC 7541 section 4.2: the intervening minimum preserves eviction state.
      hex(result.last) shouldEqual "3fe1073fe10f82"
    }

    "coalesce multiple size settings within one acknowledgement" in {
      val ack = SettingsAckFrame(Seq(2048, 1024, 2048).map(size =>
        Setting(SettingIdentifier.SETTINGS_HEADER_TABLE_SIZE, size)))
      val result = compress(ack, headers())
      result.head shouldEqual ack
      hex(result.last) shouldEqual "3fe1073fe10f82"
    }

    "flush pending sizes in an empty non-terminal header block" in {
      val result = compress(setting(1024), headers(1, Nil), headers(3))
      val blocks = result.collect { case frame: HeadersFrame => hex(frame) }
      blocks shouldEqual Seq("3fe107", "82")
    }

    "omit redundant updates and forget the previous block's minimum" in {
      val result = compress(setting(4096), setting(4096), headers(),
        setting(2048), setting(1024), headers(3), setting(3072), headers(5))
      val blocks = result.collect { case frame: HeadersFrame => hex(frame) }
      blocks shouldEqual Seq("82", "3fe10782", "3fe11782")
    }

    "evict at the smallest size before regrowing the table" in {
      val values = Seq("k" -> "x")
      val result = compress(headers(1, values), setting(0), setting(1024), setting(4096),
        headers(3, values), headers(5, values))
      val blocks = result.collect { case frame: HeadersFrame => hex(frame) }
      // Regrowth must not resurrect the old entry: the next block is a literal, then it can be indexed again.
      blocks shouldEqual Seq("40016b0178", "203fe11f40016b0178", "be")
    }

    "retain pending updates when empty trailing headers are encoded as DATA" in {
      val end = ParsedHeadersFrame(1, endStream = true, Nil, None, None)
      val result = compress(setting(1024), end, setting(2048), headers(3))
      result(1) shouldEqual DataFrame(1, endStream = true, ByteString.empty)
      hex(result.last) shouldEqual "3fe1073fe10f82"
    }
  }
}
