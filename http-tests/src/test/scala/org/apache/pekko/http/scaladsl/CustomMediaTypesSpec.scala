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

import scala.concurrent.duration._

import org.apache.pekko
import pekko.http.scaladsl.client.RequestBuilding
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.model.MediaType.WithFixedCharset
import pekko.http.scaladsl.server.Directives
import pekko.testkit._
import pekko.util.ByteString

import org.scalatest.concurrent.ScalaFutures

class CustomMediaTypesSpec extends PekkoSpec with ScalaFutures
    with Directives with RequestBuilding {

  "Http" should {
    "find media types in a set if they differ in casing" in {
      val set: java.util.Set[MediaType] = new java.util.HashSet
      set.add(MediaTypes.`application/vnd.ms-excel`)
      set.add(MediaTypes.`application/vnd.ms-powerpoint`)
      set.add(MediaTypes.`application/msword`)
      set.add(MediaType.customBinary("application", "x-Akka-TEST", MediaType.NotCompressible))

      set.contains(MediaType.parse("application/msword").right.get) should ===(true)
      set.contains(MediaType.parse("application/MsWord").right.get) should ===(true)
      set.contains(MediaType.parse("application/vnd.ms-POWERPOINT").right.get) should ===(true)
      set.contains(MediaType.parse("application/VnD.MS-eXceL").right.get) should ===(true)
      set.contains(MediaType.parse("application/x-pekko-test").right.get) should ===(true)
      set.contains(MediaType.parse("application/x-Pekko-TEST").right.get) should ===(true)
    }

    "allow registering custom media type" in {
      // #application-custom

      import system.dispatcher

      import pekko.http.scaladsl.settings.ParserSettings
      import pekko.http.scaladsl.settings.ServerSettings

      // define custom media type:
      val utf8 = HttpCharsets.`UTF-8`
      val `application/custom`: WithFixedCharset =
        MediaType.customWithFixedCharset("application", "custom", utf8)

      // add custom media type to parser settings:
      val parserSettings = ParserSettings.forServer(system).withCustomMediaTypes(`application/custom`)
      val serverSettings = ServerSettings(system).withParserSettings(parserSettings)

      val routes = extractRequest { r =>
        complete(r.entity.contentType.toString + " = " + r.entity.contentType.getClass)
      }
      val binding = Http().newServerAt("localhost", 0).withSettings(serverSettings).bind(routes)
      // #application-custom

      val request = Get(s"http://localhost:${binding.futureValue.localAddress.getPort}/").withEntity(
        HttpEntity(`application/custom`, "~~example~=~value~~"))
      val response = Http().singleRequest(request).futureValue

      response.status should ===(StatusCodes.OK)
      val responseBody = response.toStrict(1.second.dilated).futureValue.entity.dataBytes.runFold(ByteString.empty)(
        _ ++ _).futureValue.utf8String
      responseBody should ===(
        "application/custom = class org.apache.pekko.http.scaladsl.model.ContentType$WithFixedCharset")
    }
  }
}
