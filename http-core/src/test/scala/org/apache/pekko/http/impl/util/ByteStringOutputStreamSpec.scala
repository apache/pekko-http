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

import org.apache.pekko.util.ByteString
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ByteStringOutputStreamSpec extends AnyWordSpec with Matchers {

  "ByteStringOutputStream" must {

    "return an empty ByteString when nothing was written" in {
      new ByteStringOutputStream(16).toByteStringUnsafe should ===(ByteString.empty)
    }

    "return the bytes written when the buffer is exactly filled" in {
      val out = new ByteStringOutputStream(4)
      out.write(Array[Byte](1, 2, 3, 4))
      out.toByteStringUnsafe should ===(ByteString(1, 2, 3, 4))
    }

    "return the bytes written when the buffer was grown" in {
      val out = new ByteStringOutputStream(2)
      val data = Array.tabulate[Byte](1000)(i => i.toByte)
      out.write(data)
      out.toByteStringUnsafe should ===(ByteString(data))
    }

    "return the bytes written when only a small part of the buffer is used" in {
      val out = new ByteStringOutputStream(1024)
      out.write(Array[Byte](1, 2, 3))
      out.write(4)
      out.toByteStringUnsafe should ===(ByteString(1, 2, 3, 4))
    }

    "not retain the buffer when only a small part of it is used" in {
      val out = new ByteStringOutputStream(1024)
      out.write(Array[Byte](1, 2, 3))
      // the ByteString is a copy, so it is not affected by later writes to the stream
      val result = out.toByteStringUnsafe
      out.write(Array[Byte](9, 9, 9))
      result should ===(ByteString(1, 2, 3))
    }

    "write single bytes and byte ranges" in {
      val out = new ByteStringOutputStream(8)
      out.write(1)
      out.write(Array[Byte](0, 2, 3, 0), 1, 2)
      out.write(Array[Byte](4, 5, 6, 7, 8))
      out.toByteStringUnsafe should ===(ByteString(1, 2, 3, 4, 5, 6, 7, 8))
    }
  }
}
