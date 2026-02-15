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

package org.apache.pekko.http.cors.scaladsl.model

import org.apache.pekko.http.scaladsl.model.headers.{ `Content-Type`, Accept }
import org.scalatest.Inspectors
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class HttpHeaderRangeSpec extends AnyWordSpec with Matchers with Inspectors {

  "The `*` range" should {
    "match any Header" in {
      val headers = Seq(
        `Content-Type`.name,
        "conTent-tyPe",
        Accept.name,
        "x-any-random-header-name")

      forAll(headers) { o => HttpHeaderRange.*.matches(o) shouldBe true }
    }

    "be printed as `*`" in {
      HttpHeaderRange.*.toString shouldBe "*"
    }
  }

  "The default range" should {
    val range = HttpHeaderRange(`Content-Type`.name, Accept.name)

    "match headers ignoring case" in {
      val headers = Seq(
        `Content-Type`.name,
        `Content-Type`.name.toUpperCase(),
        `Content-Type`.name.toLowerCase(),
        "conTent-tyPe",
        Accept.name,
        Accept.name.toUpperCase(),
        Accept.name.toLowerCase(),
        "aCcepT")

      forAll(headers) { o => range.matches(o) shouldBe true }
    }

    "not match other headers" in {
      val headers = Seq(
        "Content-Type2",
        "Content-Typ",
        "x-any-random-header-name")

      forAll(headers) { o => range.matches(o) shouldBe false }
    }
  }

  "Concatenation of ranges" should {
    "match both ranges" in {
      val range1 = HttpHeaderRange(`Content-Type`.name)
      val range2 = HttpHeaderRange(Accept.name)

      val combined = range1 ++ range2

      val headers = Seq(
        `Content-Type`.name,
        Accept.name)
      val notHeaders = Seq(
        "Content-Type2",
        "Content-Typ",
        "x-any-random-header-name")

      forAll(headers) { o => combined.matches(o) shouldBe true }
      forAll(notHeaders) { o => combined.matches(o) shouldBe false }
    }

    "combine with the `*` range" in {
      val range1 = HttpHeaderRange(`Content-Type`.name)
      val starRange = HttpHeaderRange.*

      val combinedBefore = starRange ++ range1
      val combinedAfter = range1 ++ starRange

      val headers = Seq(
        `Content-Type`.name,
        "conTent-tyPe",
        Accept.name,
        "x-any-random-header-name")

      forAll(headers) { o => combinedBefore.matches(o) shouldBe true }
      forAll(headers) { o => combinedAfter.matches(o) shouldBe true }
    }
  }

}
