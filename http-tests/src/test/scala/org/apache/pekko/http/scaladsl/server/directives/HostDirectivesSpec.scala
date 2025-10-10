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

import org.apache.pekko.http.scaladsl.model.headers.Host

import org.scalatest.freespec.AnyFreeSpec

class HostDirectivesSpec extends AnyFreeSpec with GenericRoutingSpec {
  "The 'host' directive" - {
    "in its simple String form should" - {
      "block requests to unmatched hosts" in {
        Get() ~> Host("spray.io") ~> {
          host("spray.com") { completeOk }
        } ~> check { handled shouldEqual false }
      }

      "let requests to matching hosts pass" in {
        Get() ~> Host("spray.io") ~> {
          host("spray.com", "spray.io") { completeOk }
        } ~> check { response shouldEqual Ok }
      }
    }

    "in its simple RegEx form" - {
      "block requests to unmatched hosts" in {
        Get() ~> Host("spray.io") ~> {
          host("hairspray.*".r) { echoComplete }
        } ~> check { handled shouldEqual false }
      }

      "let requests to matching hosts pass and extract the full host" in {
        Get() ~> Host("spray.io") ~> {
          host("spra.*".r) { echoComplete }
        } ~> check { responseAs[String] shouldEqual "spray.io" }
      }
    }

    "in its group RegEx form" - {
      "block requests to unmatched hosts" in {
        Get() ~> Host("spray.io") ~> {
          host("hairspray(.*)".r) { echoComplete }
        } ~> check { handled shouldEqual false }
      }

      "let requests to matching hosts pass and extract the full host" in {
        Get() ~> Host("spray.io") ~> {
          host("spra(.*)".r) { echoComplete }
        } ~> check { responseAs[String] shouldEqual "y.io" }
      }
    }
  }
}
