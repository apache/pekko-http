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

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }

import scala.collection.immutable.VectorBuilder

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import org.apache.pekko.http.impl.util.StringTools

class HpackEncoderSpec extends AnyWordSpec with Matchers {

  private def hex(bytes: Array[Byte]): String = bytes.map(b => f"$b%02x").mkString

  private def huffmanEncode(bytes: Array[Byte]): Array[Byte] =
    Huffman.ENCODER.encode(bytes, Huffman.ENCODER.getEncodedLength(bytes))

  private def huffmanEncode(literal: String): Array[Byte] = huffmanEncode(StringTools.asciiStringBytes(literal))

  // indexing off so that the header value is always sent as a string literal, never as a table reference
  private def encodeHeader(name: String, value: String, forceHuffmanOn: Boolean, forceHuffmanOff: Boolean): Array[Byte] = {
    val encoder = new Encoder(4096, false, forceHuffmanOn, forceHuffmanOff)
    val out = new ByteArrayOutputStream()
    encoder.encodeHeader(out, name, value, false)
    out.toByteArray
  }

  private def decodeHeaders(bytes: Array[Byte]): Seq[(String, String)] = {
    val decoder = new Decoder(8192, 4096)
    val headers = new VectorBuilder[(String, String)]()
    decoder.decode(new ByteArrayInputStream(bytes),
      (name: String, value: String, parsedValue: AnyRef, _: Boolean) => {
        headers += name -> value
        parsedValue
      })
    headers.result()
  }

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
          hex(huffmanEncode(literal)) shouldEqual expected
      }
    }

    "report the encoded length that encode actually produces" in {
      rfc7541Examples.foreach {
        case (literal, expected) =>
          Huffman.ENCODER.getEncodedLength(StringTools.asciiStringBytes(literal)) shouldEqual expected.length / 2
      }
    }

    "encode the empty literal to no bytes" in {
      huffmanEncode("") shouldEqual Array.emptyByteArray
      Huffman.ENCODER.getEncodedLength(Array.emptyByteArray) shouldEqual 0
    }

    "encode every octet with the code for that octet" in {
      // a byte above 0x7F must be looked up as an unsigned symbol, not as a negative array index
      val allOctets = Array.tabulate(256)(_.toByte)
      Huffman.DECODER.decode(huffmanEncode(allOctets)) shouldEqual allOctets
    }

    "reject an encoded length that does not match the input" in {
      val bytes = StringTools.asciiStringBytes("no-cache")
      an[IllegalArgumentException] should be thrownBy Huffman.ENCODER.encode(bytes, 7)
      an[ArrayIndexOutOfBoundsException] should be thrownBy Huffman.ENCODER.encode(bytes, 5)
    }
  }

  "Encoder" should {
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
        val bytes = encodeHeader("x-opaque", value, forceHuffmanOn = huffman, forceHuffmanOff = !huffman)
        decodeHeaders(bytes) shouldEqual Seq("x-opaque" -> value)
      }
    }
  }
}
