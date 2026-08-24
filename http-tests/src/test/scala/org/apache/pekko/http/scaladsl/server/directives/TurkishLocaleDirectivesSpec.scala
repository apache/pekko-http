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

import java.util.Locale

import org.apache.pekko
import pekko.http.scaladsl.model.headers.RawHeader
import pekko.http.scaladsl.server._

/**
 * Directives that lower- or upper-case a user supplied name must do so with `Locale.ROOT`,
 * otherwise they break in locales like tr-TR where 'I' does not lowercase to 'i'.
 */
class TurkishLocaleDirectivesSpec extends RoutingSpec {

  "The headerValueByName directive" should {
    "extract a header whose name contains a capital I in the turkish locale" in withTurkishLocale {
      lazy val route = headerValueByName("If-Match") { value => complete(value) }
      Get("abc") ~> RawHeader("If-Match", "\"xyzzy\"") ~> route ~> check {
        responseAs[String] shouldEqual "\"xyzzy\""
      }
    }
  }

  "The overrideMethodWithParameter directive" should {
    "override with a method name containing an i in the turkish locale" in withTurkishLocale {
      lazy val route = overrideMethodWithParameter("_method") {
        get { complete("GET") } ~
        options { complete("OPTIONS") }
      }
      Get("/?_method=options") ~> route ~> check { responseAs[String] shouldEqual "OPTIONS" }
    }
  }

  private def withTurkishLocale(body: => Any): Unit = {
    val previousLocale = Locale.getDefault
    try {
      Locale.setDefault(new Locale("tr", "TR"))
      body
    } finally {
      Locale.setDefault(previousLocale)
    }
  }
}
