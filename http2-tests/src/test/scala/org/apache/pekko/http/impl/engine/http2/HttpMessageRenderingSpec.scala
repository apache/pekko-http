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

package org.apache.pekko.http.impl.engine.http2

import java.time.format.DateTimeFormatter

import com.typesafe.config.ConfigFactory
import org.apache.pekko
import pekko.event.NoLogging
import pekko.http.impl.engine.rendering.DateHeaderRendering
import pekko.http.scaladsl.model.headers.{ Trailer => _, _ }
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.settings.{ ClientConnectionSettings, ServerSettings }
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.immutable.VectorBuilder
import scala.collection.immutable.Seq
import scala.collection.immutable
import scala.util.Try

object MyCustomHeader extends ModeledCustomHeaderCompanion[MyCustomHeader] {
  override def name: String = "custom-header"
  override def parse(value: String): Try[MyCustomHeader] = ???
}
class MyCustomHeader(val value: String, val renderInResponses: Boolean) extends ModeledCustomHeader[MyCustomHeader] {
  override def companion = MyCustomHeader
  override def renderInRequests(): Boolean = false
}

class HttpMessageRenderingSpec extends AnyWordSpec with Matchers {

  "The request/response common header logic" should {

    "output headers" in {
      val builder = new VectorBuilder[(String, String)]
      val headers = Seq(
        ETag("tagetitag"),
        RawHeader("raw", "whatever"),
        new MyCustomHeader("whatever", renderInResponses = true))
      renderServerHeaders(headers, builder)
      val out = builder.result()
      out.exists(_._1 == "etag") shouldBe true
      out.exists(_._1 == "raw") shouldBe true
      out.exists(_._1 == "custom-header") shouldBe true
    }

    "add a date header when none is present" in {
      val builder = new VectorBuilder[(String, String)]
      renderServerHeaders(Seq.empty, builder)
      val date = builder.result().collectFirst {
        case ("date", str) => str
      }

      date.isDefined shouldBe true

      // just make sure it parses
      DateTimeFormatter.RFC_1123_DATE_TIME.parse(date.get)
    }

    "keep the date header if it already is present" in {
      val builder = new VectorBuilder[(String, String)]
      val originalDateTime = DateTime(1981, 3, 6, 20, 30, 24)
      renderServerHeaders(Seq(Date(originalDateTime)), builder)
      val date = builder.result().collectFirst {
        case ("date", str) => str
      }

      date shouldEqual Some(originalDateTime.toRfc1123DateTimeString)
    }

    "add server header if default provided (in server mode)" in {
      val builder = new VectorBuilder[(String, String)]
      renderServerHeaders(Seq.empty, builder, Some(("server", "default server")))
      val result = builder.result().find(_._1 == "server").map(_._2)
      result shouldEqual Some("default server")
    }

    "keep server header if explicitly provided (in server mode)" in {
      val builder = new VectorBuilder[(String, String)]
      renderServerHeaders(Seq(Server("explicit server")), builder, Some(("server", "default server")))
      val result = builder.result().find(_._1 == "server").map(_._2)
      result shouldEqual Some("explicit server")
    }

    "exclude explicit headers that is not valid for HTTP/2" in {
      val builder = new VectorBuilder[(String, String)]
      val invalidExplicitHeaders = Seq(
        Connection("whatever"),
        `Content-Length`(42L),
        `Content-Type`(ContentTypes.`application/json`),
        `Transfer-Encoding`(TransferEncodings.gzip))
      renderServerHeaders(invalidExplicitHeaders, builder)
      builder.result().exists(_._1 != "date") shouldBe false
    }

    "exclude headers that should not be rendered in responses" in {
      val builder = new VectorBuilder[(String, String)]
      val shouldNotBeRendered = Seq(
        Host("example.com", 80),
        new MyCustomHeader("whatever", renderInResponses = false))
      renderServerHeaders(shouldNotBeRendered, builder)
      val value1 = builder.result()
      value1.exists(_._1 != "date") shouldBe false
    }

    "exclude explicit raw headers that is not valid for HTTP/2 or should not be provided as raw headers" in {
      val builder = new VectorBuilder[(String, String)]
      val invalidRawHeaders = Seq(
        "connection", "content-length", "content-type", "transfer-encoding", "date", "server").map(name =>
        RawHeader(name, "whatever"))
      renderServerHeaders(invalidRawHeaders, builder)
      builder.result().exists(_._1 != "date") shouldBe false
    }

    // Client-specific tests
    "add user-agent header if default provided (in client mode)" in {
      val builder = new VectorBuilder[(String, String)]
      renderClientHeaders(Seq(`User-Agent`("fancy browser")), builder)
      val result = builder.result().find(_._1 == "user-agent").map(_._2)
      result shouldEqual Some("fancy browser")
    }

    "keep user-agent header if explicitly provided (in client mode)" in {
      val builder = new VectorBuilder[(String, String)]
      renderClientHeaders(Seq(`User-Agent`("fancier client")), builder, Some(("user-agent", "fancy browser")))
      val result = builder.result().find(_._1 == "user-agent").map(_._2)
      result shouldEqual Some("fancier client")
    }

    "exclude headers that should not be rendered in requests" in {
      val builder = new VectorBuilder[(String, String)]
      val shouldNotBeRendered = Seq(
        Host("example.com", 80),
        new MyCustomHeader("whatever", renderInResponses = false))
      renderClientHeaders(shouldNotBeRendered, builder)
      val value1 = builder.result()
      value1.exists(_._1 == "date") shouldBe false
    }

    "handle empty trailer" in {
      val config = ConfigFactory.load("reference.conf")
      Try {
        val rendering = new RequestRendering(ClientConnectionSettings(config), NoLogging)
        rendering(HttpRequest().withAttributes(Map(AttributeKeys.trailer -> Trailer())))
      }.isSuccess shouldBe true
      Try {
        val rendering = new ResponseRendering(ServerSettings(config), NoLogging, dateHeaderRendering)
        rendering(
          HttpResponse().withAttributes(
            Map(
              AttributeKeys.trailer -> Trailer(),
              Http2.streamId -> 0)))
      }.isSuccess shouldBe true
    }

  }

  private def renderClientHeaders(headers: immutable.Seq[HttpHeader], builder: VectorBuilder[(String, String)],
      peerIdHeader: Option[(String, String)] = None): Unit =
    HttpMessageRendering.renderHeaders(headers, builder, peerIdHeader, NoLogging, isServer = false,
      shouldRenderAutoHeaders = true, dateHeaderRendering = DateHeaderRendering.Unavailable)

  private def renderServerHeaders(headers: immutable.Seq[HttpHeader], builder: VectorBuilder[(String, String)],
      peerIdHeader: Option[(String, String)] = None): Unit =
    HttpMessageRendering.renderHeaders(headers, builder, peerIdHeader, NoLogging, isServer = true,
      shouldRenderAutoHeaders = true,
      dateHeaderRendering = dateHeaderRendering)

  private lazy val dateHeaderRendering: DateHeaderRendering = new DateHeaderRendering {
    // fake date rendering
    override def renderHeaderPair(): (String, String) = "date" -> DateTime.now.toRfc1123DateTimeString
    override def renderHeaderBytes(): Array[Byte] = ???
    override def renderHeaderValue(): String = ???
  }
}
