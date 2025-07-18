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

package org.apache.pekko.http.scaladsl.model.headers

import org.apache.pekko
import pekko.http.impl.util._

import java.net.InetAddress
import pekko.http.scaladsl.model.{ headers, Trailer => _, _ }
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.immutable

class HeaderSpec extends AnyFreeSpec with Matchers {
  "ModeledCompanion should" - {
    "provide parseFromValueString method" - {
      "successful parse run" in {
        headers.`Cache-Control`.parseFromValueString(
          "private, no-cache, no-cache=Set-Cookie, proxy-revalidate, s-maxage=1000") shouldEqual
        Right(headers.`Cache-Control`(
          CacheDirectives.`private`(),
          CacheDirectives.`no-cache`,
          CacheDirectives.`no-cache`("Set-Cookie"),
          CacheDirectives.`proxy-revalidate`,
          CacheDirectives.`s-maxage`(1000)))
      }
      "failing parse run" in {
        val Left(List(ErrorInfo(summary, detail))) = headers.`Last-Modified`.parseFromValueString("abc")
        summary shouldEqual "Illegal HTTP header 'Last-Modified': Invalid input 'a', expected IMF-fixdate, asctime-date or '0' (line 1, column 1)"
        detail shouldEqual
        """abc
            |^""".stripMarginWithNewline("\n")

      }
    }
  }

  "MediaType should" - {
    "provide parse method" - {
      "successful parse run" in {
        MediaType.parse("application/gnutar") shouldEqual Right(MediaTypes.`application/gnutar`)
      }
      "failing parse run" in {
        val Left(List(ErrorInfo(summary, detail))) = MediaType.parse("application//gnutar")
        summary shouldEqual "Illegal HTTP header 'Content-Type': Invalid input '/', expected subtype (line 1, column 13)"
        detail shouldEqual
        """application//gnutar
            |            ^""".stripMarginWithNewline("\n")
      }
    }
  }

  "ContentType should" - {
    "provide parse method" - {
      "successful parse run" in {
        ContentType.parse("text/plain; charset=UTF8") shouldEqual Right(
          MediaTypes.`text/plain`.withCharset(HttpCharsets.`UTF-8`))
      }
      "failing parse run" in {
        val Left(List(ErrorInfo(summary, detail))) = ContentType.parse("text/plain, charset=UTF8")
        summary shouldEqual "Illegal HTTP header 'Content-Type': Invalid input ',', expected tchar, OWS, ws or 'EOI' (line 1, column 11)"
        detail shouldEqual
        """text/plain, charset=UTF8
            |          ^""".stripMarginWithNewline("\n")
      }
    }
  }

  "Retry-After should" - {
    "provide parseFromValueString method" - {
      "successful parse run" in {
        headers.`Retry-After`.parseFromValueString("120") shouldEqual Right(headers.`Retry-After`(120))
        headers.`Retry-After`.parseFromValueString("Wed, 21 Oct 2015 07:28:00 GMT") shouldEqual
        Right(headers.`Retry-After`(DateTime(2015, 10, 21, 7, 28)))
      }
      "failing parse run" in {
        val Left(List(ErrorInfo(summary, detail))) = `Retry-After`.parseFromValueString("011")
        summary shouldEqual "Illegal HTTP header 'Retry-After': Invalid input '1', expected OWS or 'EOI' (line 1, column 2)"
        val Left(List(ErrorInfo(summary2, detail2))) = `Retry-After`.parseFromValueString("-10")
        summary2 shouldEqual "Illegal HTTP header 'Retry-After': Invalid input '-', expected HTTP-date or delta-seconds (line 1, column 1)"
        val Left(List(ErrorInfo(summary3, detail3))) = `Retry-After`.parseFromValueString("2015-10-21H07:28:00Z")
        summary3 shouldEqual "Illegal HTTP header 'Retry-After': Invalid input '-', expected DIGIT, OWS or 'EOI' (line 1, column 5)"
      }
    }
  }

  "Strict-Transport-Security should" - {
    "provide parseFromValueString method" - {
      "successful parse run" in {
        headers.`Strict-Transport-Security`.parseFromValueString("max-age=30") shouldEqual Right(
          headers.`Strict-Transport-Security`(30, false))
        headers.`Strict-Transport-Security`.parseFromValueString("max-age=30; includeSubDomains") shouldEqual Right(
          headers.`Strict-Transport-Security`(30, true))
        headers.`Strict-Transport-Security`.parseFromValueString("includeSubDomains; max-age=30") shouldEqual Right(
          headers.`Strict-Transport-Security`(30, true))
      }
      "successful parse run with ignored directives" in {
        headers.`Strict-Transport-Security`.parseFromValueString(
          "max-age=30; includeSubDomains; preload; dummy") shouldEqual
        Right(headers.`Strict-Transport-Security`(30, true))
        headers.`Strict-Transport-Security`.parseFromValueString(
          "max-age=30; includeSubDomains; foo=bar; preload") shouldEqual
        Right(headers.`Strict-Transport-Security`(30, true))
      }
      "successful parse run with trailing semicolons" in {
        headers.`Strict-Transport-Security`.parseFromValueString("max-age=30;") shouldEqual Right(
          headers.`Strict-Transport-Security`(30, false))
        headers.`Strict-Transport-Security`.parseFromValueString("max-age=30; includeSubDomains;;;") shouldEqual Right(
          headers.`Strict-Transport-Security`(30, true))
      }
      "failing parse run because of missing max-age directive" in {
        val Left(List(ErrorInfo(summary, detail))) =
          `Strict-Transport-Security`.parseFromValueString("includeSubDomains")
        summary shouldEqual "Illegal HTTP header 'Strict-Transport-Security'"
        detail shouldEqual "exactly one 'max-age' directive required"
      }
      "failing parse run because of too many max-age directives" in {
        val Left(List(ErrorInfo(summary, detail))) =
          `Strict-Transport-Security`.parseFromValueString("max-age=30; max-age=30")
        summary shouldEqual "Illegal HTTP header 'Strict-Transport-Security'"
        detail shouldEqual "exactly one 'max-age' directive required"
      }
      "failing parse run because of too many includeSubDomains directives" in {
        val Left(List(ErrorInfo(summary, detail))) =
          `Strict-Transport-Security`.parseFromValueString("max-age=30; includeSubDomains; includeSubDomains")
        summary shouldEqual "Illegal HTTP header 'Strict-Transport-Security'"
        detail shouldEqual "at most one 'includeSubDomains' directive allowed"
      }
    }
  }

  "All request headers should" - {

    "render in request" in {
      val requestHeaders = Vector[HttpHeader](
        Accept(MediaRanges.`*/*`),
        `Accept-Charset`(HttpCharsetRange(HttpCharsets.`UTF-8`)),
        `Accept-Encoding`(HttpEncodingRange(HttpEncodings.gzip)),
        `Accept-Language`(LanguageRange(Language("sv_SE"))),
        `Access-Control-Request-Headers`("Host"),
        `Access-Control-Request-Method`(HttpMethods.GET),
        Authorization(BasicHttpCredentials("johan", "correcthorsebatterystaple")),
        `Cache-Control`(CacheDirectives.`max-age`(3000)),
        Connection("upgrade"),
        `Content-Length`(2000),
        `Content-Disposition`(ContentDispositionTypes.inline),
        `Content-Encoding`(HttpEncodings.gzip),
        `Content-Range`(ContentRange.Default(1, 20, None)),
        `Content-Type`(ContentTypes.`text/xml(UTF-8)`),
        Cookie("cookie", "with-chocolate"),
        Date(DateTime(2016, 2, 4, 9, 9, 0)),
        Expect.`100-continue`,
        Host("example.com"),
        `If-Match`(EntityTag("hash")),
        `If-Modified-Since`(DateTime(2016, 2, 4, 9, 9, 0)),
        `If-None-Match`(EntityTagRange(EntityTag("hashhash"))),
        `If-Range`(DateTime(2016, 2, 4, 9, 9, 0)),
        `If-Unmodified-Since`(DateTime(2016, 2, 4, 9, 9, 0)),
        `Last-Event-ID`("123"),
        Link(Uri("http://example.com"), LinkParams.`title*`("example")),
        Origin(HttpOrigin("http", Host("example.com"))),
        `Proxy-Authorization`(BasicHttpCredentials("johan", "correcthorsebatterystaple")),
        Range(RangeUnits.Bytes, Vector(ByteRange(1, 1024))),
        Referer(Uri("http://example.com/")),
        `Sec-WebSocket-Extensions`(Vector(WebSocketExtension("permessage-deflate"),
          WebSocketExtension("client_max_window_bits"))),
        `Sec-WebSocket-Protocol`(Vector("chat", "superchat")),
        `Sec-WebSocket-Key`("dGhlIHNhbXBsZSBub25jZQ"),
        `Sec-WebSocket-Version`(Vector(13)),
        TE(TransferEncodings.trailers),
        `Transfer-Encoding`(TransferEncodings.chunked),
        Upgrade(Vector(UpgradeProtocol("HTTP", Some("2.0")))),
        `User-Agent`("Pekko HTTP Client 2.4"),
        `X-Forwarded-For`(RemoteAddress(InetAddress.getByName("192.168.0.1"))),
        `X-Forwarded-Host`(Uri.Host(InetAddress.getByName("192.168.0.2"))),
        `X-Forwarded-Proto`("https"),
        `X-Real-Ip`(RemoteAddress(InetAddress.getByName("192.168.1.1"))),
        Trailer(immutable.Seq("X-My-Trailer", "X-My-Other-Trailer")))

      requestHeaders.foreach { header =>
        header shouldBe Symbol("renderInRequests")
      }
    }
  }

  "All response headers should" - {

    "render in response" in {
      val responseHeaders = Vector[HttpHeader](
        `Accept-Ranges`(RangeUnits.Bytes),
        `Access-Control-Allow-Credentials`(true),
        `Access-Control-Allow-Headers`("X-Custom"),
        `Access-Control-Allow-Methods`(HttpMethods.GET),
        `Access-Control-Allow-Origin`(HttpOrigin("http://example.com")),
        `Access-Control-Expose-Headers`("X-Custom"),
        `Access-Control-Max-Age`(2000),
        Age(2000),
        Allow(HttpMethods.GET),
        `Cache-Control`(CacheDirectives.`no-cache`),
        Connection("close"),
        `Content-Length`(2000),
        `Content-Disposition`(ContentDispositionTypes.inline),
        `Content-Encoding`(HttpEncodings.gzip),
        `Content-Range`(ContentRange.Default(1, 20, None)),
        `Content-Type`(ContentTypes.`text/xml(UTF-8)`),
        Date(DateTime(2016, 2, 4, 9, 9, 0)),
        ETag("suchhashwow"),
        Expires(DateTime(2016, 2, 4, 9, 9, 0)),
        `Last-Modified`(DateTime(2016, 2, 4, 9, 9, 0)),
        Link(Uri("http://example.com"), LinkParams.`title*`("example")),
        Location(Uri("http://example.com")),
        `Proxy-Authenticate`(HttpChallenge("Basic", Some("example.com"))),
        `Sec-WebSocket-Accept`("dGhlIHNhbXBsZSBub25jZQ"),
        `Sec-WebSocket-Extensions`(Vector(WebSocketExtension("foo"))),
        `Sec-WebSocket-Version`(Vector(13)),
        Server("Pekko-HTTP/2.4"),
        `Set-Cookie`(HttpCookie("sessionId", "b0eb8b8b3ad246")),
        `Transfer-Encoding`(TransferEncodings.chunked),
        Upgrade(Vector(UpgradeProtocol("HTTP", Some("2.0")))),
        `WWW-Authenticate`(HttpChallenge("Basic", Some("example.com"))),
        `Retry-After`(120),
        Trailer(immutable.Seq("X-My-Trailer", "X-My-Other-Trailer")))

      responseHeaders.foreach { header =>
        header shouldBe Symbol("renderInResponses")
      }
    }
  }
  "RawHeader should" - {
    "check for valid arguments" - {
      "successful parse run" in {
        RawHeader("foo", "bar").toString shouldEqual "foo: bar"
      }
      "failing parse run" in {
        an[IllegalArgumentException] should be thrownBy RawHeader(null, "bar")
        an[IllegalArgumentException] should be thrownBy RawHeader("foo", null)
      }
    }
  }
}
