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

class EnhancedStringSpec extends AnyWordSpec with Matchers {

  "EnhancedString.asciiBytes" should {
    "map every ASCII character to the byte of the same value" in {
      val ascii = new String(Array.tabulate(128)(_.toChar))
      ascii.asciiBytes shouldEqual Array.tabulate(128)(_.toByte)
    }

    "truncate a character to its low 8 bits instead of substituting a replacement character" in {
      // U+0100 is not Latin-1, so the string uses the UTF-16 coder on JDK 9+; the byte must still be the low 8 bits
      // rather than the '?' that encoding with a charset would produce.
      "aÿbĀcሴ".asciiBytes shouldEqual Array[Byte]('a', 0xFF.toByte, 'b', 0x00, 'c', 0x34)
    }

    "encode the empty string to an empty array" in {
      "".asciiBytes shouldEqual Array.emptyByteArray
    }
  }

  "EnhancedString.getAsciiBytes" should {
    "copy the bytes into the target array starting at the offset" in {
      val array = Array.fill[Byte](8)('.')
      "abc".getAsciiBytes(array, 2)
      new String(array, "ISO-8859-1") shouldEqual "..abc..."
    }

    "copy only the portion that fits when the array is too small" in {
      val array = Array.fill[Byte](4)('.')
      "abcdef".getAsciiBytes(array, 2)
      new String(array, "ISO-8859-1") shouldEqual "..ab"
    }

    "copy nothing when the offset is at or past the end of the array" in {
      val array = Array.fill[Byte](3)('.')
      "abc".getAsciiBytes(array, 3)
      "abc".getAsciiBytes(array, 4)
      new String(array, "ISO-8859-1") shouldEqual "..."
    }

    "truncate a non-Latin-1 character to its low 8 bits when copying at an offset" in {
      val array = new Array[Byte](3)
      "Łł".getAsciiBytes(array, 1)
      array shouldEqual Array[Byte](0, 0x41, 0x42)
    }
  }
}
