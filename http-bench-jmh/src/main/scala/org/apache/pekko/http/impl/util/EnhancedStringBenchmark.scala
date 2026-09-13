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

package org.apache.pekko.http.impl.util

import java.util.concurrent.TimeUnit

import scala.annotation.tailrec

import org.openjdk.jmh.annotations._

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Array(Mode.AverageTime))
@Fork(2)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
private[util] class EnhancedStringBenchmark {

  @Param(Array("short", "header", "long"))
  var input = "header"

  var string: String = null
  var target: Array[Byte] = null

  @Setup
  def setup(): Unit = {
    string = input match {
      case "short"  => "chunked"
      case "header" => "Content-Type: application/json; charset=UTF-8\r\n"
      case "long"   => "x" * 1024
    }
    target = new Array[Byte](string.length + 16)
  }

  // the per-character loop that `getAsciiBytes` used before it switched to the bulk `String.getBytes` copy,
  // kept for comparison
  @tailrec private def charAtLoop(s: String, array: Array[Byte], ix: Int): Unit =
    if (ix < array.length) {
      array(ix) = s.charAt(ix).asInstanceOf[Byte]
      charAtLoop(s, array, ix + 1)
    }

  @Benchmark
  def baseline_charAt_asciiBytes(): Array[Byte] = {
    val array = new Array[Byte](string.length)
    charAtLoop(string, array, 0)
    array
  }

  @Benchmark
  def asciiBytes(): Array[Byte] = string.asciiBytes

  @Benchmark
  def getAsciiBytes(): Array[Byte] = {
    string.getAsciiBytes(target, 8)
    target
  }
}
