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
 */

package org.apache.pekko.http.impl.engine.http2

import org.apache.pekko
import pekko.http.impl.engine.http2.FrameEvent.ParsedHeadersFrame
import pekko.http.impl.engine.parsing.HttpHeaderParser
import pekko.http.scaladsl.model.{ HttpMethod, HttpMethods, Uri }
import pekko.http.scaladsl.model.headers.Expect
import pekko.http.scaladsl.settings.ServerSettings
import pekko.stream.Attributes
import pekko.testkit.PekkoSpec

class Http2ContinuePolicySpec extends PekkoSpec {
  "The HTTP/2 server Continue policy" should {
    val settings = ServerSettings(system).withTransparentHeadRequests(true)

    def shouldContinue(method: HttpMethod): Boolean = {
      val parser = HttpHeaderParser(settings.parserSettings, system.log)
      val demux = new Http2ServerDemux(settings.http2Settings, Nil, upgraded = false, Some(settings -> parser))
      val fields = Seq[(String, AnyRef)](
        ":method" -> method,
        ":scheme" -> "https",
        ":authority" -> Uri.Authority(Uri.Host("example.com")),
        ":path" -> (Uri.Path("/upload"), None),
        "expect" -> Expect.`100-continue`)
      demux.createContinuePredicate(Attributes.none)(ParsedHeadersFrame(1, endStream = false, fields, None, None))
    }

    "allow a POST that is awaiting its entity" in {
      shouldContinue(HttpMethods.POST) shouldBe true
    }

    "skip methods that do not accept an entity, including transparent HEAD" in {
      shouldContinue(HttpMethods.HEAD) shouldBe false
      shouldContinue(HttpMethods.TRACE) shouldBe false
    }
  }
}
