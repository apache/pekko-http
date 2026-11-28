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

package org.apache.pekko.http.scaladsl.marshalling

import scala.concurrent.Await
import scala.concurrent.duration._

import org.apache.pekko
import pekko.http.impl.util._
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.model.HttpCharsets._
import pekko.http.scaladsl.model.MediaTypes._
import pekko.http.scaladsl.server.CharsetNegotiator
import pekko.http.scaladsl.server.ContentNegotiator.Alternative
import pekko.http.scaladsl.server.MediaTypeNegotiator
import pekko.http.scaladsl.server.util.VarArgsFunction1
import pekko.http.scaladsl.util.FastFuture._
import pekko.util.ByteString

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class ContentNegotiationSpec extends AnyFreeSpec with Matchers {
  "Content Negotiation should work properly for requests with header(s)" - {
    "(without headers)".test { accept =>
      accept(`text/plain`.withCharset(`UTF-16`)) should select(`text/plain`.withCharset(`UTF-16`))
      accept(`text/plain`, `text/html`) should select(`text/plain`.withCharset(`UTF-8`))
      accept(`text/html`, `text/plain`) should select(`text/html`.withCharset(`UTF-8`))
    }

    "Accept: */*".test { accept =>
      accept(`text/plain`) should select(`text/plain`.withCharset(`UTF-8`))
      accept(`text/plain`.withCharset(`UTF-16`)) should select(`text/plain`.withCharset(`UTF-16`))
    }

    "Accept: */*;q=.8".test { accept =>
      accept(`text/plain`) should select(`text/plain`.withCharset(`UTF-8`))
      accept(`text/plain`.withCharset(`UTF-16`)) should select(`text/plain`.withCharset(`UTF-16`))
    }

    "Accept: text/*".test { accept =>
      accept(`text/plain`) should select(`text/plain`.withCharset(`UTF-8`))
      accept(`text/xml`.withCharset(`UTF-16`)) should select(`text/xml`.withCharset(`UTF-16`))
      accept(`audio/ogg`) should reject
    }

    "Accept: text/*;q=.8".test { accept =>
      accept(`text/plain`) should select(`text/plain`.withCharset(`UTF-8`))
      accept(`text/xml`.withCharset(`UTF-16`)) should select(`text/xml`.withCharset(`UTF-16`))
      accept(`audio/ogg`) should reject
    }

    "Accept: text/*;q=0".test { accept =>
      accept(`text/plain`) should reject
      accept(`text/xml`.withCharset(`UTF-16`)) should reject
      accept(`audio/ogg`) should reject
    }

    "Accept: text/*, application/json;q=0.8, text/plain;q=0.5".test { accept =>
      accept(`text/plain`, `application/json`) should select(`application/json`)
    }

    "Accept-Charset: UTF-16".test { accept =>
      accept(`text/plain`) should select(`text/plain`.withCharset(`UTF-16`))
      accept(`text/plain`.withCharset(`UTF-8`)) should reject
    }

    "manually created Accept-Charset: UTF-16" in testHeaders(
      headers.`Accept-Charset`(Vector(HttpCharsets.`UTF-16`.toRange))) { accept =>
      accept(`text/plain`) should select(`text/plain`.withCharset(`UTF-16`))
      accept(`text/plain`.withCharset(`UTF-8`)) should reject
    }

    "Accept-Charset: UTF-16, UTF-8".test { accept =>
      accept(`text/plain`) should select(`text/plain`.withCharset(`UTF-16`))
      accept(`text/plain`.withCharset(`UTF-8`)) should select(`text/plain`.withCharset(`UTF-8`))
    }

    "Accept-Charset: UTF-8;q=.2, UTF-16".test { accept =>
      accept(`text/plain`) should select(`text/plain`.withCharset(`UTF-16`))
      accept(`text/plain`.withCharset(`UTF-8`)) should select(`text/plain`.withCharset(`UTF-8`))
    }

    "Accept-Charset: ISO-8859-1;q=.2".test { accept =>
      accept(`text/plain`) should select(`text/plain`.withCharset(`ISO-8859-1`))
      accept(`text/plain`.withCharset(`UTF-8`)) should reject
    }

    "Accept-Charset: latin1;q=.1, UTF-8;q=.2".test { accept =>
      accept(`text/plain`) should select(`text/plain`.withCharset(`UTF-8`))
      accept(`text/plain`.withCharset(`UTF-8`)) should select(`text/plain`.withCharset(`UTF-8`))
    }

    "Accept-Charset: *".test { accept =>
      accept(`text/plain`) should select(`text/plain`.withCharset(`UTF-8`))
      accept(`text/plain`.withCharset(`UTF-16`)) should select(`text/plain`.withCharset(`UTF-16`))
    }

    "Accept-Charset: *;q=0".test { accept =>
      accept(`text/plain`) should reject
      accept(`text/plain`.withCharset(`UTF-16`)) should reject
    }

    "Accept-Charset: UTF-8;q=0".test { accept =>
      accept(`text/plain`) should reject
      accept(`text/plain`.withCharset(`UTF-8`)) should reject
    }

    "Accept-Charset: UTF-8;q=0, *;q=0.5".test { accept =>
      accept(`text/plain`) should select(`text/plain`.withCharset(`UTF-16`))
      accept(`text/plain`.withCharset(`UTF-8`)) should reject
    }

    "Accept-Charset: UTF-16;q=0.3, *;q=0.9".test { accept =>
      accept(`text/plain`) should select(`text/plain`.withCharset(`UTF-8`))
    }

    "Accept-Charset: UTF-8;q=0.3, *;q=0.9".test { accept =>
      accept(`text/plain`) should select(`text/plain`.withCharset(`UTF-16`))
    }

    "Accept-Charset: UTF-16;q=0.9, *;q=0.3".test { accept =>
      accept(`text/plain`) should select(`text/plain`.withCharset(`UTF-16`))
    }

    "Accept-Charset: UTF-16;q=0.5, *;q=0.5".test { accept =>
      accept(`text/plain`) should select(`text/plain`.withCharset(`UTF-16`))
    }

    """Accept: text/plain, image/gif;q=0.5
      |Accept-Charset: *;q=0""".test { accept =>
      accept(`text/plain`, `image/gif`) should select(`image/gif`)
    }

    "Accept-Charset: *;q=0.1".test { accept =>
      accept(`text/plain`) should select(`text/plain`.withCharset(`UTF-8`))
      accept(`image/gif`) should select(`image/gif`)
    }

    "Accept-Charset: us;q=0.1,*;q=0".test { accept =>
      accept(`text/plain`) should select(`text/plain`.withCharset(`US-ASCII`))
      accept(`text/plain`.withCharset(`UTF-8`)) should reject
    }

    "Accept-Charset: UTF-8, *;q=0.8, us;q=0.1".test { accept =>
      accept(
        `text/plain`.withCharset(`US-ASCII`),
        `text/plain`.withCharset(`ISO-8859-1`)) should select(`text/plain`.withCharset(`ISO-8859-1`))
    }

    "Accept: text/xml, text/html;q=.5".test { accept =>
      accept(`text/plain`) should reject
      accept(`text/xml`) should select(`text/xml`.withCharset(`UTF-8`))
      accept(`text/html`) should select(`text/html`.withCharset(`UTF-8`))
      accept(`text/html`, `text/xml`) should select(`text/xml`.withCharset(`UTF-8`))
      accept(`text/xml`, `text/html`) should select(`text/xml`.withCharset(`UTF-8`))
      accept(`text/plain`, `text/xml`) should select(`text/xml`.withCharset(`UTF-8`))
      accept(`text/plain`, `text/html`) should select(`text/html`.withCharset(`UTF-8`))
    }

    """Accept: text/html, text/plain;q=0.8, application/*;q=.5, *;q= .2
      |Accept-Charset: UTF-16""".test { accept =>
      accept(`text/plain`, `text/html`, `audio/ogg`) should select(`text/html`.withCharset(`UTF-16`))
      accept(`text/plain`, `text/html`.withCharset(`UTF-8`), `audio/ogg`) should select(
        `text/plain`.withCharset(`UTF-16`))
      accept(`audio/ogg`, `application/javascript`, `text/plain`.withCharset(`UTF-8`)) should select(
        `application/javascript`.withCharset(`UTF-16`))
      accept(`image/gif`, `application/javascript`) should select(`application/javascript`.withCharset(`UTF-16`))
      accept(`image/gif`, `audio/ogg`) should select(`image/gif`)
    }

    "Accept: text/xml, text/plain".test { accept =>
      accept(`text/plain`.withCharset(`UTF-16`)) should select(`text/plain`.withCharset(`UTF-16`))
      accept(`text/plain`, `text/xml`) should select(`text/plain`.withCharset(`UTF-8`))
      accept(`text/xml`, `text/plain`) should select(`text/xml`.withCharset(`UTF-8`))
    }

    /** when both acceptable media types are equally specific and have the same qValue, we choose to send the first */
    "Accept: text/html;level=1, text/html;format=flowed".test { accept =>
      accept(`text/html`.withParams(Map("level" -> "1")),
        `text/html`.withParams(Map("format" -> "flowed"))) should select(
        `text/html`.withParams(Map("level" -> "1")).withCharset(`UTF-8`))
    }

    /** when an acceptable media type is more specific, it is matched even when it has a lower qValue */
    "Accept: text/html;format=flowed;q=0.5, text/html;q=1".test { accept =>
      accept(`text/html`.withParams(Map("level" -> "1")),
        `text/html`.withParams(Map("format" -> "flowed"))) should select(
        `text/html`.withParams(Map("level" -> "1")).withCharset(`UTF-8`))
    }

    /** when both acceptable media types are available as separate marshallers and equally specific, we must select the one with the highest qValue */
    "Accept: text/html;level=1;q=0.4, text/html;format=flowed;q=0.7".test { accept =>
      accept(`text/html`.withParams(Map("level" -> "1")),
        `text/html`.withParams(Map("format" -> "flowed"))) should select(
        `text/html`.withParams(Map("format" -> "flowed")).withCharset(`UTF-8`))
    }

    /** when both acceptable media types are available as separate marshallers and equally specific, we must select the one with the highest qValue */
    "Accept: text/*;level=1;q=0.4, text/*;format=flowed;q=0.7".test { accept =>
      accept(`text/html`.withParams(Map("level" -> "1")),
        `text/html`.withParams(Map("format" -> "flowed"))) should select(
        `text/html`.withParams(Map("format" -> "flowed")).withCharset(`UTF-8`))
    }

    /** when there's 2 equally specific matching media types for the available marshaller, we should choose the one with the highest qValue */
    "Accept: text/html;level=1;q=0.4, text/html;format=flowed;q=1, text/html;q=0.7".test { accept =>
      accept(`text/html`.withParams(Map("level" -> "1", "format" -> "flowed")), `text/html`) should select(
        `text/html`.withParams(Map("level" -> "1", "format" -> "flowed")).withCharset(`UTF-8`))
    }

    // https://tools.ietf.org/html/rfc7231#section-5.3.2
    "Correctly calculate qValues" in {
      val negotiator = new MediaTypeNegotiator(parseHeaders(
        "Accept: text/*;q=0.3, text/html;q=0.7, text/html;level=1, text/html;level=2;q=0.4, */*;q=0.5"))
      negotiator.qValueFor(`text/html`.withParams(Map("level" -> "1"))) should be(1.0f)
      negotiator.qValueFor(`text/html`) should be(0.7f)
      negotiator.qValueFor(`text/plain`) should be(0.3f)

      negotiator.qValueFor(`image/jpeg`) should be(0.5f)
      negotiator.qValueFor(`text/html`.withParams(Map("level" -> "2"))) should be(0.4f)
      negotiator.qValueFor(`text/html`.withParams(Map("level" -> "3"))) should be(0.7f)
    }
  }

  "Picking from application-supported charsets" - {
    "honors a higher wildcard without selecting an explicitly excluded charset" in {
      val request = HttpRequest(headers = parseHeaders("Accept-Charset: UTF-8;q=0, ISO-8859-1;q=0.4, *;q=0.8"))

      val offered = List(`UTF-8`, `UTF-16LE`, `ISO-8859-1`)
      val selected = new CharsetNegotiator(request.headers).pickCharset(offered)

      selected shouldBe Some(`UTF-16LE`)
      val negotiator = new CharsetNegotiator(request.headers)
      negotiator.pickCharset(List(`UTF-16BE`, `UTF-16LE`)) shouldBe Some(`UTF-16BE`)
      negotiator.pickCharset(List(`UTF-8`, `ISO-8859-1`)) shouldBe Some(`ISO-8859-1`)
      negotiator.pickCharset(List(`UTF-8`)) shouldBe None
    }

    "prefers a named charset at equal quality, then honors the offered order" in {
      val negotiator = new CharsetNegotiator(
        parseHeaders("Accept-Charset: UTF-8;q=0.5, latin1;q=0.5, *;q=0.5"))

      negotiator.pickCharset(List(`UTF-16`, `ISO-8859-1`, `UTF-8`)) shouldBe Some(`ISO-8859-1`)
      negotiator.pickCharset(List(`UTF-16`, `UTF-8`, `ISO-8859-1`)) shouldBe Some(`UTF-8`)
      negotiator.pickBest shouldBe Some(`UTF-8`)
    }

    "uses the first offered charset when there are no client preferences" in {
      val negotiator = new CharsetNegotiator(Nil)
      negotiator.pickCharset(List(`ISO-8859-1`, `UTF-8`)) shouldBe Some(`ISO-8859-1`)
      negotiator.pickCharset(Nil) shouldBe None
      negotiator.pickBest shouldBe Some(`UTF-8`)
    }

    "does not select a charset outside the offered alternatives" in {
      val negotiator = new CharsetNegotiator(parseHeaders("Accept-Charset: UTF-8, latin1;q=0.5"))
      negotiator.pickCharset(List(`ISO-8859-1`, `UTF-8`)) shouldBe Some(`UTF-8`)
      negotiator.pickCharset(List(`UTF-16`, `ISO-8859-1`)) shouldBe Some(`ISO-8859-1`)
      negotiator.pickCharset(List(`UTF-16`)) shouldBe None
    }

    "lets the application encode the entity in the selected charset" in {
      val request = HttpRequest(headers = parseHeaders("Accept-Charset: UTF-8;q=0, *;q=0.8"))

      // #pick-charset
      val offered = List(`UTF-8`, `ISO-8859-1`)
      val entity = new CharsetNegotiator(request.headers).pickCharset(offered).map { charset =>
        HttpEntity(`text/plain`.withCharset(charset), "café")
      }
      // #pick-charset

      val selected = entity.getOrElse(fail("no acceptable charset"))
      selected.contentType shouldBe `text/plain`.withCharset(`ISO-8859-1`)
      selected.data shouldBe ByteString.fromArray(Array[Byte](0x63, 0x61, 0x66, 0xE9.toByte))
    }
  }

  def testHeaders[U](headers: HttpHeader*)(body: VarArgsFunction1[Alternative, Option[ContentType]] => U): U = {
    val request = HttpRequest(headers = headers.toVector)
    body { alternatives =>
      import scala.concurrent.ExecutionContext.Implicits.global

      // creates a pseudo marshaller for X, that applies for all the given content types
      trait X
      object X extends X
      implicit val marshallers: ToEntityMarshaller[X] =
        Marshaller.oneOf(alternatives.map {
          case Alternative.ContentType(ct) =>
            Marshaller.withFixedContentType(ct)((s: X) => HttpEntity(ct, ByteString("The X")))
          case Alternative.MediaType(mt) =>
            Marshaller.withOpenCharset(mt)((s: X, cs) => HttpEntity(mt.withCharset(cs), "The X"))
        }: _*)

      Await.result(
        Marshal(X).toResponseFor(request)
          .fast.map(response => Some(response.entity.contentType))
          .fast.recover { case _: Marshal.UnacceptableResponseContentTypeException => None }, 1.second)
    }
  }

  def reject = equal(None)
  def select(contentType: ContentType) = equal(Some(contentType))

  def parseHeaders(
      example: String): List[HttpHeader] = example.stripMarginWithNewline("\n").split('\n').toList.map { rawHeader =>
    val Array(name, value) = rawHeader.split(':')
    HttpHeader.parse(name.trim, value) match {
      case HttpHeader.ParsingResult.Ok(header, Nil) => header
      case result                                   => fail(result.errors.head.formatPretty)
    }
  }

  implicit class AddStringToIn(example: String) {
    def test(body: VarArgsFunction1[Alternative, Option[ContentType]] => Unit): Unit = example in {
      val headers =
        if (example != "(without headers)") {
          parseHeaders(example)
        } else Nil

      testHeaders(headers: _*)(accept => body(accept))
    }
  }
}
