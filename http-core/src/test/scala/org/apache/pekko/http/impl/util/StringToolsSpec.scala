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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class StringToolsSpec extends AnyWordSpec with Matchers {

  "StringTools.asciiStringBytes" should {
    "map every ASCII character to the byte of the same value" in {
      StringTools.asciiStringBytes("content-type: application/json") shouldEqual
      "content-type: application/json".getBytes("US-ASCII")
    }

    "be the inverse of asciiStringFromBytes for every octet" in {
      // HPACK string literals are opaque octets, so a char in 0x80-0xFF must map back to that octet rather than
      // to the '?' that encoding with US-ASCII would substitute
      val allOctets = Array.tabulate(256)(_.toByte)
      StringTools.asciiStringBytes(StringTools.asciiStringFromBytes(allOctets)) shouldEqual allOctets
    }

    "truncate a character above 0xFF to its low 8 bits" in {
      // U+0100 forces the UTF-16 coder on JDK 9+, so this covers the non-arraycopy path too
      StringTools.asciiStringBytes("aĀb") shouldEqual Array[Byte]('a', 0x00, 'b')
    }

    "encode the empty string to an empty array" in {
      StringTools.asciiStringBytes("") shouldEqual Array.emptyByteArray
    }
  }
}
