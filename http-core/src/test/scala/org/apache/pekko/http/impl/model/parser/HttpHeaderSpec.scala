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

package org.apache.pekko.http.impl.model.parser

import org.apache.pekko
import pekko.http.scaladsl.settings.ParserSettings.CookieParsingMode
import pekko.http.impl.model.parser.HeaderParser.Settings
import org.scalatest.matchers.{ MatchResult, Matcher }
import pekko.http.impl.util._
import pekko.http.scaladsl.model.{ HttpHeader, _ }
import headers.{ Trailer => `Trailer`, _ }
import CacheDirectives._
import MediaTypes._
import MediaRanges._
import HttpCharsets._
import HttpEncodings._
import HttpMethods._

import java.net.InetAddress
import scala.collection.immutable.Seq
import pekko.http.scaladsl.model.MediaType.WithOpenCharset
import org.scalatest.exceptions.TestFailedException
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class HttpHeaderSpec extends AnyFreeSpec with Matchers {
  val `application/vnd.spray` = MediaType.applicationBinary("vnd.spray", MediaType.Compressible)
  val PROPFIND = HttpMethod.custom("PROPFIND")

  "The HTTP header model must correctly parse and render the headers" - {

    "Accept" in {
      "Accept: audio/midi;q=0.2, audio/basic" =!=
        Accept(`audio/midi`.withQValue(0.2), `audio/basic`)
      "Accept: text/plain;q=0.5, text/html,\r\n text/css;q=0.8" =!=
        Accept(`text/plain`.withQValue(0.5), `text/html`, `text/css`.withQValue(0.8)).renderedTo(
          "text/plain;q=0.5, text/html, text/css;q=0.8")
      "Accept: text/html, image/gif, image/jpeg, *;q=.2, */*;q=.2" =!=
        Accept(`text/html`, `image/gif`, `image/jpeg`, `*/*`.withQValue(0.2), `*/*`.withQValue(0.2)).renderedTo(
          "text/html, image/gif, image/jpeg, */*;q=0.2, */*;q=0.2")
      "Accept: application/vnd.spray" =!=
        Accept(`application/vnd.spray`)
      "Accept: */*, text/*; foo=bar, custom/custom; bar=\"b>az\"" =!=
        Accept(
          `*/*`,
          MediaRange.custom("text", Map("foo" -> "bar")),
          MediaType.customBinary("custom", "custom", MediaType.Compressible, params = Map("bar" -> "b>az")))
      "Accept: application/*+xml; version=2" =!=
        Accept(MediaType.customBinary("application", "*+xml", MediaType.Compressible, params = Map("version" -> "2")))
    }

    "Accept-Charset" in {
      "Accept-Charset: *" =!= `Accept-Charset`(HttpCharsetRange.`*`)
      "Accept-Charset: UTF-8" =!= `Accept-Charset`(`UTF-8`)
      "Accept-Charset: utf16;q=1" =!= `Accept-Charset`(`UTF-16`).renderedTo("UTF-16")
      "Accept-Charset: utf-8; q=0.5, *" =!= `Accept-Charset`(`UTF-8`.withQValue(0.5), HttpCharsetRange.`*`).renderedTo(
        "UTF-8;q=0.5, *")
      "Accept-Charset: latin1, UTf-16; q=0, *;q=0.8" =!=
        `Accept-Charset`(`ISO-8859-1`, `UTF-16`.withQValue(0), HttpCharsetRange.`*`.withQValue(0.8)).renderedTo(
          "ISO-8859-1, UTF-16;q=0.0, *;q=0.8")
      `Accept-Charset`(`UTF-16`.withQValue(0.234567)).unsafeToString shouldEqual "Accept-Charset: UTF-16;q=0.235"
      "Accept-Charset: UTF-16, unsupported42" =!= `Accept-Charset`(`UTF-16`, HttpCharset.custom("unsupported42"))
    }

    "Access-Control-Allow-Credentials" in {
      "Access-Control-Allow-Credentials: true" =!= `Access-Control-Allow-Credentials`(allow = true)
    }

    "Access-Control-Allow-Headers" in {
      "Access-Control-Allow-Headers: Accept, X-My-Header" =!= `Access-Control-Allow-Headers`("Accept", "X-My-Header")
    }

    "Access-Control-Allow-Methods" in {
      "Access-Control-Allow-Methods: GET, POST" =!= `Access-Control-Allow-Methods`(GET, POST)
      "Access-Control-Allow-Methods: GET, PROPFIND, POST" =!= `Access-Control-Allow-Methods`(GET, PROPFIND, POST)
    }

    "Access-Control-Allow-Origin" in {
      "Access-Control-Allow-Origin: *" =!= `Access-Control-Allow-Origin`.`*`
      "Access-Control-Allow-Origin: null" =!= `Access-Control-Allow-Origin`.`null`
      "Access-Control-Allow-Origin: http://spray.io" =!= `Access-Control-Allow-Origin`("http://spray.io")
      "Access-Control-Allow-Origin: http://pekko.apache.org http://spray.io" =!=
        `Access-Control-Allow-Origin`.forRange(HttpOriginRange("http://pekko.apache.org", "http://spray.io"))
    }

    "Access-Control-Expose-Headers" in {
      "Access-Control-Expose-Headers: Accept, X-My-Header" =!= `Access-Control-Expose-Headers`("Accept", "X-My-Header")
    }

    "Access-Control-Max-Age" in {
      "Access-Control-Max-Age: 3600" =!= `Access-Control-Max-Age`(3600)
    }

    "Access-Control-Request-Headers" in {
      "Access-Control-Request-Headers: Accept, X-My-Header" =!= `Access-Control-Request-Headers`("Accept",
        "X-My-Header")
    }

    "Access-Control-Request-Method" in {
      "Access-Control-Request-Method: POST" =!= `Access-Control-Request-Method`(POST)
      "Access-Control-Request-Method: PROPFIND" =!= `Access-Control-Request-Method`(PROPFIND)
    }

    "Accept-Ranges" in {
      "Accept-Ranges: bytes" =!= `Accept-Ranges`(RangeUnits.Bytes)
      "Accept-Ranges: bytes, sausages" =!= `Accept-Ranges`(RangeUnits.Bytes, RangeUnits.Other("sausages"))
      "Accept-Ranges: none" =!= `Accept-Ranges`()
    }

    "Accept-Encoding" in {
      "Accept-Encoding: compress, gzip, fancy" =!=
        `Accept-Encoding`(compress, gzip, HttpEncoding.custom("fancy"))
      "Accept-Encoding: gzip, identity;q=0.5, *;q=0.0" =!=
        `Accept-Encoding`(gzip, identity.withQValue(0.5), HttpEncodingRange.`*`.withQValue(0))
          .renderedTo("gzip, identity;q=0.5, *;q=0.0")
      "Accept-Encoding: " =!= `Accept-Encoding`()
    }

    "Accept-Language" in {
      "Accept-Language: da, en-gb;q=0.8, en;q=0.7" =!=
        `Accept-Language`(Language("da"), Language("en", "gb").withQValue(0.8f), Language("en").withQValue(0.7f))
      "Accept-Language: de-CH-1901, *;q=0.0" =!=
        `Accept-Language`(Language("de", "CH", "1901"), LanguageRange.`*`.withQValue(0f))
      "Accept-Language: es-419, es" =!= `Accept-Language`(Language("es", "419"), Language("es"))
    }

    "Age" in {
      "Age: 3600" =!= Age(3600)
    }

    "Allow" in {
      "Allow: " =!= Allow()
      "Allow: GET, PUT" =!= Allow(GET, PUT)
      "Allow: GET, PROPFIND, PUT" =!= Allow(GET, PROPFIND, PUT)
    }

    "Authorization" in {
      BasicHttpCredentials("Aladdin", "open sesame").token shouldEqual "QWxhZGRpbjpvcGVuIHNlc2FtZQ=="
      "Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==" =!=
        Authorization(BasicHttpCredentials("Aladdin", "open sesame"))
      "Authorization: bAsIc QWxhZGRpbjpvcGVuIHNlc2FtZQ==" =!=
        Authorization(BasicHttpCredentials("Aladdin", "open sesame")).renderedTo(
          "Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==")
      "Authorization: Fancy QWxhZGRpbjpvcGVuIHNlc2FtZQ==" =!=
        Authorization(GenericHttpCredentials("Fancy", "QWxhZGRpbjpvcGVuIHNlc2FtZQ=="))
      """Authorization: Fancy yes="n:o", nonce=42""" =!=
        Authorization(GenericHttpCredentials("Fancy", Map("nonce" -> "42", "yes" -> "n:o"))).renderedTo(
          """Fancy nonce=42,yes="n:o"""")
      """Authorization: Fancy nonce="4\\2",yes=no""" =!=
        Authorization(GenericHttpCredentials("Fancy", Map("nonce" -> """4\2""", "yes" -> "no")))
      """Authorization: Other empty="",yes=no""" =!=
        Authorization(GenericHttpCredentials("Other", Map("empty" -> "", "yes" -> "no")))
      "Authorization: Basic Qm9iOg==" =!=
        Authorization(BasicHttpCredentials("Bob", ""))
      """Authorization: Digest name=Bob""" =!=
        Authorization(GenericHttpCredentials("Digest", Map("name" -> "Bob")))
      """Authorization: Bearer mF_9.B5f-4.1JqM/""" =!=
        Authorization(OAuth2BearerToken("mF_9.B5f-4.1JqM/"))
      "Authorization: NoParamScheme" =!=
        Authorization(GenericHttpCredentials("NoParamScheme", Map.empty[String, String]))
      "Authorization: NoTokenScheme" =!=
        Authorization(GenericHttpCredentials("NoTokenScheme", ""))
      "Authorization: QVFJQzV3TTJMWTRTZmN3Zk=" =!=
        ErrorInfo(
          "Illegal HTTP header 'Authorization': Invalid input '=', expected tchar, OWS, auth-param, token68 or 'EOI' (line 1, column 23)",
          """QVFJQzV3TTJMWTRTZmN3Zk=
            |                      ^""".stripMarginWithNewline("\n"))
    }

    "Cache-Control" in {
      "Cache-Control: no-cache, max-age=0" =!=
        `Cache-Control`(`no-cache`, `max-age`(0))
      "Cache-Control: private=\"Some-Field\"" =!=
        `Cache-Control`(`private`("Some-Field"))
      "Cache-Control: private, community=\"<UCI>\"" =!=
        `Cache-Control`(`private`(), CacheDirective.custom("community", Some("<UCI>")))
      "Cache-Control: max-age=1234567890123456789" =!=
        `Cache-Control`(`max-age`(Int.MaxValue)).renderedTo("max-age=2147483647")
      "Cache-Control: private, no-cache, no-cache=Set-Cookie, proxy-revalidate" =!=
        `Cache-Control`(`private`(), `no-cache`, `no-cache`("Set-Cookie"), `proxy-revalidate`).renderedTo(
          "private, no-cache, no-cache=\"Set-Cookie\", proxy-revalidate")
      "Cache-Control: no-cache=Set-Cookie" =!=
        `Cache-Control`(`no-cache`("Set-Cookie")).renderedTo("no-cache=\"Set-Cookie\"")
      "Cache-Control: private=\"a,b\", no-cache" =!=
        `Cache-Control`(`private`("a", "b"), `no-cache`)
      "Cache-Control: private, immutable" =!=
        `Cache-Control`(`private`(), immutableDirective)
    }

    "Connection" in {
      "Connection: close" =!= Connection("close")
      "Connection: pipapo, close" =!= Connection("pipapo", "close")
    }

    "Content-Disposition" in {
      "Content-Disposition: form-data" =!= `Content-Disposition`(ContentDispositionTypes.`form-data`)
      "Content-Disposition: attachment; filename=\"file/txt\"; name=\"field1\"" =!=
        `Content-Disposition`(ContentDispositionTypes.attachment, Map("filename" -> "file/txt", "name" -> "field1"))
      "Content-Disposition: attachment; name=\"field1\"; other=\"\"" =!=
        `Content-Disposition`(ContentDispositionTypes.attachment, Map("name" -> "field1", "other" -> ""))
      "Content-Disposition: attachment; filename=\"x=2*2+1(?)\"; filename*=UTF-8''x%3D2%2A2+1%28%E2%82%AC%29" =!=
        `Content-Disposition`(ContentDispositionTypes.attachment, Map("filename" -> "x=2*2+1(€)"))
      "Content-Disposition: attachment; filename*=UTF-8''x%27%3D2%2A2+1%28%E2%82%AC%29; filename=\"x'=2*2+1(?)\"" =!=
        `Content-Disposition`(ContentDispositionTypes.attachment, Map("filename" -> "x'=2*2+1(€)")).renderedTo(
          "attachment; filename=\"x'=2*2+1(?)\"; filename*=UTF-8''x%27%3D2%2A2+1%28%E2%82%AC%29")
      "Content-Disposition: attachment; filename=\"naive\"; filename*=UTF-8''na%C3%AFve" <=!=
        `Content-Disposition`(ContentDispositionTypes.attachment, Map("filename" -> "naive", "filename*" -> "naïve"))
      "Content-Disposition: attachment; filename=\"naive\"; filename*=UTF-8''na%C3%AFve" <=!=
        `Content-Disposition`(ContentDispositionTypes.attachment, Map("filename*" -> "naïve", "filename" -> "naive"))
      "Content-Disposition: attachment; filename*=UTF-8''US-$%20rates" =!=
        `Content-Disposition`(ContentDispositionTypes.attachment, Map("filename" -> "US-$ rates")).renderedTo(
          "attachment; filename=\"US-$ rates\"")
      "Content-Disposition: attachment; filename=\"euro sign.txt\"; filename*=UTF-8''%E2%82%AC%20sign.txt" =!=
        `Content-Disposition`(ContentDispositionTypes.attachment, Map("filename" -> "€ sign.txt")).renderedTo(
          "attachment; filename=\"? sign.txt\"; filename*=UTF-8''%E2%82%AC%20sign.txt")
      "Content-Disposition: attachment; filename*=UTF-8''%E2%82%AC%20sign.txt" =!=
        `Content-Disposition`(ContentDispositionTypes.attachment, Map("filename" -> "€ sign.txt")).renderedTo(
          "attachment; filename=\"? sign.txt\"; filename*=UTF-8''%E2%82%AC%20sign.txt")
      "Content-Disposition: attachment; filename*=ISO-8859-1''t%e9l%e9phone" =!=
        `Content-Disposition`(ContentDispositionTypes.attachment, Map("filename" -> "téléphone")).renderedTo(
          "attachment; filename=\"t?l?phone\"; filename*=UTF-8''t%C3%A9l%C3%A9phone")
      "Content-Disposition: attachment; filename=\"téléphone\"" =!=
        `Content-Disposition`(ContentDispositionTypes.attachment, Map("filename" -> "téléphone")).renderedTo(
          "attachment; filename=\"t?l?phone\"; filename*=UTF-8''t%C3%A9l%C3%A9phone")
      "Content-Disposition: attachment; filename*=UTF-8'en-US'esc-%22%5C%25.x" =!=
        `Content-Disposition`(ContentDispositionTypes.attachment, Map("filename" -> "esc-\"\\%.x")).renderedTo(
          "attachment; filename=\"esc-???.x\"; filename*=UTF-8''esc-%22%5C%25.x")
    }

    "Content-Encoding" in {
      "Content-Encoding: gzip" =!= `Content-Encoding`(gzip)
      "Content-Encoding: compress, pipapo" =!= `Content-Encoding`(compress, HttpEncoding.custom("pipapo"))
    }

    "Content-Length" in {
      "Content-Length: 42" =!= `Content-Length`(42)
      "Content-Length: 12345678901234567890123456789" =!= `Content-Length`(999999999999999999L)
        .renderedTo("999999999999999999")
    }

    "Content-Location" in {
      "Content-Location: https://spray.io/secure" =!= `Content-Location`(Uri("https://spray.io/secure"))
      "Content-Location: /en-us/default.aspx?foo=bar" =!= `Content-Location`(Uri("/en-us/default.aspx?foo=bar"))
      "Content-Location: https://pekko.apache.org/#sec" =!= ErrorInfo(
        "Illegal HTTP header 'Content-Location': requirement failed",
        "Content-Location header URI must not contain a fragment")
    }

    "Content-Type" in {
      "Content-Type: application/pdf" =!=
        `Content-Type`(`application/pdf`)
      "Content-Type: application/json" =!=
        `Content-Type`(`application/json`)
      "Content-Type: text/plain; charset=utf8" =!=
        `Content-Type`(ContentType(`text/plain`, `UTF-8`)).renderedTo("text/plain; charset=UTF-8")
      "Content-Type: text/plain; Charset=utf8" =!=
        `Content-Type`(ContentType(`text/plain`, `UTF-8`)).renderedTo("text/plain; charset=UTF-8")
      "Content-Type: text/plain" =!=
        `Content-Type`(ContentType.WithMissingCharset(MediaTypes.`text/plain`)).renderedTo("text/plain")
      "Content-Type: text/xml2; version=3; charset=windows-1252" =!=
        `Content-Type`(MediaType.customWithOpenCharset("text", "xml2", params = Map("version" -> "3"))
          .withCharset(HttpCharsets.getForKey("windows-1252").get))
      "Content-Type: text/plain; charset=fancy-pants" =!=
        `Content-Type`(`text/plain`.withCharset(HttpCharset.custom("fancy-pants")))
      "Content-Type: multipart/mixed; boundary=ABC123" =!=
        `Content-Type`(`multipart/mixed`.withBoundary("ABC123"))
          .renderedTo("multipart/mixed; boundary=ABC123")
      "Content-Type: multipart/mixed; boundary=\"ABC/123\"" =!=
        `Content-Type`(`multipart/mixed`.withBoundary("ABC/123"))
          .renderedTo("""multipart/mixed; boundary="ABC/123"""")
      "Content-Type: application/*" =!=
        `Content-Type`(MediaType.customBinary("application", "*", MediaType.Compressible,
          allowArbitrarySubtypes = true))
    }

    "Content-Range" in {
      "Content-Range: bytes 42-1233/1234" =!= `Content-Range`(ContentRange(42, 1233, 1234))
      "Content-Range: bytes 42-1233/*" =!= `Content-Range`(ContentRange(42, 1233))
      "Content-Range: bytes */1234" =!= `Content-Range`(ContentRange.Unsatisfiable(1234))
      "Content-Range: bytes */12345678901234567890123456789" =!= `Content-Range`(
        ContentRange.Unsatisfiable(999999999999999999L))
        .renderedTo("bytes */999999999999999999")
    }

    "Cookie (RFC 6265)" in {
      "Cookie: SID=31d4d96e407aad42" =!= Cookie("SID" -> "31d4d96e407aad42")
      "Cookie: SID=31d4d96e407aad42; lang=en>US" =!= Cookie("SID" -> "31d4d96e407aad42", "lang" -> "en>US")
      "Cookie: a=1; b=2" =!= Cookie("a" -> "1", "b" -> "2")
      "Cookie: a=1;b=2" =!= Cookie("a" -> "1", "b" -> "2").renderedTo("a=1; b=2")
      "Cookie: a=1 ;b=2" =!= Cookie("a" -> "1", "b" -> "2").renderedTo("a=1; b=2")

      "Cookie: z=0;a=1,b=2" =!= Cookie("z" -> "0").renderedTo("z=0")
      """Cookie: a=1;b="test"""" =!= Cookie("a" -> "1", "b" -> "test").renderedTo("a=1; b=test")

      "Cookie: a=1; b=f\"d\"c\"; c=xyz" =!= Cookie("a" -> "1", "c" -> "xyz").renderedTo("a=1; c=xyz")
      "Cookie: a=1; b=ä; c=d" =!= Cookie("a" -> "1", "c" -> "d").renderedTo("a=1; c=d")

      "Cookie: a=1,2" =!=
        ErrorInfo(
          "Illegal HTTP header 'Cookie'",
          "Cookie header contained no parsable cookie values.")
    }

    "Cookie (Raw)" in {
      "Cookie: SID=31d4d96e407aad42" =!= Cookie("SID" -> "31d4d96e407aad42").withCookieParsingMode(
        CookieParsingMode.Raw)
      "Cookie: SID=31d4d96e407aad42; lang=en>US" =!= Cookie("SID" -> "31d4d96e407aad42",
        "lang" -> "en>US").withCookieParsingMode(CookieParsingMode.Raw)
      "Cookie: a=1; b=2" =!= Cookie("a" -> "1", "b" -> "2").withCookieParsingMode(CookieParsingMode.Raw)
      "Cookie: a=1;b=2" =!= Cookie("a" -> "1", "b" -> "2").renderedTo("a=1; b=2").withCookieParsingMode(
        CookieParsingMode.Raw)
      "Cookie: a=1 ;b=2" =!= Cookie(List(HttpCookiePair.raw("a" -> "1 "), HttpCookiePair("b" -> "2"))).renderedTo(
        "a=1 ; b=2").withCookieParsingMode(CookieParsingMode.Raw)

      "Cookie: z=0; a=1,b=2" =!= Cookie(List(HttpCookiePair("z" -> "0"),
        HttpCookiePair.raw("a" -> "1,b=2"))).withCookieParsingMode(CookieParsingMode.Raw)
      """Cookie: a=1;b="test"""" =!= Cookie(List(HttpCookiePair("a" -> "1"),
        HttpCookiePair.raw("b" -> "\"test\""))).renderedTo("a=1; b=\"test\"").withCookieParsingMode(
        CookieParsingMode.Raw)
      "Cookie: a=1; b=f\"d\"c\"; c=xyz" =!= Cookie(List(HttpCookiePair("a" -> "1"),
        HttpCookiePair.raw("b" -> "f\"d\"c\""), HttpCookiePair("c" -> "xyz"))).withCookieParsingMode(
        CookieParsingMode.Raw)
      "Cookie: a=1; b=ä; c=d" =!= Cookie(List(HttpCookiePair("a" -> "1"), HttpCookiePair.raw("b" -> "ä"),
        HttpCookiePair("c" -> "d"))).withCookieParsingMode(CookieParsingMode.Raw)
    }

    "Date" in {
      "Date: Wed, 13 Jul 2011 08:12:31 GMT" =!= Date(DateTime(2011, 7, 13, 8, 12, 31))
      "Date: Wed, 13-Jul-2011 08:12:31 GMT" =!= Date(DateTime(2011, 7, 13, 8, 12, 31)).renderedTo(
        "Wed, 13 Jul 2011 08:12:31 GMT")
      "Date: Wed, 13-Jul-11 08:12:31 GMT" =!= Date(DateTime(2011, 7, 13, 8, 12, 31)).renderedTo(
        "Wed, 13 Jul 2011 08:12:31 GMT")
      "Date: Mon, 13-Jul-70 08:12:31 GMT" =!= Date(DateTime(1970, 7, 13, 8, 12, 31)).renderedTo(
        "Mon, 13 Jul 1970 08:12:31 GMT")
      "Date: Fri, 23 Mar 1804 12:11:10 UTC" =!= Date(DateTime(1804, 3, 23, 12, 11, 10)).renderedTo(
        "Fri, 23 Mar 1804 12:11:10 GMT")
    }

    "ETag" in {
      """ETag: "938fz3f83z3z38z"""" =!= ETag("938fz3f83z3z38z", weak = false)
      """ETag: W/"938fz3f83z3z38z"""" =!= ETag("938fz3f83z3z38z", weak = true)
    }

    "Expect" in {
      "Expect: 100-continue" =!= Expect.`100-continue`
    }

    "Expires" in {
      "Expires: Wed, 13 Jul 2011 08:12:31 GMT" =!= Expires(DateTime(2011, 7, 13, 8, 12, 31))
      "Expires: 0" =!= Expires(DateTime.MinValue).renderedTo("Wed, 01 Jan 1800 00:00:00 GMT")
      "Expires: -1" =!= Expires(DateTime.MinValue).renderedTo("Wed, 01 Jan 1800 00:00:00 GMT")
      "Expires: " =!= Expires(DateTime.MinValue).renderedTo("Wed, 01 Jan 1800 00:00:00 GMT")
      "Expires: batman" =!= Expires(DateTime.MinValue).renderedTo("Wed, 01 Jan 1800 00:00:00 GMT")
    }

    "Host" in {
      "Host: www.spray.io:8080" =!= Host("www.spray.io", 8080)
      "Host: spray.io" =!= Host("spray.io")
      "Host: [2001:db8::1]:8080" =!= Host("[2001:db8::1]", 8080)
      "Host: [2001:db8::1]" =!= Host("[2001:db8::1]")
      "Host: [::FFFF:129.144.52.38]" =!= Host("[::FFFF:129.144.52.38]")
      "Host: spray.io:80000" =!= ErrorInfo("Illegal HTTP header 'Host': requirement failed", "Illegal port: 80000")
      "Host: 127.0.0.1:9000" =!= Host("127.0.0.1", 9000)
      "Host: 127.0.0.1" =!= Host("127.0.0.1")
    }

    "If-Match dispatching" in {
      // https://github.com/akka/akka-http/issues/443 Check dispatching for "if-match" does not throw "RuleNotFound"
      import scala.util._
      import org.parboiled2.DynamicRuleHandler
      import org.parboiled2.support.hlist.{ ::, HNil }
      val Failure(cause) =
        Try(HeaderParser.dispatch(null.asInstanceOf[DynamicRuleHandler[HeaderParser, HttpHeader :: HNil]], "if-match"))
      cause.getClass should be(classOf[NullPointerException])
    }

    "If-Match" in {
      """If-Match: *""" =!= `If-Match`.`*`
      """If-Match: "938fz3f83z3z38z"""" =!= `If-Match`(EntityTag("938fz3f83z3z38z"))
      """If-Match: "938fz3f83z3z38z", "0293f34hhv0nc"""" =!=
        `If-Match`(EntityTag("938fz3f83z3z38z"), EntityTag("0293f34hhv0nc"))
    }

    "If-Modified-Since" in {
      "If-Modified-Since: Wed, 13 Jul 2011 08:12:31 GMT" =!= `If-Modified-Since`(DateTime(2011, 7, 13, 8, 12, 31))
      "If-Modified-Since: Tue, 09 May 2017 18:49:57 GMT" =!= `If-Modified-Since`(DateTime(2017, 5, 9, 18, 49, 57))
      "If-Modified-Since: Tue, 9 May 2017 18:49:57 GMT" =!=> "Tue, 09 May 2017 18:49:57 GMT"
      "If-Modified-Since: Tue, 9 May 17 18:49:57 GMT" =!=> "Tue, 09 May 2017 18:49:57 GMT"
      "If-Modified-Since: Sat, 09 May 70 18:49:57 GMT" =!=> "Sat, 09 May 1970 18:49:57 GMT"
      "If-Modified-Since: 0" =!= `If-Modified-Since`(DateTime.MinValue).renderedTo("Wed, 01 Jan 1800 00:00:00 GMT")
    }

    "If-None-Match" in {
      """If-None-Match: *""" =!= `If-None-Match`.`*`
      """If-None-Match: "938fz3f83z3z38z"""" =!= `If-None-Match`(EntityTag("938fz3f83z3z38z"))
      """If-None-Match: "938fz3f83z3z38z", "0293f34hhv0nc"""" =!=
        `If-None-Match`(EntityTag("938fz3f83z3z38z"), EntityTag("0293f34hhv0nc"))
      """If-None-Match: W/"938fz3f83z3z38z"""" =!= `If-None-Match`(EntityTag("938fz3f83z3z38z", weak = true))
    }

    "If-Range" in {
      """If-Range: "abcdefg"""" =!= `If-Range`(Left(EntityTag("abcdefg")))
      """If-Range: Wed, 13 Jul 2011 08:12:31 GMT""" =!= `If-Range`(Right(DateTime(2011, 7, 13, 8, 12, 31)))
    }

    "If-Unmodified-Since" in {
      "If-Unmodified-Since: Wed, 13 Jul 2011 08:12:31 GMT" =!= `If-Unmodified-Since`(DateTime(2011, 7, 13, 8, 12, 31))
    }

    "Last-Modified" in {
      "Last-Modified: Wed, 13 Jul 2011 08:12:31 GMT" =!= `Last-Modified`(DateTime(2011, 7, 13, 8, 12, 31))
    }

    "Location" in {
      "Location: https://spray.io/secure" =!= Location(Uri("https://spray.io/secure"))
      "Location: /en-us/default.aspx" =!= Location(Uri("/en-us/default.aspx"))
      "Location: https://spray.io/{sec}" =!= Location(Uri("https://spray.io/{sec}")).renderedTo(
        "https://spray.io/%7Bsec%7D")
      "Location: https://spray.io/ sec" =!= ErrorInfo(
        "Illegal HTTP header 'Location': Invalid input ' ', " +
        "expected segment, '/', '?', '#' or 'EOI' (line 1, column 18)", "https://spray.io/ sec\n                 ^")
    }

    "Link" in {
      "Link: </?page=2>; rel=next" =!= Link(Uri("/?page=2"), LinkParams.next)
      "Link: <https://spray.io>; rel=next" =!= Link(Uri("https://spray.io"), LinkParams.next)
      """Link: </>; rel=prev, </page/2>; rel="next"""" =!=
        Link(LinkValue(Uri("/"), LinkParams.prev), LinkValue(Uri("/page/2"), LinkParams.next)).renderedTo(
          "</>; rel=prev, </page/2>; rel=next")

      """Link: </>; rel="x.y-z http://spray.io"""" =!= Link(Uri("/"), LinkParams.rel("x.y-z http://spray.io"))
      """Link: </>; title="My Title"""" =!= Link(Uri("/"), LinkParams.title("My Title"))
      """Link: </>; rel=next; title="My Title"""" =!= Link(Uri("/"), LinkParams.next, LinkParams.title("My Title"))
      """Link: </>; anchor="http://example.com"""" =!= Link(Uri("/"), LinkParams.anchor(Uri("http://example.com")))
      """Link: </>; rev=foo; hreflang=de-de; media=print; type=application/json""" =!=
        Link(Uri("/"), LinkParams.rev("foo"), LinkParams.hreflang(Language("de", "de")), LinkParams.media("print"),
          LinkParams.`type`(`application/json`))

      /* RFC 5988 examples */
      """Link: <http://example.com/TheBook/chapter2>; rel="previous"; title="previous chapter"""" =!=
        Link(Uri("http://example.com/TheBook/chapter2"), LinkParams.rel("previous"),
          LinkParams.title("previous chapter"))
          .renderedTo("""<http://example.com/TheBook/chapter2>; rel=previous; title="previous chapter"""")

      """Link: </>; rel="http://example.net/foo"""" =!= Link(Uri("/"), LinkParams.rel("http://example.net/foo"))
        .renderedTo("</>; rel=http://example.net/foo")

      """Link: <http://example.org/>; rel="start http://example.net/relation/other"""" =!= Link(
        Uri("http://example.org/"),
        LinkParams.rel("start http://example.net/relation/other"))

      // only one 'rel=' is allowed, http://tools.ietf.org/html/rfc5988#section-5.3 requires any subsequent ones to be skipped
      "Link: </>; rel=prev; rel=next" =!=> "</>; rel=prev"
    }

    "Origin" in {
      "Origin: null" =!= Origin(Nil)
      "Origin: http://spray.io" =!= Origin("http://spray.io")
    }

    "Proxy-Authenticate" in {
      "Proxy-Authenticate: Basic realm=\"WallyWorld\",attr=\"val>ue\", Fancy realm=\"yeah\"" =!=
        `Proxy-Authenticate`(HttpChallenge("Basic", Some("WallyWorld"), Map("attr" -> "val>ue")),
          HttpChallenge("Fancy", Some("yeah")))
      """Proxy-Authenticate: NTLM TlRMTVNTUAABAAAABzIAAAYABgArAAAACwALACAAAABXT1JLU1RBVElPTkRPTUFJTg==""" =!=
        `Proxy-Authenticate`(HttpChallenge("NTLM", None,
          Map("" -> "TlRMTVNTUAABAAAABzIAAAYABgArAAAACwALACAAAABXT1JLU1RBVElPTkRPTUFJTg==")))
    }

    "Proxy-Authorization" in {
      """Proxy-Authorization: Fancy nonce="4\\2",yes=no""" =!=
        `Proxy-Authorization`(GenericHttpCredentials("Fancy", Map("nonce" -> """4\2""", "yes" -> "no")))
      "Proxy-Authorization: Fancy QWxhZGRpbjpvcGVuIHNlc2FtZQ==" =!=
        `Proxy-Authorization`(GenericHttpCredentials("Fancy", "QWxhZGRpbjpvcGVuIHNlc2FtZQ=="))
    }

    "Referer" in {
      "Referer: https://spray.io/secure" =!= Referer(Uri("https://spray.io/secure"))
      "Referer: /en-us/default.aspx?foo=bar" =!= Referer(Uri("/en-us/default.aspx?foo=bar"))
      "Referer: https://pekko.apache.org/#sec" =!= ErrorInfo(
        "Illegal HTTP header 'Referer': requirement failed",
        "Referer header URI must not contain a fragment")
    }

    "Retry-After" in {
      "Retry-After: 120" =!= `Retry-After`(120)
      "Retry-After: Wed, 21 Oct 2015 07:28:00 GMT" =!= `Retry-After`(DateTime(2015, 10, 21, 7, 28))
    }

    "Server" in {
      "Server: as fghf.fdf/xx" =!= `Server`(Vector(ProductVersion("as"), ProductVersion("fghf.fdf", "xx")))
    }

    "Strict-Transport-Security" in {
      "Strict-Transport-Security: max-age=31536000" =!= `Strict-Transport-Security`(maxAge = 31536000)
      "Strict-Transport-Security: max-age=31536000" =!= `Strict-Transport-Security`(maxAge = 31536000,
        includeSubDomains = false)
      "Strict-Transport-Security: max-age=31536000; includeSubDomains" =!= `Strict-Transport-Security`(
        maxAge = 31536000, includeSubDomains = true)
    }

    "TE" in {
      "TE: chunked" =!= TE(TransferEncodings.chunked)
      "TE: gzip" =!= TE(TransferEncodings.gzip)
      "TE: trailers" =!= TE(TransferEncodings.trailers)
      "TE: someextension" =!= TE(TransferEncodings.Extension("someextension"))
    }

    "Transfer-Encoding" in {
      "Transfer-Encoding: chunked" =!= `Transfer-Encoding`(TransferEncodings.chunked)
      "Transfer-Encoding: gzip" =!= `Transfer-Encoding`(TransferEncodings.gzip)
      "Transfer-Encoding: trailers" =!= `Transfer-Encoding`(TransferEncodings.trailers)
      "Transfer-Encoding: someextension" =!= `Transfer-Encoding`(TransferEncodings.Extension("someextension"))
    }

    "Range" in {
      "Range: bytes=0-1" =!= Range(ByteRange(0, 1))
      "Range: bytes=0-" =!= Range(ByteRange.fromOffset(0))
      "Range: bytes=-1" =!= Range(ByteRange.suffix(1))
      "Range: bytes=0-1, 2-3, -99" =!= Range(ByteRange(0, 1), ByteRange(2, 3), ByteRange.suffix(99))
    }

    "Sec-WebSocket-Accept" in {
      "Sec-WebSocket-Accept: ZGgwOTM0Z2owcmViamRvcGcK" =!= `Sec-WebSocket-Accept`("ZGgwOTM0Z2owcmViamRvcGcK")
    }
    "Sec-WebSocket-Extensions" in {
      "Sec-WebSocket-Extensions: abc" =!=
        `Sec-WebSocket-Extensions`(Vector(WebSocketExtension("abc")))
      "Sec-WebSocket-Extensions: abc, def" =!=
        `Sec-WebSocket-Extensions`(Vector(WebSocketExtension("abc"), WebSocketExtension("def")))
      "Sec-WebSocket-Extensions: abc; param=2; use_y, def" =!=
        `Sec-WebSocket-Extensions`(Vector(WebSocketExtension("abc", Map("param" -> "2", "use_y" -> "")),
          WebSocketExtension("def")))
      "Sec-WebSocket-Extensions: abc; param=\",xyz\", def" =!=
        `Sec-WebSocket-Extensions`(Vector(WebSocketExtension("abc", Map("param" -> ",xyz")), WebSocketExtension("def")))

      // real examples from https://tools.ietf.org/html/draft-ietf-hybi-permessage-compression-19
      "Sec-WebSocket-Extensions: permessage-deflate" =!=
        `Sec-WebSocket-Extensions`(Vector(WebSocketExtension("permessage-deflate")))
      "Sec-WebSocket-Extensions: permessage-deflate; client_max_window_bits; server_max_window_bits=10" =!=
        `Sec-WebSocket-Extensions`(Vector(WebSocketExtension("permessage-deflate",
          Map("client_max_window_bits" -> "", "server_max_window_bits" -> "10"))))
      "Sec-WebSocket-Extensions: permessage-deflate; client_max_window_bits; server_max_window_bits=10, permessage-deflate; client_max_window_bits" =!=
        `Sec-WebSocket-Extensions`(Vector(
          WebSocketExtension("permessage-deflate",
            Map("client_max_window_bits" -> "", "server_max_window_bits" -> "10")),
          WebSocketExtension("permessage-deflate", Map("client_max_window_bits" -> ""))))
    }
    "Sec-WebSocket-Key" in {
      "Sec-WebSocket-Key: c2Zxb3JpbmgyMzA5dGpoMDIzOWdlcm5vZ2luCg==" =!= `Sec-WebSocket-Key`(
        "c2Zxb3JpbmgyMzA5dGpoMDIzOWdlcm5vZ2luCg==")
    }
    "Sec-WebSocket-Protocol" in {
      "Sec-WebSocket-Protocol: chat" =!= `Sec-WebSocket-Protocol`(Vector("chat"))
      "Sec-WebSocket-Protocol: chat, superchat" =!= `Sec-WebSocket-Protocol`(Vector("chat", "superchat"))
    }
    "Sec-WebSocket-Version" in {
      "Sec-WebSocket-Version: 25" =!= `Sec-WebSocket-Version`(Vector(25))
      "Sec-WebSocket-Version: 13, 8, 7" =!= `Sec-WebSocket-Version`(Vector(13, 8, 7))

      "Sec-WebSocket-Version: 255" =!= `Sec-WebSocket-Version`(Vector(255))
      "Sec-WebSocket-Version: 0" =!= `Sec-WebSocket-Version`(Vector(0))
    }

    "Set-Cookie" in {
      "Set-Cookie: SID=\"31d4d96e407aad42\"" =!=
        `Set-Cookie`(HttpCookie("SID", "31d4d96e407aad42")).renderedTo("SID=31d4d96e407aad42")
      "Set-Cookie: SID=31d4d96e407aad42; Domain=example.com; Path=/" =!=
        `Set-Cookie`(HttpCookie("SID", "31d4d96e407aad42", path = Some("/"), domain = Some("example.com")))
      "Set-Cookie: lang=en-US; Expires=Wed, 09 Jun 2021 10:18:14 GMT; Path=/hello" =!=
        `Set-Cookie`(HttpCookie("lang", "en-US", expires = Some(DateTime(2021, 6, 9, 10, 18, 14)),
          path = Some("/hello")))
      "Set-Cookie: name=123; Max-Age=12345; Secure" =!=
        `Set-Cookie`(HttpCookie("name", "123", maxAge = Some(12345), secure = true))
      "Set-Cookie: name=123; HttpOnly; fancyPants" =!=
        `Set-Cookie`(HttpCookie("name", "123", httpOnly = true, extension = Some("fancyPants")))
      "Set-Cookie: foo=bar; domain=example.com; Path=/this is a path with blanks; extension with blanks" =!=
        `Set-Cookie`(HttpCookie("foo", "bar", domain = Some("example.com"), path = Some("/this is a path with blanks"),
          extension = Some("extension with blanks"))).renderedTo(
          "foo=bar; Domain=example.com; Path=/this is a path with blanks; extension with blanks")

      // SameSite values
      "Set-Cookie: name=123; SameSite=Lax" =!=
        `Set-Cookie`(HttpCookie("name", "123").withSameSite(SameSite.Lax))
      "Set-Cookie: name=123; SameSite=Strict" =!=
        `Set-Cookie`(HttpCookie("name", "123").withSameSite(SameSite.Strict))
      "Set-Cookie: name=123; SameSite=None" =!=
        `Set-Cookie`(HttpCookie("name", "123").withSameSite(SameSite.None))
      "Set-Cookie: name=123" =!=
        `Set-Cookie`(HttpCookie("name", "123").withSameSite(None))
      "Set-Cookie: name=123; SameSite=sTRIct" =!=
        `Set-Cookie`(HttpCookie("name", "123").withSameSite(SameSite.Strict)).renderedTo("name=123; SameSite=Strict")
      "Set-Cookie: name=123; SameSite=lAX" =!=
        `Set-Cookie`(HttpCookie("name", "123").withSameSite(SameSite.Lax)).renderedTo("name=123; SameSite=Lax")
      "Set-Cookie: name=123; SameSite=NONE" =!=
        `Set-Cookie`(HttpCookie("name", "123").withSameSite(SameSite.None)).renderedTo("name=123; SameSite=None")
      "Set-Cookie: name=123; SameSite=Wrong" =!=
        ErrorInfo(
          "Illegal HTTP header 'Set-Cookie': Invalid input 'W', expected OWS or same-site-value (line 1, column 20)",
          "name=123; SameSite=Wrong\n                   ^")
      "Set-Cookie: name=123" =!=
        `Set-Cookie`(HttpCookie("name", "123").withSameSite(SameSite("Wrong"))).renderedTo("name=123")

      // test all weekdays
      "Set-Cookie: lang=; Expires=Sun, 07 Dec 2014 00:42:55 GMT; Max-Age=12345" =!=
        `Set-Cookie`(HttpCookie("lang", "", expires = Some(DateTime(2014, 12, 7, 0, 42, 55)), maxAge = Some(12345)))
      "Set-Cookie: lang=; Expires=Sunday, 07 Dec 2014 00:42:55 GMT; Max-Age=12345" =!=
        `Set-Cookie`(HttpCookie("lang", "", expires = Some(DateTime(2014, 12, 7, 0, 42, 55)), maxAge = Some(12345)))
          .renderedTo("lang=; Expires=Sun, 07 Dec 2014 00:42:55 GMT; Max-Age=12345")

      "Set-Cookie: lang=; Expires=Mon, 08 Dec 2014 00:42:55 GMT; Max-Age=12345" =!=
        `Set-Cookie`(HttpCookie("lang", "", expires = Some(DateTime(2014, 12, 8, 0, 42, 55)), maxAge = Some(12345)))
      "Set-Cookie: lang=; Expires=Monday, 08 Dec 2014 00:42:55 GMT; Max-Age=12345" =!=
        `Set-Cookie`(HttpCookie("lang", "", expires = Some(DateTime(2014, 12, 8, 0, 42, 55)), maxAge = Some(12345)))
          .renderedTo("lang=; Expires=Mon, 08 Dec 2014 00:42:55 GMT; Max-Age=12345")

      "Set-Cookie: lang=; Expires=Tue, 09 Dec 2014 00:42:55 GMT; Max-Age=12345" =!=
        `Set-Cookie`(HttpCookie("lang", "", expires = Some(DateTime(2014, 12, 9, 0, 42, 55)), maxAge = Some(12345)))
      "Set-Cookie: lang=; Expires=Tuesday, 09 Dec 2014 00:42:55 GMT; Max-Age=12345" =!=
        `Set-Cookie`(HttpCookie("lang", "", expires = Some(DateTime(2014, 12, 9, 0, 42, 55)), maxAge = Some(12345)))
          .renderedTo("lang=; Expires=Tue, 09 Dec 2014 00:42:55 GMT; Max-Age=12345")

      "Set-Cookie: lang=; Expires=Wed, 10 Dec 2014 00:42:55 GMT; Max-Age=12345" =!=
        `Set-Cookie`(HttpCookie("lang", "", expires = Some(DateTime(2014, 12, 10, 0, 42, 55)), maxAge = Some(12345)))
      "Set-Cookie: lang=; Expires=Wednesday, 10 Dec 2014 00:42:55 GMT; Max-Age=12345" =!=
        `Set-Cookie`(HttpCookie("lang", "", expires = Some(DateTime(2014, 12, 10, 0, 42, 55)), maxAge = Some(12345)))
          .renderedTo("lang=; Expires=Wed, 10 Dec 2014 00:42:55 GMT; Max-Age=12345")

      "Set-Cookie: lang=; Expires=Thu, 11 Dec 2014 00:42:55 GMT; Max-Age=12345" =!=
        `Set-Cookie`(HttpCookie("lang", "", expires = Some(DateTime(2014, 12, 11, 0, 42, 55)), maxAge = Some(12345)))
      "Set-Cookie: lang=; Expires=Thursday, 11 Dec 2014 00:42:55 GMT; Max-Age=12345" =!=
        `Set-Cookie`(HttpCookie("lang", "", expires = Some(DateTime(2014, 12, 11, 0, 42, 55)), maxAge = Some(12345)))
          .renderedTo("lang=; Expires=Thu, 11 Dec 2014 00:42:55 GMT; Max-Age=12345")

      "Set-Cookie: lang=; Expires=Fri, 12 Dec 2014 00:42:55 GMT; Max-Age=12345" =!=
        `Set-Cookie`(HttpCookie("lang", "", expires = Some(DateTime(2014, 12, 12, 0, 42, 55)), maxAge = Some(12345)))
      "Set-Cookie: lang=; Expires=Friday, 12 Dec 2014 00:42:55 GMT; Max-Age=12345" =!=
        `Set-Cookie`(HttpCookie("lang", "", expires = Some(DateTime(2014, 12, 12, 0, 42, 55)), maxAge = Some(12345)))
          .renderedTo("lang=; Expires=Fri, 12 Dec 2014 00:42:55 GMT; Max-Age=12345")

      "Set-Cookie: lang=; Expires=Sat, 13 Dec 2014 00:42:55 GMT; Max-Age=12345" =!=
        `Set-Cookie`(HttpCookie("lang", "", expires = Some(DateTime(2014, 12, 13, 0, 42, 55)), maxAge = Some(12345)))
      "Set-Cookie: lang=; Expires=Saturday, 13 Dec 2014 00:42:55 GMT; Max-Age=12345" =!=
        `Set-Cookie`(HttpCookie("lang", "", expires = Some(DateTime(2014, 12, 13, 0, 42, 55)), maxAge = Some(12345)))
          .renderedTo("lang=; Expires=Sat, 13 Dec 2014 00:42:55 GMT; Max-Age=12345")

      "Set-Cookie: lang=; Expires=Mon, 13 Dec 2014 00:42:55 GMT; Max-Age=12345" =!=
        ErrorInfo("Illegal HTTP header 'Set-Cookie': Illegal weekday in date 2014-12-13T00:42:55",
          "is 'Mon' but should be 'Sat'")

      "Set-Cookie: lang=; Expires=xxxx" =!=
        `Set-Cookie`(HttpCookie("lang", "", expires = Some(DateTime.MinValue)))
          .renderedTo("lang=; Expires=Wed, 01 Jan 1800 00:00:00 GMT")

      "Set-Cookie: lang=; domain=----" =!=
        ErrorInfo(
          "Illegal HTTP header 'Set-Cookie': Invalid input '-', expected OWS or domain-value (line 1, column 15)",
          "lang=; domain=----\n              ^")

      // extra examples from play
      "Set-Cookie: PLAY_FLASH=\"success=found\"; Path=/; HTTPOnly" =!=
        `Set-Cookie`(HttpCookie("PLAY_FLASH", "success=found", path = Some("/"), httpOnly = true))
          .renderedTo("PLAY_FLASH=success=found; Path=/; HttpOnly")
      "Set-Cookie: PLAY_FLASH=; Expires=Sun, 07 Dec 2014 22:48:47 GMT; Path=/; HTTPOnly" =!=
        `Set-Cookie`(HttpCookie("PLAY_FLASH", "", expires = Some(DateTime(2014, 12, 7, 22, 48, 47)), path = Some("/"),
          httpOnly = true))
          .renderedTo("PLAY_FLASH=; Expires=Sun, 07 Dec 2014 22:48:47 GMT; Path=/; HttpOnly")
    }

    "Upgrade" in {
      "Upgrade: abc, def" =!= Upgrade(Vector(UpgradeProtocol("abc"), UpgradeProtocol("def")))
      "Upgrade: abc, def/38.1" =!= Upgrade(Vector(UpgradeProtocol("abc"), UpgradeProtocol("def", Some("38.1"))))

      "Upgrade: websocket" =!= Upgrade(Vector(UpgradeProtocol("websocket")))
    }

    "User-Agent" in {
      "User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_8_3) AppleWebKit/537.31" =!=
        `User-Agent`(ProductVersion("Mozilla", "5.0", "Macintosh; Intel Mac OS X 10_8_3"),
          ProductVersion("AppleWebKit", "537.31"))
      "User-Agent: foo(bar)(baz)" =!=
        `User-Agent`(ProductVersion("foo", "", "bar"), ProductVersion(comment = "baz")).renderedTo("foo (bar) (baz)")
    }

    "WWW-Authenticate" in {
      "WWW-Authenticate: Basic" =!=
        `WWW-Authenticate`(HttpChallenge("Basic", None))
      "WWW-Authenticate: Basic realm=\"WallyWorld\"" =!=
        `WWW-Authenticate`(HttpChallenge("Basic", Some("WallyWorld")))
      "WWW-Authenticate: BaSiC rEaLm=WallyWorld" =!=
        `WWW-Authenticate`(HttpChallenge("BaSiC", "WallyWorld")).renderedTo("BaSiC realm=\"WallyWorld\"")
      "WWW-Authenticate: Basic realm=\"foo<bar\"" =!= `WWW-Authenticate`(HttpChallenge("Basic", Some("foo<bar")))
      """WWW-Authenticate: Digest
                           realm="testrealm@host.com",
                           qop="auth,auth-int",
                           nonce=dcd98b7102dd2f0e8b11d0f600bfb0c093,
                           opaque=5ccc069c403ebaf9f0171e9517f40e41""".stripMarginWithNewline("\r\n") =!=
        `WWW-Authenticate`(HttpChallenge("Digest", "testrealm@host.com",
          Map(
            "nonce" -> "dcd98b7102dd2f0e8b11d0f600bfb0c093",
            "opaque" -> "5ccc069c403ebaf9f0171e9517f40e41",
            "qop" -> "auth,auth-int"))).renderedTo(
          "Digest realm=\"testrealm@host.com\",nonce=dcd98b7102dd2f0e8b11d0f600bfb0c093,opaque=5ccc069c403ebaf9f0171e9517f40e41,qop=\"auth,auth-int\"")
      "WWW-Authenticate: Basic realm=\"WallyWorld\",attr=\"val>ue\", Fancy realm=\"yeah\"" =!=
        `WWW-Authenticate`(HttpChallenge("Basic", Some("WallyWorld"), Map("attr" -> "val>ue")),
          HttpChallenge("Fancy", Some("yeah")))
      "WWW-Authenticate: Basic another=\"val>ue\",attr=value" =!=
        `WWW-Authenticate`(HttpChallenge("Basic", None, Map("another" -> "val>ue", "attr" -> "value")))
      "WWW-Authenticate: Basic attr=value" =!=
        `WWW-Authenticate`(HttpChallenge("Basic", None, Map("attr" -> "value")))
      """WWW-Authenticate: Fancy realm="Secure Area",nonce=42""" =!=
        `WWW-Authenticate`(HttpChallenge("Fancy", Some("Secure Area"), Map("nonce" -> "42")))
      """WWW-Authenticate: NTLM TlRMTVNTUAABAAAABzIAAAYABgArAAAACwALACAAAABXT1JLU1RBVElPTkRPTUFJTg==""" =!=
        `WWW-Authenticate`(HttpChallenge("NTLM", None,
          Map("" -> "TlRMTVNTUAABAAAABzIAAAYABgArAAAACwALACAAAABXT1JLU1RBVElPTkRPTUFJTg==")))
    }

    "X-Forwarded-For" in {
      "X-Forwarded-For: 1.2.3.4" =!= `X-Forwarded-For`(remoteAddress("1.2.3.4"))
      "X-Forwarded-For: 1.2.3.4" <=!= `X-Forwarded-For`(remoteAddress("1.2.3.4", Some(56789)))
      "X-Forwarded-For: 234.123.5.6, 8.8.8.8" =!= `X-Forwarded-For`(remoteAddress("234.123.5.6"),
        remoteAddress("8.8.8.8"))
      "X-Forwarded-For: 1.2.3.4, unknown" =!= `X-Forwarded-For`(remoteAddress("1.2.3.4"), RemoteAddress.Unknown)
      "X-Forwarded-For: 192.0.2.43, 2001:db8:cafe:0:0:0:0:17" =!= `X-Forwarded-For`(remoteAddress("192.0.2.43"),
        remoteAddress("2001:db8:cafe::17"))
      "X-Forwarded-For: 1234:5678:9abc:def1:2345:6789:abcd:ef00" =!= `X-Forwarded-For`(
        remoteAddress("1234:5678:9abc:def1:2345:6789:abcd:ef00"))
      "X-Forwarded-For: 1234:567:9a:d:2:67:abc:ef00" =!= `X-Forwarded-For`(remoteAddress("1234:567:9a:d:2:67:abc:ef00"))
      "X-Forwarded-For: 2001:db8:85a3::8a2e:370:7334" =!=> "2001:db8:85a3:0:0:8a2e:370:7334"
      "X-Forwarded-For: 1:2:3:4:5:6:7:8" =!= `X-Forwarded-For`(remoteAddress("1:2:3:4:5:6:7:8"))
      "X-Forwarded-For: 1:2:3:4:5:6:7:8" <=!= `X-Forwarded-For`(remoteAddress("1:2:3:4:5:6:7:8", Some(9)))
      "X-Forwarded-For: ::2:3:4:5:6:7:8" =!=> "0:2:3:4:5:6:7:8"
      "X-Forwarded-For: ::3:4:5:6:7:8" =!=> "0:0:3:4:5:6:7:8"
      "X-Forwarded-For: ::4:5:6:7:8" =!=> "0:0:0:4:5:6:7:8"
      "X-Forwarded-For: ::5:6:7:8" =!=> "0:0:0:0:5:6:7:8"
      "X-Forwarded-For: ::6:7:8" =!=> "0:0:0:0:0:6:7:8"
      "X-Forwarded-For: ::7:8" =!=> "0:0:0:0:0:0:7:8"
      "X-Forwarded-For: ::8" =!=> "0:0:0:0:0:0:0:8"
      "X-Forwarded-For: 1:2:3:4:5:6:7::" =!=> "1:2:3:4:5:6:7:0"
      "X-Forwarded-For: 1:2:3:4:5:6::" =!=> "1:2:3:4:5:6:0:0"
      "X-Forwarded-For: 1:2:3:4:5::" =!=> "1:2:3:4:5:0:0:0"
      "X-Forwarded-For: 1:2:3:4::" =!=> "1:2:3:4:0:0:0:0"
      "X-Forwarded-For: 1:2:3::" =!=> "1:2:3:0:0:0:0:0"
      "X-Forwarded-For: 1:2::" =!=> "1:2:0:0:0:0:0:0"
      "X-Forwarded-For: 1::" =!=> "1:0:0:0:0:0:0:0"
      "X-Forwarded-For: 1::3:4:5:6:7:8" =!=> "1:0:3:4:5:6:7:8"
      "X-Forwarded-For: 1:2::4:5:6:7:8" =!=> "1:2:0:4:5:6:7:8"
      "X-Forwarded-For: 1:2:3::5:6:7:8" =!=> "1:2:3:0:5:6:7:8"
      "X-Forwarded-For: 1:2:3:4::6:7:8" =!=> "1:2:3:4:0:6:7:8"
      "X-Forwarded-For: 1:2:3:4:5::7:8" =!=> "1:2:3:4:5:0:7:8"
      "X-Forwarded-For: 1:2:3:4:5:6::8" =!=> "1:2:3:4:5:6:0:8"
      "X-Forwarded-For: ::" =!=> "0:0:0:0:0:0:0:0"
      "X-Forwarded-For: 1.2.3.4, pekko.apache.org" =!=
        ErrorInfo(
          "Illegal HTTP header 'X-Forwarded-For': Invalid input 'p', expected WSP, CRLF or addr (line 1, column 10)",
          "1.2.3.4, pekko.apache.org\n         ^")
    }

    "X-Forwarded-Host" in {
      "X-Forwarded-Host: 1.2.3.4" =!= `X-Forwarded-Host`(Uri.Host("1.2.3.4"))
      "X-Forwarded-Host: [2001:db8:cafe:0:0:0:0:17]" =!= `X-Forwarded-Host`(Uri.Host("2001:db8:cafe:0:0:0:0:17"))
      "X-Forwarded-Host: [1234:5678:9abc:def1:2345:6789:abcd:ef00]" =!= `X-Forwarded-Host`(
        Uri.Host("1234:5678:9abc:def1:2345:6789:abcd:ef00"))
      "X-Forwarded-Host: [1234:567:9a:d:2:67:abc:ef00]" =!= `X-Forwarded-Host`(Uri.Host("1234:567:9a:d:2:67:abc:ef00"))
      "X-Forwarded-Host: [1:2:3:4:5:6:7:8]" =!= `X-Forwarded-Host`(Uri.Host("1:2:3:4:5:6:7:8"))
      "X-Forwarded-Host: pekko.apache.org" =!= `X-Forwarded-Host`(Uri.Host("pekko.apache.org"))
      "X-Forwarded-Host: [1:2:3:4::6:7:8]" =!= `X-Forwarded-Host`(Uri.Host("1:2:3:4::6:7:8"))
    }

    "X-Forwarded-Proto" in {
      "X-Forwarded-Proto: http" =!= `X-Forwarded-Proto`("http")
      "X-Forwarded-Proto: https" =!= `X-Forwarded-Proto`("https")
      "X-Forwarded-Proto: pekko" =!= `X-Forwarded-Proto`("pekko")
    }

    "X-Real-Ip" in {
      "X-Real-Ip: 1.2.3.4" =!= `X-Real-Ip`(remoteAddress("1.2.3.4"))
      "X-Real-Ip: 1.2.3.4" <=!= `X-Real-Ip`(remoteAddress("1.2.3.4", Some(56789)))
      "X-Real-Ip: 2001:db8:cafe:0:0:0:0:17" =!= `X-Real-Ip`(remoteAddress("2001:db8:cafe:0:0:0:0:17"))
      "X-Real-Ip: 1234:5678:9abc:def1:2345:6789:abcd:ef00" =!= `X-Real-Ip`(
        remoteAddress("1234:5678:9abc:def1:2345:6789:abcd:ef00"))
      "X-Real-Ip: 1234:567:9a:d:2:67:abc:ef00" =!= `X-Real-Ip`(remoteAddress("1234:567:9a:d:2:67:abc:ef00"))
      "X-Real-Ip: 2001:db8:85a3::8a2e:370:7334" =!=> "2001:db8:85a3:0:0:8a2e:370:7334"
      "X-Real-Ip: 1:2:3:4:5:6:7:8" =!= `X-Real-Ip`(remoteAddress("1:2:3:4:5:6:7:8"))
      "X-Real-Ip: 1:2:3:4:5:6:7:8" <=!= `X-Real-Ip`(remoteAddress("1:2:3:4:5:6:7:8", Some(9)))
      "X-Real-Ip: ::2:3:4:5:6:7:8" =!=> "0:2:3:4:5:6:7:8"
      "X-Real-Ip: ::3:4:5:6:7:8" =!=> "0:0:3:4:5:6:7:8"
      "X-Real-Ip: ::4:5:6:7:8" =!=> "0:0:0:4:5:6:7:8"
      "X-Real-Ip: ::5:6:7:8" =!=> "0:0:0:0:5:6:7:8"
      "X-Real-Ip: ::6:7:8" =!=> "0:0:0:0:0:6:7:8"
      "X-Real-Ip: ::7:8" =!=> "0:0:0:0:0:0:7:8"
      "X-Real-Ip: ::8" =!=> "0:0:0:0:0:0:0:8"
      "X-Real-Ip: 1:2:3:4:5:6:7::" =!=> "1:2:3:4:5:6:7:0"
      "X-Real-Ip: 1:2:3:4:5:6::" =!=> "1:2:3:4:5:6:0:0"
      "X-Real-Ip: 1:2:3:4:5::" =!=> "1:2:3:4:5:0:0:0"
      "X-Real-Ip: 1:2:3:4::" =!=> "1:2:3:4:0:0:0:0"
      "X-Real-Ip: 1:2:3::" =!=> "1:2:3:0:0:0:0:0"
      "X-Real-Ip: 1:2::" =!=> "1:2:0:0:0:0:0:0"
      "X-Real-Ip: 1::" =!=> "1:0:0:0:0:0:0:0"
      "X-Real-Ip: 1::3:4:5:6:7:8" =!=> "1:0:3:4:5:6:7:8"
      "X-Real-Ip: 1:2::4:5:6:7:8" =!=> "1:2:0:4:5:6:7:8"
      "X-Real-Ip: 1:2:3::5:6:7:8" =!=> "1:2:3:0:5:6:7:8"
      "X-Real-Ip: 1:2:3:4::6:7:8" =!=> "1:2:3:4:0:6:7:8"
      "X-Real-Ip: 1:2:3:4:5::7:8" =!=> "1:2:3:4:5:0:7:8"
      "X-Real-Ip: 1:2:3:4:5:6::8" =!=> "1:2:3:4:5:6:0:8"
      "X-Real-Ip: ::" =!=> "0:0:0:0:0:0:0:0"
      "X-Real-Ip: pekko.apache.org" =!=
        ErrorInfo(
          "Illegal HTTP header 'X-Real-Ip': Invalid input 'p', expected ip-v4-address or ip-v6-address (line 1, column 1)",
          "pekko.apache.org\n^")
    }

    "Trailer" in {
      "Trailer: one" =!= `Trailer`(Seq("one"))
      "Trailer: one, two" =!= `Trailer`(Seq("one", "two"))
      "Trailer: ignore,spacing,          between,    values" =!=> "ignore, spacing, between, values"
      "Trailer: Content-MD5 , ETag,  X-Custom-Field" =!= `Trailer`(Seq("Content-MD5", "ETag", "X-Custom-Field"))
        .renderedTo("Content-MD5, ETag, X-Custom-Field")
      "Trailer: content-md5, ETAG, X-Custom-Field" =!= `Trailer`(Seq("content-md5", "ETAG", "X-Custom-Field"))
      "Trailer: " =!= ErrorInfo(
        summary = "Illegal HTTP header 'Trailer': Trailer values must not be empty",
        detail = "No valid header names specified")
      "Trailer: X-Custom-Hash, X-Custom-Signature" =!= `Trailer`(Seq("X-Custom-Hash", "X-Custom-Signature"))
      "Trailer: Content@Md5" =!= ErrorInfo(
        summary =
          "Illegal HTTP header 'Trailer': Invalid input '@', expected tchar, OWS, listSep or 'EOI' (line 1, column 8)",
        detail = "Content@Md5\n       ^")
      "Trailer: Content-Length" =!= ErrorInfo(
        summary = "Illegal HTTP header 'Trailer': Trailer values must not contain forbidden header names",
        detail = "Trailer contained [Content-Length]")
    }

    "RawHeader" in {
      "X-Space-Ranger: no, this rock!" =!= RawHeader("X-Space-Ranger", "no, this rock!")
    }
  }

  "The header parser should" - {
    import HttpHeader._
    "not accept illegal header names" in {
      parse("X:", "a") shouldEqual ParsingResult.Error(ErrorInfo("Illegal HTTP header name", "X:"))
      parse(" X", "a") shouldEqual ParsingResult.Error(ErrorInfo("Illegal HTTP header name", " X"))
    }
    "not accept illegal header values" in {
      parse("Foo", "ba\u0000r") shouldEqual ParsingResult.Error(ErrorInfo(
        "Illegal HTTP header value: Invalid input '\\u0000', expected field-value-char, FWS or 'EOI' (line 1, column 3)",
        "ba\u0000r\n  ^"))
    }
    "allow UTF8 characters in RawHeaders" in {
      parse("Flood-Resistant-Hammerdrill", "árvíztűrő ütvefúrógép") shouldEqual
      ParsingResult.Ok(RawHeader("Flood-Resistant-Hammerdrill", "árvíztűrő ütvefúrógép"), Nil)
    }
    "compress value whitespace into single spaces and trim" in {
      parse("Foo", " b  a \tr\t") shouldEqual ParsingResult.Ok(RawHeader("Foo", "b a r"), Nil)
    }
    "resolve obs-fold occurrences" in {
      parse("Foo", "b\r\n\ta \r\n r") shouldEqual ParsingResult.Ok(RawHeader("Foo", "b a r"), Nil)
    }

    "parse with custom uri parsing mode" in {
      val targetUri = Uri("http://example.org/?abc=def=ghi", Uri.ParsingMode.Relaxed)
      HttpHeader.parse("location", "http://example.org/?abc=def=ghi",
        HeaderParser.Settings(uriParsingMode = Uri.ParsingMode.Relaxed)) shouldEqual
      ParsingResult.Ok(Location(targetUri), Nil)
    }
    "parse content-type with custom media types" in {
      // Override the application/json media type and give it an open instead of fixed charset.
      // This allows us to support various third-party agents which use an explicit charset.
      val openJson: WithOpenCharset = MediaType.customWithOpenCharset("application", "json")

      def checkContentType(headerValue: String, contentType: ContentType) = {
        val customMediaTypes: MediaTypes.FindCustom = {
          case ("application", "json") => Some(openJson)
          case _                       => None
        }
        val headerParserSettings = HeaderParser.Settings(customMediaTypes = customMediaTypes)
        val header = `Content-Type`(contentType)
        HttpHeader.parse("content-type", headerValue, headerParserSettings) shouldEqual ParsingResult.Ok(header, Nil)
        header.unsafeToString shouldEqual s"Content-Type: $headerValue"
      }

      checkContentType("application/json", ContentType.WithMissingCharset(openJson))
      checkContentType("application/json; charset=UTF-8", ContentType(openJson, HttpCharsets.`UTF-8`))
    }
    "fail gracefully for deeply nested header comments" in {
      parse("User-Agent", "(" * 10000).errors.head shouldEqual
      ErrorInfo("Illegal HTTP header 'User-Agent': Illegal header value", "Header comment nested too deeply")
    }

    "not break when header-value is null" in {
      val errors = parse("Content-Disposition", null).errors
      errors should have size 1
      errors.head shouldBe an[ErrorInfo]
    }

  }

  implicit class TestLine(line: String) {
    def =!=(testHeader: TestExample) = testHeader(line)
    def <=!=(header: HttpHeader) = {
      header.unsafeToString shouldEqual line
    }
    def =!=>(expectedRendering: String) = {
      val Array(name, value) = line.split(": ", 2)
      val HttpHeader.ParsingResult.Ok(header, Nil) = HttpHeader.parse(name, value)
      header.unsafeToString shouldEqual header.renderedTo(expectedRendering).rendering("")
    }
  }
  sealed trait TestExample extends (String => Unit)
  implicit class TestHeader(val header: HttpHeader) extends TestExample { outer =>
    def apply(line: String) = {
      val Array(name, value) = line.split(": ", 2)
      val parseResult = HttpHeader.parse(name, value, settings)
      parseResult should renderFromHeaderTo(this, line)
      val HttpHeader.ParsingResult.Ok(parsedHeader, Nil) = parseResult
      try parsedHeader should equal(header)
      catch {
        case e: TestFailedException if parsedHeader.toString == header.unsafeToString =>
          def className[T](t: T): String = scala.reflect.NameTransformer.decode(t.getClass.getName)
          throw new AssertionError(
            s"Test equals failed with equal toString. parsedHeader class was ${className(parsedHeader)}, " +
            s"header class was ${className(header)}", e)
      }
    }
    def rendering(line: String): String = line
    def settings: HeaderParser.Settings = HeaderParser.DefaultSettings
    def renderedTo(expectedRendering: String): TestHeader =
      new TestHeader(header) {
        override def rendering(line: String): String =
          header match {
            case x: ModeledHeader => x.name + ": " + expectedRendering
            case _                => expectedRendering
          }

        override def settings: Settings = outer.settings
      }
    def withCookieParsingMode(mode: CookieParsingMode): TestHeader =
      withParserSettings(Settings(settings.uriParsingMode, mode))

    def withParserSettings(newSettings: HeaderParser.Settings): TestHeader =
      new TestHeader(header) {
        override def rendering(line: String): String = outer.rendering(line)
        override def settings = newSettings
      }
  }
  implicit class TestError(expectedError: ErrorInfo) extends TestExample {
    def apply(line: String) = {
      val Array(name, value) = line.split(": ", 2)
      val HttpHeader.ParsingResult.Ok(_, error :: Nil) = HttpHeader.parse(name, value)
      error shouldEqual expectedError
    }
  }

  def renderFromHeaderTo(header: TestHeader, line: String): Matcher[HttpHeader.ParsingResult] =
    Matcher {
      case HttpHeader.ParsingResult.Ok(h, Nil) =>
        MatchResult(
          h.unsafeToString === header.rendering(line),
          s"doesn't render to '${header.rendering(line)}' but '${h.unsafeToString}'", "XXX")
      case result =>
        val info = result.errors.head
        fail(s"Input `${header.header}` failed to parse:\n${info.summary}\n${info.detail}")
    }

  def remoteAddress(ip: String, port: Option[Int] = None) = RemoteAddress(InetAddress.getByName(ip), port)
}
