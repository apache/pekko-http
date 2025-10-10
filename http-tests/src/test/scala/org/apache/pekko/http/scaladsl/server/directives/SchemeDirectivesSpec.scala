/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.scaladsl.server
package directives

class SchemeDirectivesSpec extends RoutingSpec {
  "the extractScheme directive" should {
    "extract the Uri scheme" in {
      Put("http://localhost/", "Hello") ~> extractScheme { echoComplete } ~> check {
        responseAs[String] shouldEqual "http"
      }
    }
  }

  """the scheme("http") directive""" should {
    "let requests with an http Uri scheme pass" in {
      Put("http://localhost/", "Hello") ~> scheme("http") { completeOk } ~> check { response shouldEqual Ok }
    }
    "reject requests with an https Uri scheme" in {
      Get("https://localhost/") ~> scheme("http") { completeOk } ~> check {
        rejections shouldEqual List(SchemeRejection("http"))
      }
    }
    "cancel SchemeRejection if other scheme passed" in {
      val route =
        scheme("https") { completeOk } ~
        scheme("http") { reject }

      Put("http://localhost/", "Hello") ~> route ~> check {
        rejections should be(Nil)
      }
    }
  }

  """the scheme("https") directive""" should {
    "let requests with an https Uri scheme pass" in {
      Put("https://localhost/", "Hello") ~> scheme("https") { completeOk } ~> check { response shouldEqual Ok }
    }
    "reject requests with an http Uri scheme" in {
      Get("http://localhost/") ~> scheme("https") { completeOk } ~> check {
        rejections shouldEqual List(SchemeRejection("https"))
      }
    }
  }
}
