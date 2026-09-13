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

import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Array(Mode.AverageTime))
@Fork(2)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 8, time = 2)
class HpackEncoderBenchmark {

  /** `default` lets the encoder pick Huffman when it is shorter, the other two force one string literal form. */
  @Param(Array("default", "huffman", "raw"))
  var mode = "default"

  // indexing is disabled so that every call encodes the values as string literals instead of table references
  var encoder: Encoder = null
  val out = new ByteArrayOutputStream(1024)

  // a typical browser request; every value goes through HuffmanEncoder.getEncodedLength and most through encode
  val headers: Array[(String, String)] = Array(
    ":method" -> "GET",
    ":scheme" -> "https",
    ":authority" -> "www.example.com",
    ":path" -> "/api/v1/users/12345/orders?page=2&sort=created_at&direction=desc",
    "user-agent" -> "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "accept" -> "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
    "accept-language" -> "en-US,en;q=0.9",
    "accept-encoding" -> "gzip, deflate, br",
    "cookie" -> "session=ab12cd34ef56ab12cd34ef56ab12cd34ef56ab12; theme=dark; consent=1",
    "cache-control" -> "no-cache")

  @Setup
  def setup(): Unit =
    encoder = mode match {
      case "default" => new Encoder(4096, false, false, false)
      case "huffman" => new Encoder(4096, false, true, false)
      case "raw"     => new Encoder(4096, false, false, true)
    }

  @Benchmark
  def encodeHeaders(): Int = {
    out.reset()
    var i = 0
    while (i < headers.length) {
      val (name, value) = headers(i)
      encoder.encodeHeader(out, name, value, false)
      i += 1
    }
    out.size
  }
}
