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

class TimestampSpec extends AnyWordSpec with Matchers {

  "Timestamp.Ordering" should {
    "correctly order two normal timestamps" in {
      val t1 = new Timestamp(100L)
      val t2 = new Timestamp(200L)
      Timestamp.Ordering.compare(t1, t2) should be < 0
      Timestamp.Ordering.compare(t2, t1) should be > 0
      Timestamp.Ordering.compare(t1, t1) shouldEqual 0
    }

    "correctly order Timestamp.never against a normal timestamp" in {
      val normal = new Timestamp(System.nanoTime())
      val never = Timestamp.never
      Timestamp.Ordering.compare(normal, never) should be < 0
      Timestamp.Ordering.compare(never, normal) should be > 0
      Timestamp.Ordering.compare(never, never) shouldEqual 0
    }

    "not overflow when comparing Long.MaxValue (never) with Long.MinValue" in {
      // Subtraction Long.MaxValue - Long.MinValue would overflow in signed arithmetic;
      // using Long.compare avoids this.
      val tMax = new Timestamp(Long.MaxValue)
      val tMin = new Timestamp(Long.MinValue)
      Timestamp.Ordering.compare(tMax, tMin) should be > 0
      Timestamp.Ordering.compare(tMin, tMax) should be < 0
    }

    "not overflow when comparing values near Long.MaxValue and Long.MinValue" in {
      val tAlmostMax = new Timestamp(Long.MaxValue - 1)
      val tAlmostMin = new Timestamp(Long.MinValue + 1)
      Timestamp.Ordering.compare(tAlmostMax, tAlmostMin) should be > 0
      Timestamp.Ordering.compare(tAlmostMin, tAlmostMax) should be < 0
    }
  }
}
