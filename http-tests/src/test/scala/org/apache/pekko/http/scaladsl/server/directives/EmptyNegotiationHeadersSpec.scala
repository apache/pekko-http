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

package org.apache.pekko.http.scaladsl.server.directives

import org.apache.pekko
import pekko.http.scaladsl.model.{ HttpCharsets, HttpHeader }
import pekko.http.scaladsl.model.headers.{ `Accept-Charset`, `Accept-Language`, Language }
import pekko.http.scaladsl.server.RoutingSpec

class EmptyNegotiationHeadersSpec extends RoutingSpec {
  "Response negotiation with an empty header" should {
    "parse an empty Accept-Charset and keep the default UTF-8 response" in {
      val empty = HttpHeader.parse("Accept-Charset", "") match {
        case HttpHeader.ParsingResult.Ok(header: `Accept-Charset`, Nil) => header
        case other                                                      => fail(s"Expected a typed empty Accept-Charset, got $other")
      }
      empty.charsetRanges shouldBe Nil
      empty.value shouldBe ""
      empty shouldBe `Accept-Charset`(Seq.empty)

      val route = complete("hello")
      Get() ~> route ~> check {
        responseEntity.contentType.charsetOption shouldBe Some(HttpCharsets.`UTF-8`)
        responseAs[String] shouldBe "hello"
      }
      Get().addHeader(empty) ~> route ~> check {
        responseEntity.contentType.charsetOption shouldBe Some(HttpCharsets.`UTF-8`)
        responseAs[String] shouldBe "hello"
      }
      Get().addHeader(`Accept-Charset`(HttpCharsets.`ISO-8859-1`)) ~> route ~> check {
        responseEntity.contentType.charsetOption shouldBe Some(HttpCharsets.`ISO-8859-1`)
        responseAs[String] shouldBe "hello"
      }
    }

    "parse an empty Accept-Language and keep the first offered language" in {
      val empty = HttpHeader.parse("Accept-Language", "") match {
        case HttpHeader.ParsingResult.Ok(header: `Accept-Language`, Nil) => header
        case other                                                       => fail(s"Expected a typed empty Accept-Language, got $other")
      }
      empty.languages shouldBe Nil
      empty.value shouldBe ""
      empty shouldBe `Accept-Language`(Seq.empty)

      val first = Language("en")
      val second = Language("de")
      val route = selectPreferredLanguage(first, second) { language => complete(language.toString) }
      Get()                                      ~> route ~> check { responseAs[String] shouldBe "en" }
      Get().addHeader(empty)                     ~> route ~> check { responseAs[String] shouldBe "en" }
      Get().addHeader(`Accept-Language`(second)) ~> route ~> check { responseAs[String] shouldBe "de" }
    }
  }
}
