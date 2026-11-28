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

class HeaderSpec extends AnyFreeSpec with Matchers {
  "Priority should" - {
    "combine received field lines in order without replacing the raw headers" in {
      // #priority-header
      val request = HttpRequest(headers = List(
        RawHeader("Priority", """u=5, hint="image, font";weight=1.5"""),
        RawHeader("priority", "u=1, i=?0")))
      val receivedPriority = Priority.fromHeaders(request.headers)
      val outgoing = Priority(urgency = Some(5), incremental = Some(true))
      // #priority-header

      val priority = receivedPriority.get
      priority.urgency shouldEqual Some(1)
      priority.incremental shouldEqual Some(false)
      priority.value shouldEqual """u=5, hint="image, font";weight=1.5, u=1, i=?0"""
      request.headers shouldEqual List(
        RawHeader("Priority", """u=5, hint="image, font";weight=1.5"""),
        RawHeader("priority", "u=1, i=?0"))
      request.header[Priority] shouldEqual None
      outgoing.toString shouldEqual "Priority: u=5, i"
      Priority.fromHeaders(List(outgoing)) shouldEqual Some(outgoing)
      outgoing.renderInRequests shouldEqual true
      outgoing.renderInResponses shouldEqual true
    }

    "distinguish omitted hints from explicitly specified defaults on a response" in {
      val omittedIncremental = Priority(3)
      val explicitIncremental = Priority(Some(3), Some(false))
      val incrementalOnly = Priority(None, Some(true))

      Priority.fromHeaders(HttpResponse(headers = List(omittedIncremental)).headers).get.incremental shouldEqual None
      Priority.fromHeaders(HttpResponse(headers = List(explicitIncremental)).headers).get.incremental shouldEqual
      Some(false)
      omittedIncremental.value shouldEqual "u=3"
      explicitIncremental.value shouldEqual "u=3, i=?0"
      incrementalOnly.value shouldEqual "i"
      val receivedIncrementalOnly = Priority.fromHeaders(HttpResponse(headers = List(incrementalOnly)).headers).get
      receivedIncrementalOnly.urgency shouldEqual None
      receivedIncrementalOnly.incremental shouldEqual Some(true)
      Priority.fromHeaders(Nil) shouldEqual None
    }

    "keep a dictionary of opaque extensions when neither known hint is present" in {
      val value = """label="a, b";visible, image=picture/webp, data=:YWJj:, timing=1.25"""
      val priority = Priority.parseFromValueString(s" $value ").toOption.get

      priority.urgency shouldEqual None
      priority.incremental shouldEqual None
      priority.value shouldEqual value
    }

    "read ordinary Structured Field dictionary members with inner lists and parameters" in {
      import pekko.http.impl.model.parser.StructuredFieldDictionary
      import StructuredFieldDictionary.{ BooleanMember, IntegerMember, OpaqueMember }

      val value = """group=(primary "secondary asset";visible);v=1, order=-1, toggled=?1, image=picture/webp, """ +
        """size=2.5, bytes=:YWJj:, text="a \"quoted\", title", order=2"""
      val fields = StructuredFieldDictionary.parse(value).get

      fields.keysIterator.toList shouldEqual List("group", "order", "toggled", "image", "size", "bytes", "text")
      fields("group") shouldEqual OpaqueMember
      fields("toggled") shouldEqual BooleanMember(true)
      fields("order") shouldEqual IntegerMember(2)
    }
  }

  "Cookie factories should" - {
    import pekko.http.javadsl.model.headers.{ Cookie => JavaCookie }

    "accept a plain cookie value" in {
      Cookie("theme", "dark").cookies.head.value shouldEqual "dark"
      JavaCookie.create("theme", "dark").getCookies.iterator().next().value() shouldEqual "dark"
    }

    "reject a semicolon in a cookie value" in {
      an[IllegalArgumentException] should be thrownBy Cookie("theme", ";")
      an[IllegalArgumentException] should be thrownBy JavaCookie.create("theme", ";")
    }

    "reject an empty cookie name" in {
      def rejected(create: => Any) = (the[IllegalArgumentException] thrownBy create).getMessage should
        include("cookie name must not be empty")

      rejected(Cookie("", "dark"))
      rejected(JavaCookie.create("", "dark"))
      rejected(HttpCookiePair("", "dark"))
      rejected(HttpCookiePair.raw("", "dark"))
      rejected(HttpCookie("", "dark"))
      // `copy` goes through the constructor too
      rejected(HttpCookie("theme", "dark").withName(""))
    }
  }

  "Sec-WebSocket-Accept should" - {
    "produce the RFC 6455 handshake example" in {
      `Sec-WebSocket-Accept`.forKey(`Sec-WebSocket-Key`("dGhlIHNhbXBsZSBub25jZQ==")).key shouldEqual
      "s3pPLMBiTxaQ9kYGzzhZRbK+xOo="
    }
  }

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
        summary should startWith("Illegal HTTP header 'Last-Modified': Invalid input 'a'")
        summary should endWith("(line 1, column 1)")
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
      "ignore empty parameter slots while preserving multipart parameters" in {
        MediaType.parse("multipart/form-data;;boundary=x;") shouldEqual Right(
          MediaTypes.`multipart/form-data`.withBoundary("x"))
      }
      "failing parse run" in {
        val Left(List(ErrorInfo(summary, detail))) = MediaType.parse("application//gnutar")
        summary shouldEqual
        "Illegal HTTP header 'Content-Type': Invalid input '/', expected subtype (line 1, column 13)"
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
      "ignore empty parameter slots" in {
        ContentType.parse("text/plain;;charset=UTF-8") shouldEqual Right(
          MediaTypes.`text/plain`.withCharset(HttpCharsets.`UTF-8`))
        ContentType.parse("multipart/form-data;;boundary=x") shouldEqual Right(
          MediaTypes.`multipart/form-data`.withBoundary("x").toContentType)
        ContentType.parse("text/plain ; ") shouldEqual ContentType.parse("text/plain")
        ContentType.parse("text/plain; ; charset=UTF8; ; ") shouldEqual Right(
          MediaTypes.`text/plain`.withCharset(HttpCharsets.`UTF-8`))
        ContentType.parse("""text/plain; ; example="a;b"; charset=UTF8;""") shouldEqual
        ContentType.parse("""text/plain; example="a;b"; charset=UTF8""")
      }
      "render without empty parameter slots" in {
        ContentType.parse("application/json;").map(_.toString) shouldEqual Right("application/json")
      }
      "reject a nonempty parameter without a value" in {
        val Left(List(ErrorInfo(summary, detail))) = ContentType.parse("text/plain; ; invalid;")
        summary shouldEqual
        "Illegal HTTP header 'Content-Type': Invalid input ';', expected tchar, OWS or ws (line 1, column 22)"
        detail shouldEqual
        """text/plain; ; invalid;
            |                     ^""".stripMarginWithNewline("\n")
      }
      "failing parse run" in {
        val Left(List(ErrorInfo(summary, detail))) = ContentType.parse("text/plain, charset=UTF8")
        summary shouldEqual
        "Illegal HTTP header 'Content-Type': Invalid input ',', expected tchar, OWS, ws or 'EOI' (line 1, column 11)"
        detail shouldEqual
        """text/plain, charset=UTF8
            |          ^""".stripMarginWithNewline("\n")
      }
    }
  }

  "Accept should" - {
    "ignore empty slots before parameters and after media ranges" in {
      val expected = Right(Accept(MediaTypes.`text/plain`))
      Seq("text/plain", "text/plain;", "text/plain;;", "text/plain ; ; ").foreach { value =>
        Accept.parseFromValueString(value) shouldEqual expected
      }
    }
    "preserve media parameters and weights while ignoring empty slots" in {
      val Right(parsed) = Accept.parseFromValueString("""text/plain;;example="a;b"; ;q=0.5; ;""")
      parsed shouldEqual Accept(MediaRange(MediaTypes.`text/plain`.withParams(Map("example" -> "a;b")), 0.5f))
      parsed.mediaRanges.head.params shouldEqual Map("example" -> "a;b")
      parsed.mediaRanges.head.qValue shouldEqual 0.5f
    }
    "preserve wildcard parameters, weights and range order" in {
      val Right(parsed) = Accept.parseFromValueString(
        "text/*;;level=1;;q=0.5;, application/json;, */*;;q=0;")
      parsed shouldEqual Accept(
        MediaRanges.`text/*`.withParams(Map("level" -> "1", "q" -> "0.5")),
        MediaRange(MediaTypes.`application/json`),
        MediaRanges.`*/*`.withQValue(0f))
      parsed.mediaRanges.map(_.qValue) shouldEqual Seq(0.5f, 1f, 0f)
      parsed.mediaRanges.map(_.params) shouldEqual Seq(Map("level" -> "1"), Map.empty, Map.empty)
    }
    "render the same value as the equivalent header without empty slots" in {
      val Right(parsed) = Accept.parseFromValueString("text/plain;;q=0.5;, application/json;")
      val Right(canonical) = Accept.parseFromValueString("text/plain;q=0.5, application/json")
      parsed shouldEqual canonical
      parsed.value shouldEqual canonical.value
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
        summary shouldEqual
        "Illegal HTTP header 'Retry-After': Invalid input '1', expected OWS or 'EOI' (line 1, column 2)"
        val Left(List(ErrorInfo(summary2, detail2))) = `Retry-After`.parseFromValueString("-10")
        summary2 should startWith("Illegal HTTP header 'Retry-After': Invalid input '-'")
        summary2 should endWith("(line 1, column 1)")
        detail2 shouldEqual "-10\n^"
        val Left(List(ErrorInfo(summary3, detail3))) = `Retry-After`.parseFromValueString("2015-10-21H07:28:00Z")
        summary3 shouldEqual
        "Illegal HTTP header 'Retry-After': Invalid input '-', expected DIGIT, OWS or 'EOI' (line 1, column 5)"
      }
      "escape control characters in the error line" in {
        // the error line is logged under the default `error-logging-verbosity = full`
        val Left(List(ErrorInfo(summary, detail))) = `Retry-After`.parseFromValueString("12\u001b[31m")
        summary should startWith("Illegal HTTP header value: Invalid input '\\u001b'")
        detail shouldEqual "12\\u001b[31m\n  ^"
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
        Trailer(Seq("X-My-Trailer", "X-My-Other-Trailer")))

      requestHeaders.foreach { header =>
        header shouldBe Symbol("renderInRequests")
      }
    }
  }

  "All response headers should" - {

    "render in response" in {
      val responseHeaders = Vector[HttpHeader](
        `Accept-Ranges`(RangeUnits.Bytes),
        `Accept-Query`(MediaTypes.`application/json`),
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
        Trailer(Seq("X-My-Trailer", "X-My-Other-Trailer")))

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
  "Raw-Request-URI should" - {
    "accept any request target made of visible ASCII" in {
      `Raw-Request-URI`("/def%80%fe%ff").uri shouldEqual "/def%80%fe%ff"
      `Raw-Request-URI`("/a+b=c?d=e&f=%2B#g").uri shouldEqual "/a+b=c?d=e&f=%2B#g"
      // every visible ASCII character, 0x21 to 0x7E
      val allVisible = (0x21 to 0x7E).map(_.toChar).mkString
      `Raw-Request-URI`(allVisible).uri shouldEqual allVisible
    }
    "reject anything that cannot be sent as a request target" in {
      // the value is written into the request line as given: a space, CR or LF would end the target early and let
      // what follows be read as the protocol, a header or a second request
      val e = the[IllegalArgumentException] thrownBy `Raw-Request-URI`("/a HTTP/1.0")
      e.getMessage should include("found U+0020 at index 2")
      an[IllegalArgumentException] should be thrownBy `Raw-Request-URI`("/a\u000d\u000aHost: evil")
      an[IllegalArgumentException] should be thrownBy `Raw-Request-URI`("/a\u000aX: y")
      an[IllegalArgumentException] should be thrownBy `Raw-Request-URI`("/a\u0009b")
      an[IllegalArgumentException] should be thrownBy `Raw-Request-URI`("/a\u0000b")
      an[IllegalArgumentException] should be thrownBy `Raw-Request-URI`("/a\u007fb")
      // rendered a character at a time truncated to a byte, so a character outside ASCII is not sent as itself:
      // U+010D would land on the wire as 0x0D, a CR
      an[IllegalArgumentException] should be thrownBy `Raw-Request-URI`("/a\u010d")
      an[IllegalArgumentException] should be thrownBy `Raw-Request-URI`("/caf\u00e9")
      an[IllegalArgumentException] should be thrownBy `Raw-Request-URI`("")
      // `copy` goes through the constructor too
      an[IllegalArgumentException] should be thrownBy `Raw-Request-URI`("/a").copy(uri = "/a\u000d\u000a")
    }
  }
}
