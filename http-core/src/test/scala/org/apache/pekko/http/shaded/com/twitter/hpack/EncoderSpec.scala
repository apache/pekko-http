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

import scala.collection.mutable.ListBuffer

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class EncoderSpec extends AnyWordSpec with Matchers {

  private class Peers {
    val encoder = new Encoder(4096)
    val decoder = new Decoder(8192, 4096)

    def encode(name: String, value: String): Array[Byte] = {
      val out = new ByteArrayOutputStream
      encoder.encodeHeader(out, name, value, false)
      out.toByteArray
    }

    def decode(block: Array[Byte]): List[(String, String)] = {
      val headers = ListBuffer.empty[(String, String)]
      decoder.decode(new ByteArrayInputStream(block),
        new HeaderListener {
          override def addHeader(name: String, value: String, parsed: AnyRef, sensitive: Boolean): AnyRef = {
            headers += name -> value
            parsed
          }
        })
      decoder.endHeaderBlock()
      headers.toList
    }

    /** RFC 7541 section 4.1: both sides have to agree on the entries and on their size, which counts octets */
    def tablesShouldAgree(): Unit = {
      encoder.length() shouldEqual decoder.length()
      encoder.size() shouldEqual decoder.size()
      (0 until encoder.length()).foreach { index =>
        encoder.getHeaderField(index).name shouldEqual decoder.getHeaderField(index).name
        encoder.getHeaderField(index).value shouldEqual decoder.getHeaderField(index).value
      }
    }
  }

  "The HPACK Encoder" should {
    "index a literal whose chars are all written as their own octet" in new Peers {
      decode(encode("x-a", "café")) shouldEqual List("x-a" -> "café")
      encoder.length() shouldEqual 1
      encoder.size() shouldEqual 3 + 4 + HeaderField.HEADER_ENTRY_OVERHEAD
      tablesShouldAgree()

      // the second time it is sent as an index into the dynamic table
      val indexed = encode("x-a", "café")
      indexed.length shouldEqual 1
      decode(indexed) shouldEqual List("x-a" -> "café")
      tablesShouldAgree()
    }

    // A char above 0xFF is not written as it is, so the peer records something else than the String, and for a
    // surrogate pair in a literal that is not Huffman coded also fewer octets than the String has chars.
    Seq(
      "a surrogate pair" -> "😀",
      "a surrogate pair in a Huffman coded value" -> ("a" * 40 + "😀"),
      "a char above 0xFF" -> "€uro",
      "a char above 0xFF in a Huffman coded value" -> ("a" * 40 + "€")).foreach {
      case (description, value) =>
        s"not index a literal with $description" in new Peers {
          val first = encode("x-a", value)
          val decoded = decode(first)
          decoded.map(_._1) shouldEqual List("x-a")
          (decoded.head._2 should not).equal(value)
          encoder.length() shouldEqual 0
          tablesShouldAgree()

          // it is sent as the same literal again, and later entries are still in step
          encode("x-a", value).toSeq shouldEqual first.toSeq
          decode(encode("x-b", "next")) shouldEqual List("x-b" -> "next")
          encoder.length() shouldEqual 1
          tablesShouldAgree()
        }
    }
  }
}
