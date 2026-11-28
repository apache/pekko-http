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

package org.apache.pekko.http.shaded.com.twitter.hpack

import java.io.ByteArrayInputStream
import java.util.HexFormat

import scala.collection.immutable.VectorBuilder

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import org.apache.pekko.http.impl.util.{ ByteStringOutputStream, StringTools }

class HpackEncoderSpec extends AnyWordSpec with Matchers {

  private def hex(bytes: Array[Byte]): String = bytes.map(b => f"$b%02x").mkString

  private def huffmanEncode(bytes: Array[Byte]): Array[Byte] = {
    val encodedLength = Huffman.ENCODER.getEncodedLength(bytes)
    val dst = new Array[Byte](encodedLength)
    Huffman.ENCODER.encode(bytes, dst, 0, encodedLength)
    dst
  }

  private def huffmanEncode(literal: String): Array[Byte] = huffmanEncode(StringTools.asciiStringBytes(literal))

  // indexing off so that the header value is always sent as a string literal, never as a table reference
  private def encodeHeader(name: String, value: String, forceHuffmanOn: Boolean, forceHuffmanOff: Boolean)
      : Array[Byte] = {
    val encoder = new Encoder(4096, false, forceHuffmanOn, forceHuffmanOff)
    val out = new ByteStringOutputStream(16)
    encoder.encodeHeader(out, name, value, false)
    out.takeByteString().toArray
  }

  // decodes one complete header block, keeping the sensitive flag the decoder reports for each field
  private def decodeHeaderFields(decoder: Decoder, bytes: Array[Byte]): Seq[(String, String, Boolean)] = {
    val headers = new VectorBuilder[(String, String, Boolean)]()
    decoder.decode(new ByteArrayInputStream(bytes),
      (name: String, value: String, parsedValue: AnyRef, sensitive: Boolean) => {
        headers += ((name, value, sensitive))
        parsedValue
      })
    withClue("header block truncated:") {
      decoder.endHeaderBlock() shouldBe false
    }
    headers.result()
  }

  private def decodeHeaders(decoder: Decoder, bytes: Array[Byte]): Seq[(String, String)] =
    decodeHeaderFields(decoder, bytes).map { case (name, value, _) => name -> value }

  private def decodeHeaders(bytes: Array[Byte]): Seq[(String, String)] = decodeHeaders(new Decoder(8192, 4096), bytes)

  "HuffmanEncoder" should {
    // the string literals from RFC 7541 Appendix C.4 with their expected Huffman codes
    val rfc7541Examples = Seq(
      "www.example.com" -> "f1e3c2e5f23a6ba0ab90f4ff",
      "no-cache" -> "a8eb10649cbf",
      "custom-key" -> "25a849e95ba97d7f",
      "custom-value" -> "25a849e95bb8e8b4bf")

    "produce the Huffman codes from RFC 7541 Appendix C.4" in {
      rfc7541Examples.foreach {
        case (literal, expected) =>
          withClue(s"literal=$literal") {
            hex(huffmanEncode(literal)) shouldEqual expected
          }
      }
    }

    "report the encoded length that encode actually produces" in {
      rfc7541Examples.foreach {
        case (literal, expected) =>
          withClue(s"literal=$literal") {
            Huffman.ENCODER.getEncodedLength(StringTools.asciiStringBytes(literal)) shouldEqual expected.length / 2
          }
      }
    }

    "encode the empty literal to no bytes" in {
      huffmanEncode("") shouldEqual Array.emptyByteArray
      Huffman.ENCODER.getEncodedLength(Array.emptyByteArray) shouldEqual 0
    }

    "encode every octet with the code for that octet" in {
      // a byte above 0x7F must be looked up as an unsigned symbol, not as a negative array index
      val allOctets = Array.tabulate(256)(_.toByte)
      val coded = huffmanEncode(allOctets)
      val out = new Array[Byte](HuffmanDecoder.maxDecodedLength(coded.length))
      val length = Huffman.DECODER.decode(coded, coded.length, out)
      out.take(length) shouldEqual allOctets
    }

    "write at the given offset and reject an encoded length that does not match the input" in {
      val bytes = StringTools.asciiStringBytes("no-cache")
      val dst = new Array[Byte](10)
      Huffman.ENCODER.encode(bytes, dst, 2, 6)
      hex(dst) shouldEqual "0000a8eb10649cbf0000"
      an[IllegalArgumentException] should be thrownBy Huffman.ENCODER.encode(bytes, new Array[Byte](10), 0, 7)
      an[IndexOutOfBoundsException] should be thrownBy Huffman.ENCODER.encode(bytes, new Array[Byte](10), 5, 6)
    }
  }

  "Decoder" should {
    def checkBlock(
        decoder: Decoder,
        encoded: String,
        expectedHeaders: Seq[(String, String)],
        expectedTable: Seq[(String, String)],
        expectedSize: Int): Unit = {
      decodeHeaders(decoder, HexFormat.of().parseHex(encoded.replace(" ", ""))) shouldEqual expectedHeaders
      decoder.size() shouldBe expectedSize
      Seq.tabulate(decoder.length()) { index =>
        val field = decoder.getHeaderField(index)
        field.name -> field.value
      } shouldEqual expectedTable
    }

    "decode consecutive requests without Huffman coding from RFC 7541 Appendix C.3" in {
      val decoder = new Decoder(8192, 4096)
      val authority = ":authority" -> "www.example.com"
      val cacheControl = "cache-control" -> "no-cache"
      val custom = "custom-key" -> "custom-value"
      val first = Seq(":method" -> "GET", ":scheme" -> "http", ":path" -> "/", authority)

      checkBlock(decoder, "8286 8441 0f77 7777 2e65 7861 6d70 6c65 2e63 6f6d", first, Seq(authority), 57)
      checkBlock(decoder, "8286 84be 5808 6e6f 2d63 6163 6865", first :+ cacheControl, Seq(cacheControl, authority),
        110)
      checkBlock(decoder, "8287 85bf 400a 6375 7374 6f6d 2d6b 6579 0c63 7573 746f 6d2d 7661 6c75 65",
        Seq(":method" -> "GET", ":scheme" -> "https", ":path" -> "/index.html", authority, custom),
        Seq(custom, cacheControl, authority), 164)
    }

    "decode consecutive responses without Huffman coding from RFC 7541 Appendix C.5" in {
      val decoder = new Decoder(8192, 256)
      val cacheControl = "cache-control" -> "private"
      val date = "date" -> "Mon, 21 Oct 2013 20:13:21 GMT"
      val location = "location" -> "https://www.example.com"
      val found = ":status" -> "302"
      val redirect = ":status" -> "307"
      val nextDate = "date" -> "Mon, 21 Oct 2013 20:13:22 GMT"
      val contentEncoding = "content-encoding" -> "gzip"
      val cookie = "set-cookie" -> "foo=ASDJKHQKBZXOQWEOPIUAXQWEOIU; max-age=3600; version=1"

      checkBlock(decoder,
        "4803 3330 3258 0770 7269 7661 7465 611d 4d6f 6e2c 2032 3120 4f63 7420 " +
        "3230 3133 2032 303a 3133 3a32 3120 474d 546e 1768 7474 7073 3a2f 2f77 7777 2e65 7861 6d70 6c65 2e63 6f6d",
        Seq(found, cacheControl, date, location), Seq(location, date, cacheControl, found), 222)
      checkBlock(decoder, "4803 3330 37c1 c0bf",
        Seq(redirect, cacheControl, date, location), Seq(redirect, location, date, cacheControl), 222)
      checkBlock(decoder,
        "88c1 611d 4d6f 6e2c 2032 3120 4f63 7420 3230 3133 2032 303a 3133 3a32 3220 474d " +
        "54c0 5a04 677a 6970 7738 666f 6f3d 4153 444a 4b48 514b 425a 584f 5157 454f 5049 " +
        "5541 5851 5745 4f49 553b 206d 6178 2d61 6765 3d33 3630 303b 2076 6572 7369 6f6e 3d31",
        Seq(":status" -> "200", cacheControl, nextDate, location, contentEncoding, cookie),
        Seq(cookie, contentEncoding, nextDate), 215)
    }

    "empty its dynamic table across consecutive size updates before fresh entries are added" in {
      val encoder = new Encoder(4096)
      val decoder = new Decoder(8192, 4096)
      val out = new ByteStringOutputStream(64)

      def decodeBlock(): Seq[(String, String)] =
        decodeHeaders(decoder, out.takeByteString().toArray)

      encoder.encodeHeader(out, "x-stale", "first", false)
      encoder.encodeHeader(out, "x-stale", "second", false)
      decodeBlock() shouldEqual Seq("x-stale" -> "first", "x-stale" -> "second")
      decoder.length() shouldBe 2

      encoder.setMaxHeaderTableSize(out, 0)
      encoder.setMaxHeaderTableSize(out, 4096)
      encoder.encodeHeader(out, ":status", "200", false)
      decodeBlock() shouldEqual Seq(":status" -> "200")
      decoder.length() shouldBe 0
      decoder.size() shouldBe 0

      encoder.encodeHeader(out, "x-example", "fresh", false)
      decodeBlock() shouldEqual Seq("x-example" -> "fresh")
      decoder.length() shouldBe 1
      decoder.size() shouldBe 46
    }
  }

  "Encoder" should {
    "preserve same-name entries across eviction and table resizing" in {
      val encoder = new Encoder(4096, true, false, true)
      val decoder = new Decoder(8192, 4096)
      val out = new ByteStringOutputStream(32)

      def block(expected: Seq[(String, String)], resize: Option[Int] = None): String = {
        resize.foreach(encoder.setMaxHeaderTableSize(out, _))
        expected.foreach { case (name, value) => encoder.encodeHeader(out, name, value, false) }
        val bytes = out.takeByteString().toArray
        decodeHeaders(decoder, bytes) shouldEqual expected
        // the decoder has to end up with the same dynamic table as the encoder
        decoder.length() shouldEqual encoder.length()
        decoder.size() shouldEqual encoder.size()
        hex(bytes)
      }

      // Exact bytes pin the current indexing policy with Huffman disabled, not the only RFC-valid encoding.
      // Each entry occupies 34 bytes (RFC 7541 section 4.1). At 67 only the newest fits.
      block(Seq("k" -> "x", "k" -> "y")) shouldEqual "40016b01787e0179"
      block(Seq("k" -> "x", "k" -> "y")) shouldEqual "bfbe"
      // 0x3f24: dynamic table size update to 67 (RFC 7541 section 6.3, 5-bit prefix 31 + 36), which evicts k: x.
      // 0x7e: incremental indexing with name index 62, the k: y entry that adding k: x evicts (section 4.4).
      block(Seq("k" -> "x"), Some(67)) shouldEqual "3f247e0178"
      encoder.length() shouldEqual 1
      encoder.size() shouldEqual 34
      // 0x3fc907: dynamic table size update to 1000 (31 + 969, 969 = 0x49 | 0x80 followed by 7)
      block(Seq("k" -> "y"), Some(1000)) shouldEqual "3fc9077e0179"
      encoder.length() shouldEqual 2
      encoder.size() shouldEqual 68
      block(Seq("k" -> "x", "k" -> "y")) shouldEqual "bfbe"
      // 0x20: dynamic table size update to 0, which empties the table; then a literal without indexing, new name
      block(Seq("k" -> "x"), Some(0)) shouldEqual "2000016b0178"
      encoder.length() shouldEqual 0
      encoder.size() shouldEqual 0
      // 0x3fe11f: dynamic table size update to 4096 (31 + 4065, 4065 = 0x61 | 0x80 followed by 31)
      block(Seq("k" -> "y"), Some(4096)) shouldEqual "3fe11f40016b0179"
      encoder.length() shouldEqual 1
      encoder.size() shouldEqual 34
      block(Seq("k" -> "y")) shouldEqual "be"
    }

    "not index a field with a char above 0xFF, which is not written as its own octet" in {
      // The encoder's dynamic table keeps the Strings and counts their chars, while the peer records the octets it
      // received and counts those (RFC 7541 section 4.1). A char above 0xFF is written as '?' and a surrogate pair as
      // a single '?', so the peer would record another value than the String, and fewer octets for the pair.
      Seq("\uD83D\uDE00", "\u20acuro").foreach { value =>
        Seq(true, false).foreach { huffman =>
          withClue(s"value=$value huffman=$huffman") {
            val encoder = new Encoder(4096, true, huffman, !huffman)
            val decoder = new Decoder(8192, 4096)
            val out = new ByteStringOutputStream(32)

            def encode(name: String, value: String): Array[Byte] = {
              encoder.encodeHeader(out, name, value, false)
              out.takeByteString().toArray
            }

            val first = encode("x-a", value)
            val received = StringTools.asciiStringFromBytes(StringTools.asciiStringBytes(value))
            (received should not).equal(value)
            decodeHeaders(decoder, first) shouldEqual Seq("x-a" -> received)
            encoder.length() shouldEqual 0
            decoder.length() shouldEqual 0

            // it is sent as the same literal again, and later entries are still in step
            val second = encode("x-a", value)
            hex(second) shouldEqual hex(first)
            decodeHeaders(decoder, second) shouldEqual Seq("x-a" -> received)
            decodeHeaders(decoder, encode("x-b", "next")) shouldEqual Seq("x-b" -> "next")
            encoder.length() shouldEqual 1
            decoder.length() shouldEqual 1
            decoder.size() shouldEqual encoder.size()
          }
        }
      }
    }

    "never index sensitive values even when an exact table entry already exists" in {
      val encoder = new Encoder(4096, true, false, true)
      val decoder = new Decoder(8192, 4096)
      val out = new ByteStringOutputStream(32)

      def encode(name: String, value: String, sensitive: Boolean): Array[Byte] = {
        encoder.encodeHeader(out, name, value, sensitive)
        out.takeByteString().toArray
      }

      def encodeSensitive(name: String, value: String): String = {
        val bytes = encode(name, value, sensitive = true)
        withClue(s"$name: $value") {
          decodeHeaderFields(decoder, bytes) shouldEqual Seq((name, value, true))
          decoder.length() shouldEqual 1
          decoder.size() shouldEqual 34
        }
        hex(bytes)
      }

      decodeHeaders(decoder, encode("k", "x", sensitive = false)) shouldEqual Seq("k" -> "x")
      // RFC 7541 section 6.2.3: never-indexed literal, indexed name 62, literal value x.
      encodeSensitive("k", "x") shouldEqual "1f2f0178"
      // never-indexed literal, static name index 2, although (:method, GET) is static entry 2
      encodeSensitive(":method", "GET") shouldEqual "1203474554"
      // never-indexed literal with a literal name that is in neither table
      encodeSensitive("secret", "x") shouldEqual "10067365637265740178"
      encoder.length() shouldEqual 1
      encoder.size() shouldEqual 34
      hex(encode("k", "x", sensitive = false)) shouldEqual "be"
    }

    "never index sensitive values when the peer only allows the static table" in {
      val encoder = new Encoder(0, true, false, true)
      val out = new ByteStringOutputStream(32)

      def encode(name: String, value: String, sensitive: Boolean): Array[Byte] = {
        encoder.encodeHeader(out, name, value, sensitive)
        out.takeByteString().toArray
      }

      // without the sensitive flag the exact static table hit is sent as indexed field 2
      hex(encode(":method", "GET", sensitive = false)) shouldEqual "82"
      val bytes = encode(":method", "GET", sensitive = true)
      hex(bytes) shouldEqual "1203474554"
      val decoder = new Decoder(8192, 4096)
      decodeHeaderFields(decoder, bytes) shouldEqual Seq((":method", "GET", true))
      decoder.length() shouldEqual 0
      encoder.length() shouldEqual 0
      encoder.size() shouldEqual 0
    }

    "resolve every static table entry and reject mismatched names or values" in {
      (1 to StaticTable.length).foreach { index =>
        val entry = StaticTable.getEntry(index)
        StaticTable.getIndex(entry.name, entry.value) shouldEqual index
        val firstIndex = StaticTable.getIndex(entry.name)
        firstIndex should be > 0
        firstIndex should be <= index
        StaticTable.getEntry(firstIndex).name shouldEqual entry.name
        StaticTable.getIndex(entry.name, entry.value + "-not-in-the-table") shouldEqual -1
      }
      StaticTable.getIndex("x-not-in-the-table", "https") shouldEqual -1
      StaticTable.getIndex(":scheme", "") shouldEqual -1
      StaticTable.getIndex("content-language", "") shouldEqual 27
    }

    "keep each static header name in a contiguous group and match values case-sensitively" in {
      val names = (1 to StaticTable.length).map(index => StaticTable.getEntry(index).name)
      val groupStarts = names.zipWithIndex.collect {
        case (name, index) if index == 0 || names(index - 1) != name => name
      }
      groupStarts.distinct shouldEqual groupStarts
      StaticTable.getIndex(":method", "get") shouldEqual -1
      StaticTable.getIndex(":METHOD", "GET") shouldEqual -1
    }

    "match the static table values listed in RFC 7541 Appendix A" in {
      StaticTable.length shouldEqual 61
      Seq(
        1 -> (":authority" -> ""),
        2 -> (":method" -> "GET"),
        7 -> (":scheme" -> "https"),
        8 -> (":status" -> "200"),
        16 -> ("accept-encoding" -> "gzip, deflate"),
        32 -> ("cookie" -> ""),
        61 -> ("www-authenticate" -> "")).foreach {
        case (index, (name, value)) =>
          withClue(s"index=$index") {
            val entry = StaticTable.getEntry(index)
            entry.name shouldEqual name
            entry.value shouldEqual value
            StaticTable.getIndex(name, value) shouldEqual index
          }
      }
      an[IndexOutOfBoundsException] should be thrownBy StaticTable.getEntry(0)
      an[IndexOutOfBoundsException] should be thrownBy StaticTable.getEntry(62)
    }

    "encode the value from RFC 7541 Appendix C.4.1 with Huffman coding when it is shorter" in {
      // 0x01: literal without indexing, name index 1 (:authority); 0x8c: Huffman coded, 12 octets
      hex(encodeHeader(":authority", "www.example.com", forceHuffmanOn = false, forceHuffmanOff = false)) shouldEqual
      "018cf1e3c2e5f23a6ba0ab90f4ff"
    }

    "encode the header from RFC 7541 Appendix C.2.1 as a raw literal when Huffman coding is forced off" in {
      // 0x00: literal without indexing, new name; 0x0a/0x0d: raw string literals of 10 and 13 octets
      hex(encodeHeader("custom-key", "custom-header", forceHuffmanOn = false, forceHuffmanOff = true)) shouldEqual
      "000a637573746f6d2d6b65790d637573746f6d2d686561646572"
    }

    "round-trip a value carrying opaque octets above 0x7F in both string literal forms" in {
      // HPACK string literals are opaque octets: a decoder maps each byte to the char of the same value, so an
      // encoder has to map each such char back to that byte, in the raw form as well as the Huffman form
      val value = new String(Array.tabulate(256)(_.toChar))
      Seq(true, false).foreach { huffman =>
        withClue(s"huffman=$huffman") {
          val bytes = encodeHeader("x-opaque", value, forceHuffmanOn = huffman, forceHuffmanOff = !huffman)
          decodeHeaders(bytes) shouldEqual Seq("x-opaque" -> value)
        }
      }
    }

    "round-trip an empty Huffman-encoded header value" in {
      // RFC 7541 Section 5.2 permits an empty Huffman-coded string literal.
      val name = "x-empty"
      val bytes = encodeHeader(name, "", forceHuffmanOn = true, forceHuffmanOff = false)
      // The empty value has no encoded data after its length prefix.
      val valuePrefix = bytes.last
      (valuePrefix & 0x80) shouldEqual 0x80
      (valuePrefix & 0x7F) shouldEqual 0
      decodeHeaders(bytes) shouldEqual Seq(name -> "")
    }

    "use Huffman coding automatically only when it reduces the literal length" in {
      // RFC 7541 allows either representation. These assertions protect Pekko's size-selection policy.
      Seq("" -> false, "a" -> false, "aa" -> false, "aaa" -> true, "|" -> false).foreach {
        case (value, huffman) =>
          withClue(s"value=$value huffman=$huffman") {
            val bytes = encodeHeader(":method", value, forceHuffmanOn = false, forceHuffmanOff = false)
            bytes(0) shouldEqual 0x02.toByte // Literal without indexing, static name :method.
            (bytes(1) & 0x80) shouldEqual (if (huffman) 0x80 else 0)
            decodeHeaders(bytes) shouldEqual Seq(":method" -> value)
          }
      }
    }

    "encode raw and Huffman literal lengths across seven-bit prefix boundaries" in {
      // The wire length counts encoded octets, not input characters (RFC 7541 sections 5.1 and 5.2).
      // Appendix B assigns 'a' a five-bit code, so floor(8 * wireLength / 5) characters Huffman code to wireLength
      // octets. 16510 = 127 + 128^2 - 1 is the longest length whose prefix still fits two continuation bytes.
      val cases = Seq(
        (126, "7e"),
        (127, "7f00"),
        (128, "7f01"),
        (254, "7f7f"),
        (255, "7f8001"),
        (16510, "7fff7f"),
        (16511, "7f808001"))
      cases.foreach {
        case (wireLength, rawPrefix) =>
          Seq(false, true).foreach { huffman =>
            withClue(s"wireLength=$wireLength huffman=$huffman") {
              val value = "a" * (if (huffman) wireLength * 8 / 5 else wireLength)
              if (huffman) {
                withClue("test fixture: Huffman input does not code to the intended wire length:") {
                  Huffman.ENCODER.getEncodedLength(StringTools.asciiStringBytes(value)) shouldEqual wireLength
                }
              }
              val bytes = encodeHeader(":authority", value, forceHuffmanOn = huffman, forceHuffmanOff = !huffman)
              // 0x01: literal without indexing, name index 1 (:authority)
              bytes(0) shouldEqual 0x01.toByte
              val prefixLength = rawPrefix.length / 2
              (bytes(1) & 0x80) shouldEqual (if (huffman) 0x80 else 0)
              // compare the length prefix with the Huffman flag masked off
              val prefix = bytes.slice(1, 1 + prefixLength).updated(0, (bytes(1) & 0x7F).toByte)
              hex(prefix) shouldEqual rawPrefix
              bytes.length shouldEqual 1 + prefixLength + wireLength
              decodeHeaders(new Decoder(32768, 4096), bytes) shouldEqual Seq(":authority" -> value)
            }
          }
      }
    }
  }
}
