/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.scaladsl.server
package directives

import org.apache.pekko
import pekko.http.scaladsl.model.FormData
import pekko.http.scaladsl.server.RoutingSpec

class CompatFormFieldSpec extends RoutingSpec {
  def test(): Unit =
    if (!scala.util.Properties.versionNumberString.startsWith("2.13")) {
      "for one parameter" in {
        val req = Post("/", FormData("num" -> "12"))
        req ~> CompatFormField.oneParameter(echoComplete) ~> check {
          responseAs[String] shouldEqual "12"
        }
        req ~> CompatFormField.oneParameterRoute ~> check {
          responseAs[String] shouldEqual "12"
        }
      }
      "for two parameters" in {
        val req = Post("/", FormData("name" -> "Aloisia", "age" -> "12"))
        req ~> CompatFormField.twoParameters(echoComplete2) ~> check {
          responseAs[String] shouldEqual "Aloisia 12"
        }

        req ~> CompatFormField.twoParametersRoute ~> check {
          responseAs[String] shouldEqual "Aloisia 12"
        }
      }
    } else
      "ignore incompatiblity in 2.13" in succeed

  "FormFieldDirectives" should {
    "be compatible" should {
      test()
    }
  }
}
