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

package org.apache.pekko.http.impl.engine.http2

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class Http2ServerShutdownBoundarySpec extends AnyWordSpec with Matchers {
  "The HTTP/2 server shutdown boundary" should {
    "exclude a request not admitted for capacity" in {
      val boundary = new Http2Demux.ServerShutdownBoundary(0)
      boundary.admit(1, hasCapacity = true) shouldBe true
      boundary.admit(3, hasCapacity = false) shouldBe false
      boundary.isOpen shouldBe true
      boundary.finish() shouldBe 1
    }

    "stay fixed after a final decision instead of admitting later requests" in {
      val boundary = new Http2Demux.ServerShutdownBoundary(0)
      boundary.admit(1, hasCapacity = true) shouldBe true
      boundary.admit(3, hasCapacity = true) shouldBe true
      boundary.finish() shouldBe 3
      boundary.isOpen shouldBe false
      boundary.admit(5, hasCapacity = true) shouldBe false
      boundary.finish() shouldBe 3
    }

    "treat the maximum valid stream ID as a final boundary rather than an open sentinel" in {
      val boundary = new Http2Demux.ServerShutdownBoundary(0)
      boundary.admit(Int.MaxValue, hasCapacity = true) shouldBe true
      boundary.finish() shouldBe Int.MaxValue
      boundary.isOpen shouldBe false
    }

    "retain the request already admitted by cleartext upgrade" in {
      val boundary = new Http2Demux.ServerShutdownBoundary(1)
      boundary.finish() shouldBe 1
      boundary.admit(3, hasCapacity = true) shouldBe false
      boundary.finish() shouldBe 1
    }
  }
}
