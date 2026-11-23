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

import org.apache.pekko.util.ByteString
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class FrameMaskingSpec extends AnyWordSpec with Matchers {
  private val maskKey = 0xFEDCBA09
  private val keyBytes = Seq(0xFE, 0xDC, 0xBA, 0x09)

  "WebSocket masking" should {
    "match the per-byte mask and preserve the source around word boundaries" in {
      for (size <- (0 to 16) ++ Seq(63, 64, 125, 126, 1024) ++ (4095 to 4103)) {
        val data = Vector.tabulate(size)(i => (i * 71).toByte)
        val source = ByteString.fromArray(data.toArray)
        val (masked, nextKey) = FrameEventParser.mask(source, maskKey)
        val expected = data.zipWithIndex.map { case (b, i) => (b ^ keyBytes(i % 4)).toByte }

        masked.toVector shouldBe expected
        nextKey shouldBe Integer.rotateLeft(maskKey, (size % 4) * 8)
        source.toVector shouldBe data
        FrameEventParser.mask(masked, maskKey)._1 shouldBe source
      }
    }

    "carry the correct mask phase across all splits of a payload" in {
      val data = Array.tabulate[Byte](27)(i => (i * 43).toByte)
      val (start, end) = data.splitAt(11)
      val source = ByteString.fromArray(start) ++ ByteString.fromArray(end)
      val (full, finalKey) = FrameEventParser.mask(source, maskKey)
      full shouldBe FrameEventParser.mask(ByteString.fromArray(data), maskKey)._1
      source.toVector shouldBe data.toVector

      for (split <- 0 to source.length) {
        val (first, nextKey) = FrameEventParser.mask(source.take(split), maskKey)
        val (last, nextFinalKey) = FrameEventParser.mask(source.drop(split), nextKey)

        (first ++ last) shouldBe full
        nextFinalKey shouldBe finalKey
      }
    }

    "carry the mask phase from a small chunk into a chunk large enough for word masking" in {
      val data = ByteString.fromArray(Array.tabulate[Byte](4103)(i => (i * 43).toByte))
      val (full, finalKey) = FrameEventParser.mask(data, maskKey)

      for (split <- 1 to 7) {
        val (first, nextKey) = FrameEventParser.mask(data.take(split), maskKey)
        val (last, nextFinalKey) = FrameEventParser.mask(data.drop(split), nextKey)

        (first ++ last) shouldBe full
        nextFinalKey shouldBe finalKey
      }
    }
  }
}
