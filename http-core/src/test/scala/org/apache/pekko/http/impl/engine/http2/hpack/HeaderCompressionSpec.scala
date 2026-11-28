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

import org.apache.pekko
import pekko.http.impl.engine.http2.FrameEvent
import pekko.http.impl.engine.http2.FrameEvent._
import pekko.http.impl.engine.http2.Http2Protocol.SettingIdentifier
import pekko.http.impl.engine.parsing.HttpHeaderParser
import pekko.http.scaladsl.model.{ DateTime, HttpHeader }
import pekko.http.scaladsl.model.headers.{ RawHeader, `Last-Modified` }
import pekko.http.scaladsl.settings.Http2CommonSettings.HpackPolicyOptions
import pekko.http.scaladsl.settings.{ Http2ClientSettings, Http2CommonSettings, ParserSettings }
import pekko.stream.scaladsl.{ Sink, Source }
import pekko.testkit._
import pekko.util.ByteString

class HeaderCompressionSpec extends PekkoSpec {
  private def setting(size: Int): SettingsAckFrame =
    SettingsAckFrame(Seq(Setting(SettingIdentifier.SETTINGS_HEADER_TABLE_SIZE, size)))

  private def headers(streamId: Int = 1, values: Seq[(String, AnyRef)] = Seq(":method" -> "GET")) =
    ParsedHeadersFrame(streamId, endStream = false, values, None, None)

  private def compress(events: FrameEvent*): Seq[FrameEvent] =
    Await.result(Source(events.toList).via(HeaderCompression).runWith(Sink.seq), 3.seconds.dilated)

  private def hex(frame: FrameEvent): String =
    frame.asInstanceOf[HeadersFrame].headerBlockFragment.map(b => f"$b%02x").mkString

  "Header compression" should {
    "reuse absolute parsed dates but reparse an indexed clock-dependent date" in {
      val relative = headers(1, Seq("last-modified" -> "Sunday, 06-Nov-94 08:49:37 GMT"))
      val absolute = headers(3, Seq("last-modified" -> "Sun, 06 Nov 1994 08:49:37 GMT"))
      val blocks = compress(relative, absolute, relative.copy(streamId = 5), absolute.copy(streamId = 7))
      // Ensure the repeated values are actually indexed, not second literals.
      blocks.drop(2).foreach { block =>
        block.asInstanceOf[HeadersFrame].headerBlockFragment.size shouldBe 1
      }

      val settings = ParserSettings(system)
      val decompression = new HeaderDecompression(HttpHeaderParser(settings, system.log), settings, 8192)
      val decoded = Await.result(Source(blocks.toList).via(decompression).runWith(Sink.seq), 3.seconds.dilated)
        .map(_.asInstanceOf[ParsedHeadersFrame].keyValuePairs.head._2)
      decoded shouldEqual Seq.fill(4)(`Last-Modified`(DateTime(1994, 11, 6, 8, 49, 37)))
      (decoded(0) eq decoded(2)) shouldBe false
      (decoded(1) eq decoded(3)) shouldBe true
    }

    "replace the configured name list with a value-dependent programmatic policy" in {
      val settings = Http2ClientSettings(
        """pekko.http.client.http2.hpack-no-index-headers = ["x-list-only"]""")
      val fields = List(headers(1, Seq("x-label" -> "special")), headers(3, Seq("x-list-only" -> "normal")))
      def representations(settings: Http2ClientSettings): Seq[Int] =
        Await.result(Source(fields).via(HeaderCompression.withPolicy(settings.hpackPolicyOptions)).runWith(Sink.seq),
          3.seconds.dilated).map(_.asInstanceOf[HeadersFrame].headerBlockFragment.head & 0xC0)

      representations(settings) shouldBe Seq(0x40, 0)
      val explicit = settings.withHpackIndexingPolicy((name, value) => name == "x-label" && value == "special")
      representations(explicit.withMaxConcurrentStreams(9)) shouldBe Seq(0, 0x40)
    }

    "match configured names case-insensitively without rewriting the source name" in {
      val names = Set("x-synthetic-trace")
      Http2CommonSettings.matchesNoIndexHeader(names, "x-synthetic-trace") shouldBe true
      Http2CommonSettings.matchesNoIndexHeader(names, "X-Synthetic-Trace") shouldBe true
      Http2CommonSettings.matchesNoIndexHeader(names, "X-Other-Trace") shouldBe false
    }

    "use the opt-in policy only for configured names and keep decoder synchronization" in {
      val selected = "x-synthetic-trace"
      val normal = "x-label"
      val values = Seq(
        headers(1, Seq(selected -> "first")),
        headers(3, Seq(normal -> "stable")),
        headers(5, Seq(selected -> "second")),
        headers(7, Seq(normal -> "stable")))
      val blocks = Await.result(
        Source(values.toList).via(
          HeaderCompression.withPolicy(Some(HpackPolicyOptions(names = Set(selected))))).runWith(Sink.seq),
        3.seconds.dilated)
      blocks.map { frame =>
        frame.asInstanceOf[HeadersFrame].headerBlockFragment.head & 0xC0
      } shouldBe Seq(0x00, 0x40, 0x00, 0x80)

      val settings = ParserSettings(system)
      val decompression = new HeaderDecompression(HttpHeaderParser(settings, system.log), settings, 8192)
      val decoded = Await.result(Source(blocks.toList).via(decompression).runWith(Sink.seq), 3.seconds.dilated)
      decoded.flatMap(_.asInstanceOf[ParsedHeadersFrame].keyValuePairs).map { case (name, value) =>
        name -> value.asInstanceOf[HttpHeader].value
      } shouldBe Seq(selected -> "first", normal -> "stable", selected -> "second", normal -> "stable")

      val defaultBlocks = compress(values: _*)
      (defaultBlocks.head.asInstanceOf[HeadersFrame].headerBlockFragment.head & 0xC0) shouldBe 0x40
    }

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

    "decode successive header blocks after a temporary table shrink and regrowth" in {
      def responseHeaders(streamId: Int, value: String) =
        headers(streamId, Seq(":status" -> "200", "x-example" -> value))

      val blocks = compress(responseHeaders(1, "first"), setting(0), setting(4096),
        responseHeaders(3, "second"), responseHeaders(5, "second")).collect { case frame: HeadersFrame => frame }
      hex(blocks(1)) should startWith("203fe11f")
      hex(blocks(2)) shouldEqual "88be"
      val parserSettings = ParserSettings(system)
      val decompression = new HeaderDecompression(HttpHeaderParser(parserSettings, system.log),
        parserSettings, maxHeaderListSize = 65536)
      val decoded = Await.result(Source(blocks.toList).via(decompression).runWith(Sink.seq), 3.seconds.dilated)

      def expected(streamId: Int, value: String) =
        headers(streamId, Seq(":status" -> "200", "x-example" -> RawHeader("x-example", value)))
      decoded shouldEqual Seq(expected(1, "first"), expected(3, "second"), expected(5, "second"))
    }

    "retain pending updates when empty trailing headers are encoded as DATA" in {
      val end = ParsedHeadersFrame(1, endStream = true, Nil, None, None)
      val result = compress(setting(1024), end, setting(2048), headers(3))
      result(1) shouldEqual DataFrame(1, endStream = true, ByteString.empty)
      hex(result.last) shouldEqual "3fe1073fe10f82"
    }
  }
}
