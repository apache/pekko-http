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

package org.apache.pekko.http.scaladsl.model

import org.apache.pekko
import pekko.http.impl.engine.rendering.BodyPartRenderer
import pekko.http.impl.util.{ DefaultNoLogging, PekkoSpecWithMaterializer }
import pekko.http.scaladsl.model.headers._
import pekko.stream.scaladsl.{ Sink, Source }
import pekko.testkit._
import pekko.util.ByteString

import scala.concurrent.Await
import scala.concurrent.duration._

class MultipartSpec extends PekkoSpecWithMaterializer {
  "Multipart.General" should {
    "support `toStrict` on the streamed model" in {
      val streamed = Multipart.General(
        MediaTypes.`multipart/mixed`,
        Source(Multipart.General.BodyPart(defaultEntity("data"), List(ETag("xzy"))) :: Nil))
      val strict = Await.result(streamed.toStrict(1.second.dilated), 1.second.dilated)

      strict shouldEqual Multipart.General(
        MediaTypes.`multipart/mixed`,
        Multipart.General.BodyPart.Strict(HttpEntity("data"), List(ETag("xzy"))))
    }

    "support `toEntity`" in {
      val streamed = Multipart.General(
        MediaTypes.`multipart/mixed`,
        Source(Multipart.General.BodyPart(defaultEntity("data"), List(ETag("xzy"))) :: Nil))
      val result = streamed.toEntity("boundary")
      result.contentType shouldBe MediaTypes.`multipart/mixed`.withBoundary("boundary").toContentType
      val encoding = Await.result(result.dataBytes.runWith(Sink.seq), 1.second.dilated)
      encoding.map(
        _.utf8String).mkString shouldBe
      "--boundary\r\nContent-Type: text/plain; charset=UTF-8\r\nETag: \"xzy\"\r\n\r\ndata\r\n--boundary--"
    }
  }

  "Multipart.FormData" should {
    "match the name parameter of Content-Disposition without changing its value" in {
      val entity = HttpEntity("hello")
      val extraHeader = RawHeader("X-Note", "unchanged")

      for (parameter <- Seq("name", "Name")) {
        val disposition =
          HttpHeader.parse("Content-Disposition", s"""form-data; $parameter="FieldName"; x-meta="kept"""") match {
            case HttpHeader.ParsingResult.Ok(header: `Content-Disposition`, Nil) => header
            case other                                                           => fail(s"Expected a modeled Content-Disposition: $other")
          }
        val part = Multipart.General.BodyPart.Strict(entity, List(disposition, extraHeader))

        part.toFormDataBodyPart.get shouldEqual
        Multipart.FormData.BodyPart.Strict("FieldName", entity, Map("x-meta" -> "kept"), List(extraHeader))
      }
    }

    "render explicit filename strategies identically for strict and streamed parts" in {
      import Multipart.FormData.FilenameEncoding.{
        Ascii => AsciiFilename,
        Extended => ExtendedFilename,
        PercentEncoded => PercentEncodedFilename
      }
      for {
        filename <- Seq("café.txt", "plain.txt", "100%.txt")
        strategy <- Seq(ExtendedFilename, AsciiFilename, PercentEncodedFilename)
      } {
        val part = Multipart.FormData.BodyPart.Strict("upload", HttpEntity("body"), Map("filename" -> filename))
        val filenameParams =
          if (filename == "plain.txt") """filename="plain.txt"; name="upload"."""
          else if (filename == "100%.txt") strategy match {
            // `%` is not a safe filename character, so the ASCII fallback replaces it
            case ExtendedFilename => """filename="100?.txt"; filename*=UTF-8''100%25.txt; name="upload"."""
            case AsciiFilename    => """filename="100?.txt"; name="upload"."""
            case _                => """filename="100%25.txt"; name="upload"."""
          }
          else strategy match {
            case ExtendedFilename => """filename="caf?.txt"; filename*=UTF-8''caf%C3%A9.txt; name="upload"."""
            case AsciiFilename    => """filename="caf?.txt"; name="upload"."""
            case _                => """filename="caf%C3%A9.txt"; name="upload"."""
          }
        val expected = ByteString("--boundary\r\nContent-Type: text/plain; charset=UTF-8\r\n" +
          "Content-Disposition: form-data; " + filenameParams.dropRight(1) + "\r\n\r\nbody\r\n--boundary--")
        val strict = BodyPartRenderer.strict(Seq(part), "boundary", 128, DefaultNoLogging, strategy)
        val streamedPart = Multipart.FormData.BodyPart("upload", defaultEntity("body"), Map("filename" -> filename))
        val streamed = Source.single[Multipart.BodyPart](streamedPart)
          .via(BodyPartRenderer.streamed("boundary", 128, DefaultNoLogging, strategy))
          .flatMapConcat(identity)
          .map(_.data)
          .runFold(ByteString.empty)(_ ++ _)
        Multipart.FormData.Strict(Seq(part)).toEntity("boundary", strategy).data shouldEqual expected
        val javaStrict: pekko.http.javadsl.model.Multipart.FormData.Strict = Multipart.FormData.Strict(Seq(part))
        javaStrict.toEntity("boundary", strategy).getData shouldEqual expected
        val directStreamed = Multipart.FormData(Source.single(streamedPart)).toEntity("boundary", strategy)
        Await.result(directStreamed.dataBytes.runFold(ByteString.empty)(_ ++ _), 1.second.dilated) shouldEqual expected
        strict shouldEqual expected
        Await.result(streamed, 1.second.dilated) shouldEqual expected
        if (strategy == ExtendedFilename) {
          BodyPartRenderer.strict(Seq(part), "boundary", 128, DefaultNoLogging) shouldEqual expected
          Multipart.FormData.Strict(Seq(part)).toEntity("boundary").data shouldEqual expected
        }
      }
    }

    "apply explicit filename precedence only when a normal filename is present" in {
      import Multipart.FormData.FilenameEncoding.{
        Ascii => AsciiFilename,
        Extended => ExtendedFilename,
        PercentEncoded => PercentEncodedFilename
      }
      val part = Multipart.FormData.BodyPart.Strict("upload", HttpEntity("body"),
        Map("filename" -> "café.txt", "filename*" -> "résumé.txt"))
      for ((strategy, filename) <- Seq(AsciiFilename -> "caf?.txt", PercentEncodedFilename -> "caf%C3%A9.txt")) {
        val data = Multipart.FormData.Strict(Seq(part)).toEntity("boundary", strategy).data.utf8String
        data should include("filename=\"" + filename + "\"")
        (data should not).include("filename*")
      }
      val legacy = Multipart.FormData.Strict(Seq(part)).toEntity("boundary").data.utf8String
      legacy should include("filename*=UTF-8''r%C3%A9sum%C3%A9.txt")
      val extendedOnly = Multipart.FormData.BodyPart.Strict("upload", HttpEntity("body"),
        Map("filename*" -> "résumé.txt"))
      val form = Multipart.FormData.Strict(Seq(extendedOnly))
      for (strategy <- Seq(AsciiFilename, PercentEncodedFilename))
        form.toEntity("boundary", strategy).data shouldEqual form.toEntity("boundary").data
    }

    "leave attachment filename rendering unchanged for every multipart strategy" in {
      import Multipart.FormData.FilenameEncoding.{
        Ascii => AsciiFilename,
        Extended => ExtendedFilename,
        PercentEncoded => PercentEncodedFilename
      }
      val attachment = `Content-Disposition`(ContentDispositionTypes.attachment, Map("filename" -> "café.txt"))
      val part = Multipart.General.BodyPart.Strict(HttpEntity("body"), List(attachment))
      val expected = BodyPartRenderer.strict(Seq(part), "boundary", 128, DefaultNoLogging)
      attachment.value shouldEqual """attachment; filename="caf?.txt"; filename*=UTF-8''caf%C3%A9.txt"""
      for (strategy <- Seq(ExtendedFilename, AsciiFilename, PercentEncodedFilename)) {
        BodyPartRenderer.strict(Seq(part), "boundary", 128, DefaultNoLogging, strategy) shouldEqual expected
      }
    }

    "percent-decode the filename only on request" in {
      for ((raw, decoded) <- Seq(
          "caf%C3%A9.txt" -> "café.txt",
          "a%20b%22c%25.txt" -> "a b\"c%.txt",
          "%E2%82%AC%20%F0%9F%98%80" -> "€ 😀",
          "plain.txt" -> "plain.txt",
          "café%20x.txt" -> "café x.txt",
          // not valid percent-encoded UTF-8: returned unchanged
          "100%.txt" -> "100%.txt",
          "100%2" -> "100%2",
          "%zz.txt" -> "%zz.txt",
          "caf%E9.txt" -> "caf%E9.txt",
          "%C3" -> "%C3",
          "%C3x%A9" -> "%C3x%A9")) {
        val part = Multipart.FormData.BodyPart.Strict("upload", HttpEntity("body"), Map("filename" -> raw))
        part.filename shouldEqual Some(raw)
        part.percentDecodedFilename shouldEqual Some(decoded)
        part.getPercentDecodedFilename shouldEqual java.util.Optional.of(decoded)
      }
      val noFilename = Multipart.FormData.BodyPart.Strict("field", HttpEntity("body"))
      noFilename.percentDecodedFilename shouldEqual None
      noFilename.getPercentDecodedFilename shouldEqual java.util.Optional.empty[String]()
    }

    "support `toStrict` on the streamed model" in {
      val streamed = Multipart.FormData(Source(
        Multipart.FormData.BodyPart("foo", defaultEntity("FOO")) ::
        Multipart.FormData.BodyPart("bar", defaultEntity("BAR")) :: Nil))
      val strict = Await.result(streamed.toStrict(1.second.dilated), 1.second.dilated)

      strict shouldEqual Multipart.FormData(Map("foo" -> HttpEntity("FOO"), "bar" -> HttpEntity("BAR")))
    }
  }

  "Multipart.ByteRanges" should {
    "support `toStrict` on the streamed model" in {
      val streamed = Multipart.ByteRanges(Source(
        Multipart.ByteRanges.BodyPart(ContentRange(0, 6), defaultEntity("snippet"),
          _additionalHeaders = List(ETag("abc"))) ::
        Multipart.ByteRanges.BodyPart(ContentRange(8, 9), defaultEntity("PR"),
          _additionalHeaders = List(ETag("xzy"))) :: Nil))
      val strict = Await.result(streamed.toStrict(1.second.dilated), 1.second.dilated)

      strict shouldEqual Multipart.ByteRanges(
        Multipart.ByteRanges.BodyPart.Strict(ContentRange(0, 6), HttpEntity("snippet"),
          additionalHeaders = List(ETag("abc"))),
        Multipart.ByteRanges.BodyPart.Strict(ContentRange(8, 9), HttpEntity("PR"),
          additionalHeaders = List(ETag("xzy"))))
    }
  }

  def defaultEntity(content: String) =
    HttpEntity.Default(ContentTypes.`text/plain(UTF-8)`, content.length, Source(ByteString(content) :: Nil))
}
