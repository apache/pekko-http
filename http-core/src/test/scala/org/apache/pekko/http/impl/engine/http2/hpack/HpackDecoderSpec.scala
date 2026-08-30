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

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream, InputStream }

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

  private def encode(headers: (String, String)*): Array[Byte] = {
    val out = new ByteArrayOutputStream
    val encoder = new Encoder(maxHeaderTableSize)
    headers.foreach { case (name, value) => encoder.encodeHeader(out, name, value, false) }
    out.toByteArray
  }

  private def decode(in: InputStream): Seq[(String, String)] = {
    val decoded = ListBuffer.empty[(String, String)]
    val decoder = new Decoder(maxHeaderSize, maxHeaderTableSize)
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
  }
}
