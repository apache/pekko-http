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

package org.apache.pekko.http.scaladsl

import org.apache.pekko
import pekko.http.impl.util.PekkoSpecWithMaterializer
import pekko.http.scaladsl.client.RequestBuilding
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.server.Directives
import pekko.http.scaladsl.settings.{ ClientConnectionSettings, ConnectionPoolSettings }

import org.scalatest.concurrent.ScalaFutures

class CustomStatusCodesSpec extends PekkoSpecWithMaterializer with ScalaFutures
    with Directives with RequestBuilding {

  "Http" should {
    "allow registering custom status code" in {
      // #application-custom
      // similarly in Java: `org.apache.pekko.http.javadsl.settings.[...]`
      import org.apache.pekko.http.scaladsl.settings.{ ParserSettings, ServerSettings }

      // define custom status code:
      val LeetCode = StatusCodes.custom(777, "LeetCode", "Some reason", isSuccess = true, allowsEntity = false)

      // add custom method to parser settings:
      val parserSettings = ParserSettings.forServer(system).withCustomStatusCodes(LeetCode)
      val serverSettings = ServerSettings(system).withParserSettings(parserSettings)

      val clientConSettings = ClientConnectionSettings(system).withParserSettings(parserSettings)
      val clientSettings = ConnectionPoolSettings(system).withConnectionSettings(clientConSettings)

      val routes =
        complete(HttpResponse(status = LeetCode))

      // use serverSettings in server:
      val binding = Http().newServerAt("127.0.0.1", 0).withSettings(serverSettings).bind(routes).futureValue

      // use clientSettings in client:
      val request = HttpRequest(uri = s"http://127.0.0.1:${binding.localAddress.getPort}/")
      val response = Http().singleRequest(request, settings = clientSettings)

      // futureValue is a ScalaTest helper:
      response.futureValue.status should ===(LeetCode)
      // #application-custom
      binding.unbind()
    }
  }

}
