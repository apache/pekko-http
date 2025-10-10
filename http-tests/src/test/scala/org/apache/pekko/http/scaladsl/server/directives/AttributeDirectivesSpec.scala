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

package org.apache.pekko.http.scaladsl.server.directives

import org.apache.pekko
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.server._

class AttributeDirectivesSpec extends RoutingSpec {
  "The attribute directive" should {
    val key = AttributeKey[String]("test-key")
    val route = attribute(key) { value =>
      complete(s"The attribute value was [$value]")
    }

    "extract the respective attribute value if a matching attribute is present" in {
      Get("/abc") ~> addAttribute(key, "the-value") ~> route ~> check {
        responseAs[String] shouldEqual "The attribute value was [the-value]"
      }
    }

    "reject if no matching request attribute is present" in {
      Get("/abc") ~> route ~> check {
        rejection shouldBe MissingAttributeRejection(key)
      }
    }

    "reject a request if no header of the given type is present" in {
      Get("abc") ~> route ~> check {
        rejection shouldBe MissingAttributeRejection(key)
      }
    }
  }

  "The optionalAttribute directive" should {
    val key = AttributeKey[String]("test-key")
    lazy val route =
      optionalAttribute(key) {
        _ match {
          case Some(value) =>
            complete(s"The attribute value was [$value]")
          case None =>
            complete(s"The attribute value was not set")
        }
      }

    "extract the attribute if present" in {
      Get("abc") ~> addAttribute(key, "the-value") ~> route ~> check {
        responseAs[String] shouldEqual "The attribute value was [the-value]"
      }
    }

    "extract None if no attribute was present for the given key" in {
      Get("abc") ~> route ~> check {
        responseAs[String] shouldEqual "The attribute value was not set"
      }
    }
  }
}
