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
import pekko.http.impl.util.PekkoSpecWithMaterializer
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
