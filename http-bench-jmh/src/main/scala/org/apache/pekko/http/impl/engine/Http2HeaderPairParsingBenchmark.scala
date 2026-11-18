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

package org.apache.pekko.http.impl.engine

import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

import scala.concurrent.Await
import scala.concurrent.duration._

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.http.impl.engine.parsing.HttpHeaderParser
import pekko.http.impl.settings.ParserSettingsImpl
import pekko.util.ByteString

import com.typesafe.config.ConfigFactory

/**
 * Parsing of HTTP/2 header name/value pairs (as HPACK hands them out) with the HTTP/1.1 header parser: building a
 * header line String and an ISO-8859-1 ByteString per header (`concatenated`), versus writing the line into the
 * parser's reused buffer (`reusedBuffer`).
 *
 * With `values = repeated` every request carries the same values, so the parser's value cache answers most headers.
 * With `values = unique` every request carries values the parser hasn't seen, so they are parsed each time.
 */
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@BenchmarkMode(Array(Mode.Throughput))
private[engine] class Http2HeaderPairParsingBenchmark {
  implicit val system: ActorSystem = ActorSystem("http2-header-pair-parsing-benchmark")

  @Param(Array("repeated", "unique"))
  var values = "repeated"

  private val VariantCount = 1024

  private var parser: HttpHeaderParser = null
  private var requests: Array[Array[(String, String)]] = null
  private var next = 0

  @Setup
  def setup(): Unit = {
    val root = ConfigFactory.load()
    val settings = ParserSettingsImpl.fromSubConfig(root, root.getConfig("pekko.http.server.parsing"))
    // a connection parses with a shallow copy of the primed master parser
    parser = HttpHeaderParser.prime(HttpHeaderParser.unprimed(settings, system.log, _ => ())).createShallowCopy()
    def request(i: Int): Array[(String, String)] =
      if (values == "repeated")
        Array(
          "user-agent" -> "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0",
          "accept" -> "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
          "accept-language" -> "en-US,en;q=0.5",
          "accept-encoding" -> "gzip, deflate, br",
          "referer" -> "https://example.com/some/page",
          "cache-control" -> "no-cache",
          "authorization" -> "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0",
          "x-request-id" -> "3f9c2b1e-8d4a-4c1b-9e7f-2a6b5c4d3e2f")
      else
        // still valid values, so that the parser takes its normal path rather than the error path
        Array(
          "user-agent" -> s"Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.$i",
          "accept" -> s"text/html;level=$i,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
          "accept-language" -> s"en-US,en;q=0.5,x-$i",
          "accept-encoding" -> s"gzip, deflate, br, x-enc-$i",
          "referer" -> s"https://example.com/some/page/$i",
          "cache-control" -> s"max-age=$i",
          "authorization" -> s"Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0$i",
          "x-request-id" -> s"3f9c2b1e-8d4a-4c1b-9e7f-2a6b5c4d$i")
    requests = Array.tabulate(VariantCount)(request)
  }

  @TearDown
  def tearDown(): Unit = Await.result(system.terminate(), 5.seconds)

  private def nextRequest(): Array[(String, String)] = {
    val r = requests(next)
    next = (next + 1) % VariantCount
    r
  }

  @Benchmark
  def concatenated(bh: Blackhole): Unit = {
    val headers = nextRequest()
    var i = 0
    while (i < headers.length) {
      val (name, value) = headers(i)
      parser.parseHeaderLine(ByteString(name + ": " + value + "\r\nx", StandardCharsets.ISO_8859_1))()
      bh.consume(parser.resultHeader)
      i += 1
    }
  }

  @Benchmark
  def reusedBuffer(bh: Blackhole): Unit = {
    val headers = nextRequest()
    var i = 0
    while (i < headers.length) {
      val (name, value) = headers(i)
      bh.consume(parser.parseHeaderPair(name, value))
      i += 1
    }
  }
}
