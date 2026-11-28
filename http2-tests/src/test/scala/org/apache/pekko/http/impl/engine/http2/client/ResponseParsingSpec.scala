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

import java.nio.charset.StandardCharsets

import scala.util.Try

import org.apache.pekko
import pekko.http.impl.engine.http2.Http2ResponseStatus
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

    def parse(keyValuePairs: Seq[(String, String)], endStream: Boolean = true): Try[HttpResponse] = {
      val settings = ClientConnectionSettings(system)
      val headerParser = HttpHeaderParser(settings.parserSettings, log)
      val encoder = new HPackEncodingSupport {}
      val frame =
        HeadersFrame(1, endStream = endStream, endHeaders = true, encoder.encodeHeaderPairs(keyValuePairs), None)

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
        .map(subStream =>
          Try(ResponseParsing.parseResponse(headerParser, settings.parserSettings, Attributes())(subStream)))
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

    "decode non-ASCII octets in a header value as the HTTP/1.1 parser does" in {
      // HPACK hands out string literals as ISO-8859-1, one char per octet: a pair value here is what was on the wire
      def wire(s: String): String = new String(s.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1)
      val response = parse(Seq(":status" -> "200", "x-a" -> wire("café €"), "x-b" -> "° ü ÿ", "x-c" -> "c")).get
      (response.headers.map(h => h.lowercaseName -> h.value) should contain).allOf(
        "x-a" -> "café €", "x-b" -> "° ü ÿ", "x-c" -> "c")
    }

    "preserve unregistered status codes in typed responses" in {
      val settings = ClientConnectionSettings(system).parserSettings
      val parseTyped = ResponseParsing.parseResponse(HttpHeaderParser(settings, log), settings, Attributes())
      val body = ByteString("response body")

      // 598 and 599 are built-in status codes in Pekko HTTP.
      for (code <- Seq(199, 299, 399, 471, 597)) {
        val headers = ParsedHeadersFrame(1, endStream = false, Seq(":status" -> code.toString), None, None)
        val data = if (code < 200) ByteString.empty else body
        val response = parseTyped(Http2SubStream(headers, OptionVal.None, Left(data), Map.empty))

        response.status.intValue shouldBe code
        response.status.isSuccess shouldBe (code < 400)
        response.status.isRedirection shouldBe (code / 100 == 3)
        response.status.isFailure shouldBe (code >= 400)
        response.status.reason shouldBe ""
        if (code < 200) response.entity shouldBe HttpEntity.Empty
        else response.entity shouldBe HttpEntity.Strict(ContentTypes.`application/octet-stream`, body)
      }
    }

    "prefer built-in statuses over an application registration and honor an unshadowed registration" in {
      val shadowed = StatusCodes.custom(200, "Custom OK", "", isSuccess = false, allowsEntity = false)
      val registered = StatusCodes.custom(471, "Application success", "", isSuccess = true, allowsEntity = true)
      val settings = ClientConnectionSettings(system).parserSettings.withCustomStatusCodes(shadowed, registered)
      val parseTyped = ResponseParsing.parseResponse(HttpHeaderParser(settings, log), settings, Attributes())
      val body = ByteString("response body")

      for (expected <- Seq(StatusCodes.OK, registered)) {
        val headers = ParsedHeadersFrame(1, endStream = false, Seq(":status" -> expected.intValue.toString), None, None)
        val response = parseTyped(Http2SubStream(headers, OptionVal.None, Left(body), Map.empty))
        response.status shouldBe theSameInstanceAs(expected)
        response.entity shouldBe HttpEntity.Strict(ContentTypes.`application/octet-stream`, body)
      }
    }

    "classify ordinary status strings and status values consistently" in {
      for (code <- Seq(100, 102, 103, 199, 200, 204, 304)) {
        Http2ResponseStatus.isInformational(code.toString) shouldBe Http2ResponseStatus.isInformational(code)
        Http2ResponseStatus.isInformational(code) shouldBe (code < 200)
      }
    }

    "classify built-in content-free final statuses separately from informational statuses" in {
      val customStatusCodes = ClientConnectionSettings(system).parserSettings.customStatusCodes
      for (code <- Seq(204, 205, 304)) {
        Http2ResponseStatus.isContentFreeFinal(code.toString, customStatusCodes) shouldBe true
        Http2ResponseStatus.isInformational(code) shouldBe false
      }
      for (code <- Seq(100, 102, 103, 200, 201, 299))
        Http2ResponseStatus.isContentFreeFinal(code.toString, customStatusCodes) shouldBe false
    }

    "parse internally dispatched informational headers with an empty entity" in {
      val response = parse(Seq(":status" -> "103", "link" -> "</style.css>; rel=preload"), endStream = false).get
      response.status shouldBe StatusCodes.EarlyHints
      response.entity shouldBe HttpEntity.Empty
    }

    for ((status, endStream, error) <- Seq(
        ("101", false, "HTTP/2 does not support the 101 status code"),
        ("101", true, "HTTP/2 does not support the 101 status code"),
        ("103", true, "Informational responses must not end the stream"))) {
      s"reject status $status with END_STREAM=$endStream" in {
        val failure = parse(Seq(":status" -> status), endStream).failed.get
        failure shouldBe a[Http2ProtocolException]
        failure.getMessage should include(error)
      }
    }

    // RFC 9113 8.2.1: a field name must not contain control, SP, uppercase or non-ASCII characters and a value must not start or end with
    // whitespace. HeaderDecompression rejects either, and the failure has to name what was wrong rather than surface as
    // the ':status' missing from the headers it dropped.
    "reject a field name containing an uppercase character" in {
      malformedResponseMessage(Seq(":status" -> "200", "X-A" -> "v")) should ===(
        "Malformed response: header field name must not contain control, whitespace, uppercase or non-ASCII characters")
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
