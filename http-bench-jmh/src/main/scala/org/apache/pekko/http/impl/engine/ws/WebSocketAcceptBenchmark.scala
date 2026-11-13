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

package org.apache.pekko.http.impl.engine.ws

import java.security.MessageDigest
import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._

import org.apache.pekko.http.impl.util._
import org.parboiled2.util.Base64
import org.apache.pekko.http.scaladsl.model.headers.{ `Sec-WebSocket-Accept`, `Sec-WebSocket-Key` }

@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Array(Mode.AverageTime))
class WebSocketAcceptBenchmark {
  @Param(Array("dGhlIHNhbXBsZSBub25jZQ==", "YLQguzhR2dR6y5M9vnA5mw=="))
  var keyText: String = _
  private var key: `Sec-WebSocket-Key` = _

  @Setup
  def setup(): Unit = {
    key = `Sec-WebSocket-Key`("dGhlIHNhbXBsZSBub25jZQ==")
    require(separateUpdates().key == "s3pPLMBiTxaQ9kYGzzhZRbK+xOo=")
    val random = new java.util.Random(0L)
    for (_ <- 0 until 100) {
      val bytes = new Array[Byte](16)
      random.nextBytes(bytes)
      key = `Sec-WebSocket-Key`(java.util.Base64.getEncoder.encodeToString(bytes))
      require(separateUpdates() == baseline())
    }
    for (text <- List(null, "", "é", "\uD83D\uDE00", "\uD800", "abc\uDC00")) {
      key = `Sec-WebSocket-Key`(text)
      require(separateUpdates() == baseline())
    }
    key = `Sec-WebSocket-Key`(keyText)
    require(separateUpdates() == baseline())
  }

  @Benchmark
  def baseline(): `Sec-WebSocket-Accept` = {
    val sha1 = MessageDigest.getInstance("sha1")
    val salted = key.key + `Sec-WebSocket-Accept`.MagicGuid
    val hash = sha1.digest(salted.asciiBytes)
    `Sec-WebSocket-Accept`(Base64.rfc2045().encodeToString(hash, false))
  }

  @Benchmark
  def separateUpdates(): `Sec-WebSocket-Accept` = `Sec-WebSocket-Accept`.forKey(key)
}
