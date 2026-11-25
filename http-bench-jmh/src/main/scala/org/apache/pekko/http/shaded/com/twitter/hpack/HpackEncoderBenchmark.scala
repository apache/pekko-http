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
  val out = new ByteStringOutputStream(128)

  // a typical browser request; every value goes through HuffmanEncoder.getEncodedLength and most through encode
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

  @Setup
  def setup(): Unit =
    encoder = mode match {
      case "default" => new Encoder(4096, false, false, false)
      case "huffman" => new Encoder(4096, false, true, false)
      case "raw"     => new Encoder(4096, false, false, true)
    }

  @Benchmark
  def encodeHeaders(): Int = {
    var i = 0
    while (i < headers.length) {
      val (name, value) = headers(i)
      encoder.encodeHeader(out, name, value, false)
      i += 1
    }
    out.takeByteString().length
  }
}

/**
 * Ordinary synthetic metadata profiles for comparing dynamic-table lookup implementations.
 * Except for the mixed profile's per-request ID, input contents are prepared outside measurement. The encoder sees a fresh value
 * String for every write, including equal values in the exact-hit profiles.
 */
@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Array(Mode.AverageTime))
@Fork(2)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 8, time = 2)
class HpackDynamicTableLookupBenchmark {
  @Param(Array("same-name-churn", "long-same-name", "long-distinct-name", "exact-newest", "exact-deep",
    "mixed-request"))
  var profile = "same-name-churn"

  private var encoder: Encoder = null
  private val out = new ByteStringOutputStream(128)
  private var nextRequestId = 0L
  private var names: Array[String] = null
  private var values: Array[String] = null

  @Setup(Level.Iteration)
  def setup(): Unit = {
    encoder = new Encoder(4096)
    val longMetadata = "app-v1;category=metrics;environment=local;" * 5
    val requestNames =
      Array(":method", ":scheme", ":authority", ":path", "accept", "user-agent", "accept-language", "x-request-id")
    names = Array.tabulate(128) { i =>
      if (profile == "mixed-request") requestNames(i % requestNames.length)
      else if (profile == "long-distinct-name") s"x-benchmark-meta-$i"
      else "x-benchmark-meta"
    }
    if (profile == "exact-newest" || profile == "exact-deep") {
      // HPACK indexed reads do not reorder entries: both hit positions remain stable.
      (0 until 12).foreach(i => encoder.encodeHeader(out, names(0), s"$longMetadata;$i", false))
      out.takeByteString()
    }
    values = Array.tabulate(128) { i =>
      profile match {
        case "mixed-request" =>
          val request = i / requestNames.length
          (i % requestNames.length) match {
            case 0 => "GET"
            case 1 => "https"
            case 2 => "api.example.org"
            case 3 => s"/products/${request % 4}"
            case 4 => "application/json"
            case 5 => "shop-client/1.0"
            case 6 => if (request % 2 == 0) "en-US" else "fr-FR"
            case _ => "" // filled with a new ID for each request below
          }
        case "same-name-churn" => s"event-$i"
        case "exact-newest"    => s"$longMetadata;11"
        case "exact-deep"      => s"$longMetadata;0"
        case _                 => s"$longMetadata;event=$i"
      }
    }
  }

  @Benchmark
  @OperationsPerInvocation(128)
  def encodeMetadata(): Int = {
    if (profile == "mixed-request") {
      var idIndex = 7
      while (idIndex < values.length) {
        // Fixed width: faster forks must not allocate larger IDs merely by issuing more requests.
        values(idIndex) = java.lang.Long.toHexString(Long.MinValue | nextRequestId)
        nextRequestId += 1
        idIndex += 8
      }
    }
    var i = 0
    while (i < names.length) {
      // These source values are never supplied to or hashed by the encoder.
      encoder.encodeHeader(out, names(i), new String(values(i)), false)
      i += 1
    }
    out.takeByteString().length
  }
}
