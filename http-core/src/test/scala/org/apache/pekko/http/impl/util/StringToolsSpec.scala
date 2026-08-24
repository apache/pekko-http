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

import java.nio.charset.StandardCharsets

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class StringToolsSpec extends AnyWordSpec with Matchers {

  "StringTools.asciiStringFromBytes" should {
    "map every byte to the character of the same value" in {
      // HPACK string literals are opaque octets, so bytes above 0x7F do reach this method. They must
      // keep mapping to the character of the same value rather than to a replacement character, which
      // is what decoding as US-ASCII would do.
      val allBytes = Array.tabulate(256)(_.toByte)
      val decoded = StringTools.asciiStringFromBytes(allBytes)

      decoded.length shouldEqual 256
      decoded.toSeq.map(_.toInt) shouldEqual (0 until 256)
    }

    "round-trip an ASCII string through asciiStringBytes" in {
      val original = "content-type: application/json"
      StringTools.asciiStringFromBytes(StringTools.asciiStringBytes(original)) shouldEqual original
    }

    "decode an empty array to an empty string" in {
      StringTools.asciiStringFromBytes(Array.emptyByteArray) shouldEqual ""
    }
  }

  "StringTools.asciiStringBytes" should {
    "encode an ASCII string to its US-ASCII bytes" in {
      StringTools.asciiStringBytes("abc") shouldEqual "abc".getBytes(StandardCharsets.US_ASCII)
    }
  }
}
