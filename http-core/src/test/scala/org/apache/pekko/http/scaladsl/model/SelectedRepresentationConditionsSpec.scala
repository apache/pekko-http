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

package org.apache.pekko.http.scaladsl.model

import org.apache.pekko
import pekko.http.javadsl.model.SelectedRepresentationDecision._
import pekko.http.scaladsl.model.HttpMethods.{ GET, HEAD, OPTIONS }
import pekko.http.scaladsl.model.headers.{
  ByteRange,
  EntityTag,
  `If-Modified-Since`,
  `If-None-Match`,
  `If-Range`,
  `If-Unmodified-Since`,
  Range
}

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class SelectedRepresentationConditionsSpec extends AnyWordSpec with Matchers {
  import SelectedRepresentationConditions.evaluate

  private val selectedTag = EntityTag("coded-r1")
  private val otherTag = EntityTag("identity-r1")
  private val modified = DateTime(2025, 1, 10, 12, 0, 0)
  private val later = DateTime(2025, 1, 10, 12, 0, 1)
  private val range = Range(ByteRange(0, 7))

  "Selected representation conditions" should {
    "validate the selected variant before considering Range" in {
      val weakSelected = selectedTag.copy(weak = true)
      val ifNoneMatch = `If-None-Match`(selectedTag)
      evaluate(GET, List(range, ifNoneMatch), Some(weakSelected), None) shouldBe NOT_MODIFIED
      evaluate(HEAD, List(ifNoneMatch), Some(weakSelected), None) shouldBe NOT_MODIFIED
      evaluate(GET, List(ifNoneMatch), Some(otherTag), None) shouldBe PROCEED
      evaluate(GET, List(`If-None-Match`.`*`), None, None) shouldBe NOT_MODIFIED
    }

    "prefer If-None-Match over the selected representation date" in {
      val ifModifiedSince = `If-Modified-Since`(later)
      evaluate(GET, List(ifModifiedSince), None, Some(modified)) shouldBe NOT_MODIFIED
      evaluate(GET, List(ifModifiedSince), None, None) shouldBe PROCEED
      evaluate(GET, List(`If-None-Match`(otherTag), ifModifiedSince), None, Some(modified)) shouldBe PROCEED
    }

    "allow range evaluation only on a strong match for a supplied tag" in {
      val requestHeaders = List(range, `If-Range`(selectedTag))
      evaluate(GET, requestHeaders, Some(selectedTag), None) shouldBe EVALUATE_RANGE
      evaluate(GET, requestHeaders, Some(otherTag), None) shouldBe PROCEED
      evaluate(GET, requestHeaders, Some(selectedTag.copy(weak = true)), None) shouldBe PROCEED
      evaluate(GET, requestHeaders, None, None) shouldBe PROCEED
    }

    "match only the exact selected date when the origin attests its strength" in {
      val requestHeaders = List(range, `If-Range`(modified))
      evaluate(GET, requestHeaders, None, Some(modified)) shouldBe PROCEED
      evaluate(GET, requestHeaders, None, Some(modified), strongLastModified = true) shouldBe EVALUATE_RANGE
      evaluate(GET, requestHeaders, None, None, strongLastModified = true) shouldBe PROCEED
      evaluate(GET, List(range, `If-Range`(later)), None, Some(modified), strongLastModified = true) shouldBe PROCEED
    }

    "use Range only on GET and leave earlier origin preconditions to the caller" in {
      evaluate(GET, List(range), None, None) shouldBe EVALUATE_RANGE
      evaluate(HEAD, List(range), None, None) shouldBe PROCEED
      evaluate(GET, List(`If-Unmodified-Since`(modified)), None, Some(modified)) shouldBe DELEGATE
      evaluate(OPTIONS, Nil, None, None) shouldBe DELEGATE
    }
  }
}
