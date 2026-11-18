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

package org.apache.pekko.http.scaladsl.coding

import java.util.concurrent.TimeUnit

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random

import org.openjdk.jmh.annotations._

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.stream.scaladsl.Source
import pekko.util.ByteString

/**
 * Decodes a gzip or deflate payload through `Decoder.decoderFlow`. One op decodes the whole payload.
 *
 *  - `text`: 1 MiB of text (compresses about 3:1), delivered as one element
 *  - `text-1k`: the same compressed bytes delivered in 1 KiB elements, so each element inflates to a few KiB
 *  - `repetitive`: 4 MiB of highly compressible data delivered as one element, so each output chunk is full
 */
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Array(Mode.AverageTime))
class DecoderBenchmark {
  implicit val system: ActorSystem = ActorSystem("decoder-benchmark")

  @Param(Array("gzip", "deflate"))
  var coder = "gzip"

  @Param(Array("text", "text-1k", "repetitive"))
  var input = "text"

  private var decoder: Decoder = null
  private var chunks: Vector[ByteString] = null

  @Setup
  def setup(): Unit = {
    val coders = if (coder == "gzip") Coders.Gzip else Coders.Deflate
    decoder = coders
    val plain =
      if (input == "repetitive") ByteString(Array.fill[Byte](4 * 1024 * 1024)('a'))
      else {
        val random = new Random(42)
        val words = Vector("the", "quick", "brown", "fox", "jumps", "over", "lazy", "dog", "pekko", "http",
          "stream", "header", "entity", "request", "response", "chunk", "buffer", "decode")
        val sb = new java.lang.StringBuilder
        while (sb.length < 1024 * 1024) sb.append(words(random.nextInt(words.size))).append(random.nextInt(1000))
          .append(' ')
        ByteString(sb.toString.take(1024 * 1024))
      }
    val compressed = Await.result(coders.encodeAsync(plain), 10.seconds)
    chunks = if (input == "text-1k") compressed.grouped(1024).toVector else Vector(compressed)
  }

  @TearDown
  def tearDown(): Unit = Await.result(system.terminate(), 5.seconds)

  @Benchmark
  def decode(): Long =
    Await.result(Source(chunks).via(decoder.decoderFlow).runFold(0L)(_ + _.size), 10.seconds)
}
