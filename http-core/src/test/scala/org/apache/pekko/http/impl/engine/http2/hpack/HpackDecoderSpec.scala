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

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream, IOException, InputStream, SequenceInputStream }
import scala.jdk.CollectionConverters._

import scala.collection.mutable.ListBuffer

import org.apache.pekko.http.shaded.com.twitter.hpack.{ Decoder, Encoder, HeaderListener }

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class HpackDecoderSpec extends AnyWordSpec with Matchers {

  val maxHeaderSize = 4096
  val maxHeaderTableSize = 4096

  /**
   * A stream that has all of its data available but hands it out in small pieces, which
   * `InputStream.read(byte[])` is explicitly allowed to do.
   */
  private class TricklingInputStream(bytes: Array[Byte], bytesPerRead: Int) extends InputStream {
    private val underlying = new ByteArrayInputStream(bytes)
    override def read(): Int = underlying.read()
    override def read(b: Array[Byte], off: Int, len: Int): Int =
      underlying.read(b, off, math.min(len, bytesPerRead))
    override def available(): Int = underlying.available()
    override def markSupported(): Boolean = underlying.markSupported()
    override def mark(readLimit: Int): Unit = underlying.mark(readLimit)
    override def reset(): Unit = underlying.reset()
    override def skip(n: Long): Long = underlying.skip(n)
  }

  /**
   * The stream a multi-fragment `ByteString` hands out: no mark/reset, and `available()` only ever
   * reports what is left in the current chunk rather than the whole block.
   */
  private def chunkedStream(bytes: Array[Byte], chunkSize: Int): InputStream =
    new SequenceInputStream(
      bytes.grouped(chunkSize).map(chunk => new ByteArrayInputStream(chunk): InputStream).asJavaEnumeration)

  private def encode(headers: (String, String)*): Array[Byte] = {
    val out = new ByteArrayOutputStream
    val encoder = new Encoder(maxHeaderTableSize)
    headers.foreach { case (name, value) => encoder.encodeHeader(out, name, value, false) }
    out.toByteArray
  }

  private def decode(in: InputStream): Seq[(String, String)] =
    decode(new Decoder(maxHeaderSize, maxHeaderTableSize), in)

  private def decode(decoder: Decoder, in: InputStream): Seq[(String, String)] = {
    val decoded = ListBuffer.empty[(String, String)]
    decoder.decode(in,
      new HeaderListener {
        override def addHeader(name: String, value: String, parsed: AnyRef, sensitive: Boolean): AnyRef = {
          decoded += (name -> value)
          null
        }
      })
    decoder.endHeaderBlock()
    decoded.toList
  }

  "The HPACK decoder" should {
    val headers = Seq("x-custom-header" -> "some-fairly-long-header-value", "another-header" -> "value")

    "decode a header block from a stream that returns everything at once" in {
      decode(new ByteArrayInputStream(encode(headers: _*))) shouldEqual headers
    }

    "decode a header block from a stream that returns one byte per read" in {
      // string literals are read with a single call, so a short read must not be taken for truncation
      decode(new TricklingInputStream(encode(headers: _*), bytesPerRead = 1)) shouldEqual headers
    }

    "decode a header block from a stream that returns a few bytes per read" in {
      decode(new TricklingInputStream(encode(headers: _*), bytesPerRead = 7)) shouldEqual headers
    }

    "decode a header block from a stream that supports neither mark/reset nor a whole-block available()" in {
      val stream = chunkedStream(encode(headers: _*), chunkSize = 4)
      stream.markSupported() shouldEqual false
      decode(stream) shouldEqual headers
    }

    "decode a header block split so that a string literal straddles a chunk boundary" in {
      decode(chunkedStream(encode(headers: _*), chunkSize = 3)) shouldEqual headers
    }

    "decode a header block split into single-byte chunks" in {
      decode(chunkedStream(encode(headers: _*), chunkSize = 1)) shouldEqual headers
    }

    "report a block that ends in the middle of a string literal as a decompression failure" in {
      val full = encode(headers: _*)
      a[IOException] should be thrownBy decode(new ByteArrayInputStream(full.dropRight(5)))
    }

    "report a block that ends in the middle of a length prefix as a decompression failure" in {
      // a literal header field with incremental indexing, new name, whose name length never arrives
      a[IOException] should be thrownBy decode(new ByteArrayInputStream(Array[Byte](0x40)))
    }

    "decode string literals longer than its initial scratch space, Huffman coded and raw" in {
      // the decoder reads literals into reusable arrays that start at 128 bytes; a value of letters is
      // Huffman coded, a value of mostly '|' (a 15-bit code) is sent raw because that is shorter
      val long = Seq("x-huffman" -> ("abcdefghij" * 50), "x-raw" -> ("|" * 500))
      decode(new ByteArrayInputStream(encode(long: _*))) shouldEqual long
    }

    "decode consecutive header blocks of varying literal lengths with the same decoder" in {
      // a literal shorter than the previous one must not pick up the tail the previous one left in the
      // reused scratch arrays
      val decoder = new Decoder(maxHeaderSize, maxHeaderTableSize)
      val blocks = Seq(
        Seq("x-a" -> ("abcdefghij" * 40)),
        Seq("x-b" -> "short", "x-c" -> ("|" * 300)),
        Seq("x-d" -> "s", "x-e" -> "|"),
        Seq("x-f" -> ""))
      blocks.foreach { block =>
        // a fresh encoder per block, so no block refers to what an earlier one put in the dynamic table
        decode(decoder, new ByteArrayInputStream(encode(block: _*))) shouldEqual block
      }
    }

    "decode a raw string literal carrying opaque octets above 0x7F to the chars of the same value" in {
      // literal header field without indexing (0x00), new name: raw name "x-opaque", raw 128-octet value
      val name = "x-opaque".getBytes("US-ASCII")
      val value = Array.tabulate(128)(i => (0x80 + i).toByte)
      // 0x7f 0x01: 7-bit prefix integer 128 = 127 + 1, Huffman flag clear
      val block = Array[Byte](0x00, name.length.toByte) ++ name ++ Array[Byte](0x7F, 0x01) ++ value
      decode(new ByteArrayInputStream(block)) shouldEqual Seq("x-opaque" -> new String(value, "ISO-8859-1"))
    }
  }
}
