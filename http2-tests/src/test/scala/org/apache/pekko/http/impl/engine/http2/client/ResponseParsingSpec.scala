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

package org.apache.pekko.http.impl.engine.http2.client

import scala.util.Try

import org.apache.pekko
import pekko.http.impl.engine.http2.{ HPackEncodingSupport, Http2SubStream }
import pekko.http.impl.engine.http2.FrameEvent._
import pekko.http.impl.engine.http2.Http2Compliance.Http2ProtocolException
import pekko.http.impl.engine.http2.hpack.HeaderDecompression
import pekko.http.impl.engine.parsing.HttpHeaderParser
import pekko.http.impl.util.PekkoSpecWithMaterializer
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.settings.ClientConnectionSettings
import pekko.stream.Attributes
import pekko.stream.scaladsl.{ Sink, Source }
import pekko.util.{ ByteString, OptionVal }

class ResponseParsingSpec extends PekkoSpecWithMaterializer {
  "ResponseParsing" should {

    def parse(keyValuePairs: Seq[(String, String)]): Try[HttpResponse] = {
      val settings = ClientConnectionSettings(system)
      val headerParser = HttpHeaderParser(settings.parserSettings, log)
      val encoder = new HPackEncodingSupport {}
      val frame = HeadersFrame(1, endStream = true, endHeaders = true, encoder.encodeHeaderPairs(keyValuePairs), None)

      Source.single(frame)
        .via(new HeaderDecompression(headerParser, settings.parserSettings, settings.http2Settings.maxHeaderListSize))
        .map { // emulate demux
          case headers: ParsedHeadersFrame =>
            Http2SubStream(
              initialHeaders = headers,
              trailingHeaders = OptionVal.None,
              data = Right(Source.empty[ByteString]),
              correlationAttributes = Map.empty)
        }
        .map(subStream => Try(ResponseParsing.parseResponse(headerParser, settings.parserSettings, Attributes())(subStream)))
        .runWith(Sink.head)
        .futureValue
    }

    def malformedResponseMessage(keyValuePairs: Seq[(String, String)]): String =
      parse(keyValuePairs).failed.get match {
        case ex: Http2ProtocolException => ex.getMessage
        case other                      => fail(s"Expected an Http2ProtocolException but got $other")
      }

    "parse a response with lowercase field names" in {
      val response = parse(Seq(":status" -> "200", "x-a" -> "v")).get
      response.status should ===(StatusCodes.OK)
      response.headers.map(h => h.lowercaseName -> h.value) should contain("x-a" -> "v")
    }

    // RFC 9113 8.2.1: a field name must not contain uppercase characters and a value must not start or end with
    // whitespace. HeaderDecompression rejects either, and the failure has to name what was wrong rather than surface as
    // the ':status' missing from the headers it dropped.
    "reject a field name containing an uppercase character" in {
      malformedResponseMessage(Seq(":status" -> "200", "X-A" -> "v")) should ===(
        "Malformed response: header field name must not contain uppercase characters")
    }

    "reject a field value with leading or trailing whitespace" in {
      for (value <- Seq(" v", "v ", "\tv", "v\t"))
        malformedResponseMessage(Seq(":status" -> "200", "x-a" -> value)) should ===(
          "Malformed response: header field value must not start or end with whitespace")
    }

    "reject a field value containing CR, LF or NUL" in {
      malformedResponseMessage(Seq(":status" -> "200", "x-a" -> "a\r\nb")) should ===(
        "Malformed response: header field value must not contain CR, LF or NUL")
    }
  }
}
