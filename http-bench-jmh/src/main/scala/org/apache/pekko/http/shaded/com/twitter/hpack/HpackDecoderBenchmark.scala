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

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._

import org.apache.pekko.http.impl.util.ByteStringOutputStream
import org.apache.pekko.util.ByteString

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Array(Mode.AverageTime))
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
class HpackDecoderBenchmark {

  /** `default` is what a browser sends (Huffman where shorter), the other two force one string literal form. */
  @Param(Array("default", "huffman", "raw"))
  var mode = "default"

  var decoder: Decoder = null
  var headerBlock: ByteString = null

  // a typical browser request; the block is encoded without indexing so that every value is a string literal
  // and decoding it does the same work on every call
  val headers: Array[(String, String)] = Array(
    ":method" -> "GET",
    ":scheme" -> "https",
    ":authority" -> "www.example.com",
    ":path" -> "/api/v1/users/12345/orders?page=2&sort=created_at&direction=desc",
    "user-agent" ->
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "accept" -> "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
    "accept-language" -> "en-US,en;q=0.9",
    "accept-encoding" -> "gzip, deflate, br",
    "cookie" -> "session=ab12cd34ef56ab12cd34ef56ab12cd34ef56ab12; theme=dark; consent=1",
    "cache-control" -> "no-cache")

  private val listener: HeaderListener =
    (_: String, _: String, parsed: AnyRef, _: Boolean) => parsed

  @Setup
  def setup(): Unit = {
    val encoder = mode match {
      case "default" => new Encoder(4096, false, false, false)
      case "huffman" => new Encoder(4096, false, true, false)
      case "raw"     => new Encoder(4096, false, false, true)
    }
    val out = new ByteStringOutputStream(512)
    headers.foreach { case (name, value) => encoder.encodeHeader(out, name, value, false) }
    headerBlock = out.takeByteString()
    decoder = new Decoder(8192, 4096)
  }

  @Benchmark
  def decodeHeaders(): Unit = {
    decoder.decode(headerBlock.asInputStream, listener)
    decoder.endHeaderBlock()
  }
}
