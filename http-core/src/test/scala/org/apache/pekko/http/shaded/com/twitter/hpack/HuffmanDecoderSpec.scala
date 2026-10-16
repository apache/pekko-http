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

import java.io.IOException
import java.nio.charset.StandardCharsets

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class HuffmanDecoderSpec extends AnyWordSpec with Matchers {

  private def bytes(hex: String): Array[Byte] = hex.grouped(2).map(Integer.parseInt(_, 16).toByte).toArray

  private def decode(coded: Array[Byte]): Array[Byte] = {
    val out = new Array[Byte](HuffmanDecoder.maxDecodedLength(coded.length))
    val length = Huffman.DECODER.decode(coded, coded.length, out)
    out.take(length)
  }

  /** Huffman codes the octets straight from the HPACK table, independently of HuffmanEncoder. */
  private def encode(octets: Array[Byte]): Array[Byte] = {
    val out = new java.io.ByteArrayOutputStream()
    var current = 0L
    var bits = 0
    octets.foreach { octet =>
      val symbol = octet & 0xFF
      val nbits = HpackUtil.HUFFMAN_CODE_LENGTHS(symbol)
      current = (current << nbits) | HpackUtil.HUFFMAN_CODES(symbol)
      bits += nbits
      while (bits >= 8) {
        bits -= 8
        out.write((current >> bits).toInt)
      }
    }
    if (bits > 0) out.write(((current << (8 - bits)) | (0xFF >>> bits)).toInt)
    out.toByteArray
  }

  "HuffmanDecoder" should {
    "decode the string literals from RFC 7541 Appendix C.4" in {
      Seq(
        "f1e3c2e5f23a6ba0ab90f4ff" -> "www.example.com",
        "a8eb10649cbf" -> "no-cache",
        "25a849e95ba97d7f" -> "custom-key",
        "25a849e95bb8e8b4bf" -> "custom-value").foreach {
        case (coded, expected) =>
          new String(decode(bytes(coded)), StandardCharsets.ISO_8859_1) shouldEqual expected
      }
    }

    "decode every octet to the symbol of the same value" in {
      val allOctets = Array.tabulate(256)(_.toByte)
      decode(encode(allOctets)) shouldEqual allOctets
    }

    "decode nothing from an empty literal" in {
      decode(Array.emptyByteArray) shouldEqual Array.emptyByteArray
    }

    "fill the output bound exactly for a literal made of the shortest codes" in {
      // '0' has the 5-bit code 00000, so 40 coded bytes carry 64 symbols, the maxDecodedLength bound
      val zeros = Array.fill[Byte](64)('0')
      val coded = encode(zeros)
      coded.length shouldEqual 40
      HuffmanDecoder.maxDecodedLength(coded.length) shouldEqual 64
      decode(coded) shouldEqual zeros
    }

    "only read the given number of bytes of the input" in {
      val coded = bytes("a8eb10649cbf") ++ Array[Byte](0, 0, 0)
      val out = new Array[Byte](HuffmanDecoder.maxDecodedLength(6))
      val length = Huffman.DECODER.decode(coded, 6, out)
      new String(out, 0, length, StandardCharsets.ISO_8859_1) shouldEqual "no-cache"
    }

    "reject a literal containing the EOS symbol" in {
      (the[IOException] thrownBy decode(bytes("ffffffff"))).getMessage shouldEqual "EOS Decoded"
    }

    "reject padding that is not the most significant bits of the EOS code" in {
      // 00000 decodes '0' and leaves three zero padding bits, which must have been ones
      (the[IOException] thrownBy decode(bytes("00"))).getMessage shouldEqual "Invalid Padding"
    }
  }
}
