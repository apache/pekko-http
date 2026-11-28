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

package org.apache.pekko.http.scaladsl.server
package directives

import scala.concurrent.Promise
import scala.concurrent.duration._

import org.apache.pekko
import pekko.http.impl.util._
import pekko.http.scaladsl.Http
import pekko.http.scaladsl.coding.Coders._
import pekko.http.scaladsl.coding.Encoder
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.model.ContentTypes.`application/octet-stream`
import pekko.http.scaladsl.model.HttpCharsets._
import pekko.http.scaladsl.model.HttpEntity.{ Chunk, ChunkStreamPart, LastChunk }
import pekko.http.scaladsl.model.MediaTypes._
import pekko.http.scaladsl.model.StatusCodes._
import pekko.http.scaladsl.model.headers.{ Trailer => TrailerHeader, _ }
import pekko.http.scaladsl.model.headers.HttpEncodings._
import pekko.http.scaladsl.testkit.RouteTestTimeout
import pekko.stream.scaladsl.{ Sink, Source }
import pekko.testkit._
import pekko.util.ByteString

import org.scalatest.Inside
import org.scalatest.matchers.Matcher

class CodingDirectivesSpec extends RoutingSpec with Inside {

  implicit val routeTestTimeout: RouteTestTimeout = RouteTestTimeout(3.seconds.dilated)

  val echoRequestContent: Route = { ctx => ctx.complete(ctx.request.entity.dataBytes.utf8String) }

  val yeah = complete("Yeah!")
  lazy val yeahGzipped = compress("Yeah!", Gzip)
  lazy val yeahDeflated = compress("Yeah!", Deflate)

  lazy val helloGzipped = compress("Hello", Gzip)
  lazy val helloDeflated = compress("Hello", Deflate)

  val nope = complete(404, "Nope!")
  lazy val nopeGzipped = compress("Nope!", Gzip)
  lazy val nopeDeflated = compress("Nope!", Deflate)

  "the requestEncodedWith directive" should {
    "match differently cased spellings of the same content coding" in {
      val coding = HttpEncoding.custom("example-codec")
      Post("/")                           ~> `Content-Encoding`(HttpEncoding.custom("Example-Codec")) ~>
      requestEncodedWith(coding) { yeah } ~> check { responseAs[String] shouldEqual "Yeah!" }
    }

    "reject a different content coding" in {
      val coding = HttpEncoding.custom("example-codec")
      Post("/")                           ~> `Content-Encoding`(HttpEncoding.custom("other-codec")) ~>
      requestEncodedWith(coding) { yeah } ~> check { rejection shouldEqual UnsupportedRequestEncodingRejection(coding) }
    }
  }

  "the NoEncoding decoder" should {
    "decode the request content if it has encoding 'identity'" in {
      Post("/", "yes") ~> `Content-Encoding`(identity) ~> {
        decodeRequestWith(NoCoding) { echoRequestContent }
      } ~> check { responseAs[String] shouldEqual "yes" }
    }
    "reject requests with content encoded with 'deflate'" in {
      Post("/", "yes") ~> `Content-Encoding`(deflate) ~> {
        decodeRequestWith(NoCoding) { echoRequestContent }
      } ~> check { rejection shouldEqual UnsupportedRequestEncodingRejection(identity) }
    }
    "decode the request content if no Content-Encoding header is present" in {
      Post("/", "yes") ~> decodeRequestWith(NoCoding) { echoRequestContent } ~> check {
        responseAs[String] shouldEqual "yes"
      }
    }
    "leave request without content unchanged" in {
      Post() ~> decodeRequestWith(NoCoding) { completeOk } ~> check { response shouldEqual Ok }
    }

    val echoDecodedEntity =
      decodeRequestWith(NoCoding) {
        extractRequest { request =>
          complete(HttpResponse(200, entity = request.entity))
        }
      }
    "leave Strict request entity unchanged" in {
      val data = ByteString(Array.fill[Byte](10000)(42.toByte))
      val strictEntity = HttpEntity.Strict(`application/octet-stream`, data)

      Post("/", strictEntity) ~> echoDecodedEntity ~> check {
        responseEntity shouldEqual strictEntity
      }
    }
    "leave Default request entity unchanged" in {
      val chunks = Vector(ByteString("abc"), ByteString("def"), ByteString("ghi"))
      val data = Source(chunks)

      val defaultEntity = HttpEntity.Default(`application/octet-stream`, 9, data)

      Post("/", defaultEntity) ~> echoDecodedEntity ~> check {
        inside(responseEntity) {
          case HttpEntity.Default(`application/octet-stream`, 9, dataChunks) =>
            dataChunks.grouped(1000).runWith(Sink.head).awaitResult(1.second.dilated).toVector shouldEqual chunks
        }
      }
    }
    // CloseDelimited not support for requests
    "leave Chunked request entity unchanged" in {
      val chunks =
        Vector(ByteString("abc"), ByteString("def"), ByteString("ghi"))
          .map(ChunkStreamPart(_))
      val data = Source(chunks)

      val defaultEntity = HttpEntity.Chunked(`application/octet-stream`, data)

      Post("/", defaultEntity) ~> echoDecodedEntity ~> check {
        inside(responseEntity) {
          case HttpEntity.Chunked(`application/octet-stream`, dataChunks) =>
            dataChunks.grouped(1000).runWith(Sink.head).awaitResult(1.second.dilated).toVector shouldEqual chunks
        }
      }
    }
  }

  "the Gzip decoder" should {
    "decode the request content if it has encoding 'gzip'" in {
      Post("/", helloGzipped) ~> `Content-Encoding`(gzip) ~> {
        decodeRequestWith(Gzip) { echoRequestContent }
      } ~> check { responseAs[String] shouldEqual "Hello" }
    }
    "preserve request trailers after decoding a chunked gzip entity" in {
      val trailer = LastChunk(trailer = List(RawHeader("x-test-trailer", "complete")))
      val (first, second) = helloGzipped.splitAt(helloGzipped.size / 2)
      val entity =
        HttpEntity.Chunked(ContentTypes.`text/plain(UTF-8)`, Source(List(Chunk(first), Chunk(second), trailer)))

      Post("/", entity) ~> `Content-Encoding`(gzip) ~> TrailerHeader(Seq("x-test-trailer")) ~> {
        decodeRequestWith(Gzip) {
          extractRequest { request =>
            inside(request.entity) {
              case decoded: HttpEntity.Chunked => complete(decoded)
            }
          }
        }
      } ~> check {
        inside(responseEntity) {
          case decoded: HttpEntity.Chunked =>
            val chunks = decoded.chunks.runWith(Sink.seq).awaitResult(1.second.dilated)
            chunks.last shouldEqual trailer
            chunks.collect { case part: LastChunk => part } shouldEqual Seq(trailer)
            chunks.init.map(_.data).foldLeft(ByteString.empty)(_ ++ _) shouldEqual ByteString("Hello")
        }
      }
    }
    "decode the request content if a raw Content-Encoding header says 'gzip'" in {
      Post("/", helloGzipped) ~> RawHeader("Content-Encoding", "gzip") ~> {
        decodeRequestWith(Gzip) { echoRequestContent }
      } ~> check { responseAs[String] shouldEqual "Hello" }
    }
    "reject the request content if it has encoding 'gzip' but is corrupt" in {
      // make sure there are no extra error logs
      EventFilter[IllegalRequestException](occurrences = 0).intercept {
        EventFilter.warning(start = "Illegal request", occurrences = 1).intercept {
          Post("/", fromHexDump("000102")) ~> `Content-Encoding`(gzip) ~> {
            decodeRequestWith(Gzip) {
              echoRequestContent
            }
          } ~> check {
            status shouldEqual BadRequest
            responseAs[String] shouldEqual "The request's encoding is corrupt"
          }
        }
      }
    }
    "reject truncated gzip request content" in {
      // make sure there are no extra error logs
      EventFilter[IllegalRequestException](occurrences = 0).intercept {
        EventFilter.warning(start = "Illegal request", occurrences = 1).intercept {
          Post("/", helloGzipped.dropRight(2)) ~> `Content-Encoding`(gzip) ~> {
            decodeRequestWith(Gzip) {
              echoRequestContent
            }
          } ~> check {
            status shouldEqual BadRequest
            responseAs[String] shouldEqual "The request's encoding is corrupt"
          }
        }
      }
    }
    "reject requests with content encoded with 'deflate'" in {
      Post("/", "Hello") ~> `Content-Encoding`(deflate) ~> {
        decodeRequestWith(Gzip) { completeOk }
      } ~> check { rejection shouldEqual UnsupportedRequestEncodingRejection(gzip) }
    }
    "reject requests without Content-Encoding header" in {
      Post("/", "Hello") ~> {
        decodeRequestWith(Gzip) { completeOk }
      } ~> check { rejection shouldEqual UnsupportedRequestEncodingRejection(gzip) }
    }
    "leave request without content unchanged" in {
      Post() ~> {
        decodeRequestWith(Gzip) { completeOk }
      } ~> check { response shouldEqual Ok }
    }
  }

  "a (decodeRequestWith(Gzip) | decodeRequestWith(NoEncoding)) compound directive" should {
    lazy val decodeWithGzipOrNoEncoding = decodeRequestWith(Gzip) | decodeRequestWith(NoCoding)
    "decode the request content if it has encoding 'gzip'" in {
      Post("/", helloGzipped) ~> `Content-Encoding`(gzip) ~> {
        decodeWithGzipOrNoEncoding { echoRequestContent }
      } ~> check { responseAs[String] shouldEqual "Hello" }
    }
    "decode the request content if it has encoding 'identity'" in {
      Post("/", "yes") ~> `Content-Encoding`(identity) ~> {
        decodeWithGzipOrNoEncoding { echoRequestContent }
      } ~> check { responseAs[String] shouldEqual "yes" }
    }
    "decode the request content if no Content-Encoding header is present" in {
      Post("/", "yes") ~> decodeWithGzipOrNoEncoding { echoRequestContent } ~> check {
        responseAs[String] shouldEqual "yes"
      }
    }
    "reject requests with content encoded with 'deflate'" in {
      Post("/", "yes") ~> `Content-Encoding`(deflate) ~> {
        decodeWithGzipOrNoEncoding { echoRequestContent }
      } ~> check {
        rejections shouldEqual Seq(
          UnsupportedRequestEncodingRejection(gzip),
          UnsupportedRequestEncodingRejection(identity))
      }
    }
  }

  "the encoder" should {
    "encode the response content with GZIP if the response is not a success and request has no Accept-Encoding header" in {
      Post() ~> {
        encodeResponseWith(Gzip) { nope }
      } ~> check { strictify(responseEntity) shouldEqual HttpEntity(ContentType(`text/plain`, `UTF-8`), nopeGzipped) }
    }
    "encode the response content with Deflate if the response is not a success and request has no Accept-Encoding header" in {
      Post() ~> {
        encodeResponseWith(Deflate) { nope }
      } ~> check { strictify(responseEntity) shouldEqual HttpEntity(ContentType(`text/plain`, `UTF-8`), nopeDeflated) }
    }
    "not encode the response content with GZIP if the response is of status not allowing entity" in {
      Post() ~> {
        encodeResponseWith(Gzip) { complete { StatusCodes.NoContent } }
      } ~> check {
        response should haveNoContentEncoding
        response shouldEqual HttpResponse(StatusCodes.NoContent, entity = HttpEntity.Empty)
      }
    }
    "not encode the response content with Deflate if the response is of status not allowing entity" in {
      Post() ~> {
        encodeResponseWith(Deflate) { complete(100, "Let's continue!") }
      } ~> check {
        response should haveNoContentEncoding
        response shouldEqual HttpResponse(StatusCodes.Continue, entity = HttpEntity.Empty)
      }
    }
    "encode the response content with GZIP if the response is of status allowing entity" in {
      Post() ~> {
        encodeResponseWith(Gzip) { nope }
      } ~> check {
        response should haveContentEncoding(gzip)
      }
    }
  }

  "the Gzip encoder" should {
    "encode the response content with GZIP if the client accepts it with a dedicated Accept-Encoding header" in {
      Post() ~> `Accept-Encoding`(gzip) ~> {
        encodeResponseWith(Gzip) { yeah }
      } ~> check {
        response should haveContentEncoding(gzip)
        strictify(responseEntity) shouldEqual HttpEntity(ContentType(`text/plain`, `UTF-8`), yeahGzipped)
      }
    }
    "encode the response content with GZIP if the request has no Accept-Encoding header" in {
      Post() ~> {
        encodeResponseWith(Gzip) { yeah }
      } ~> check { strictify(responseEntity) shouldEqual HttpEntity(ContentType(`text/plain`, `UTF-8`), yeahGzipped) }
    }
    "reject the request if the client does not accept GZIP encoding" in {
      Post() ~> `Accept-Encoding`(identity) ~> {
        encodeResponseWith(Gzip) { completeOk }
      } ~> check { rejection shouldEqual UnacceptedResponseEncodingRejection(gzip) }
    }
    "leave responses without content unchanged" in {
      Post() ~> `Accept-Encoding`(gzip) ~> {
        encodeResponseWith(Gzip) { completeOk }
      } ~> check {
        response shouldEqual Ok
        response should haveNoContentEncoding
      }
    }
    "leave responses with an already set Content-Encoding header unchanged" in {
      Post() ~> `Accept-Encoding`(gzip) ~> {
        encodeResponseWith(Gzip) {
          RespondWithDirectives.respondWithHeader(`Content-Encoding`(identity)) { completeOk }
        }
      } ~> check { response shouldEqual Ok.withHeaders(`Content-Encoding`(identity)) }
    }
    "leave a pre-encoded response with a raw Content-Encoding header unchanged" in {
      val header = RawHeader("cOnTeNt-EnCoDiNg", "gzip")
      val entity = HttpEntity(ContentType(`text/plain`, `UTF-8`), helloGzipped)

      Post() ~> `Accept-Encoding`(gzip) ~> {
        encodeResponseWith(Gzip) {
          complete(Ok.withHeaders(header).withEntity(entity))
        }
      } ~> check {
        response.headers.filter(_.is("content-encoding")) shouldEqual List(header)
        strictify(responseEntity) shouldEqual entity
      }
    }
    "correctly encode the chunk stream produced by a chunked response" in {
      val text = "This is a somewhat lengthy text that is being chunked by the autochunk directive!"
      val textChunks =
        () =>
          text.grouped(8).map { chars =>
            Chunk(chars.mkString): ChunkStreamPart
          }
      val chunkedTextEntity = HttpEntity.Chunked(ContentTypes.`text/plain(UTF-8)`, Source.fromIterator(textChunks))

      Post() ~> `Accept-Encoding`(gzip) ~> {
        encodeResponseWith(Gzip) {
          complete(chunkedTextEntity)
        }
      } ~> check {
        response should haveContentEncoding(gzip)
        chunks.size shouldEqual (11 + 1) // 11 regular + the last one
        val bytes = chunks.foldLeft(ByteString.empty)(_ ++ _.data)
        Gzip.decode(bytes).awaitResult(1.second.dilated) should readAs(text)
      }
    }
    "preserve a declared trailer on an HTTP/1.1 gzip response" in {
      val first = ByteString("report ")
      val second = ByteString("complete")
      val trailer = RawHeader("X-Report-State", "ready")
      val declaration = TrailerHeader(Seq(trailer.name))
      val terminal = LastChunk(trailer = List(trailer))
      val entity =
        HttpEntity.Chunked(ContentTypes.`text/plain(UTF-8)`, Source(List(Chunk(first), Chunk(second), terminal)))
      val acceptsTrailers = TE(TransferEncodings.trailers)
      val receivedTrailerSupport = Promise[(Option[TE], Option[Connection])]()

      val host = "127.0.0.1"
      val timeout = 3.seconds.dilated
      val route = extractRequest { request =>
        receivedTrailerSupport.success((request.header[TE], request.header[Connection]))
        encodeResponseWith(Gzip) {
          complete(HttpResponse(headers = List(declaration), entity = entity))
        }
      }
      val binding = Http().newServerAt(host, 0).bind(route).awaitResult(timeout)
      try {
        val request = HttpRequest(uri = "/report",
          headers = List(`Accept-Encoding`(gzip), acceptsTrailers, Connection("TE")))
        val parts = Source.single(request)
          .via(Http().connectionTo(host).toPort(binding.localAddress.getPort).http())
          .mapAsync(1) { result =>
            result.status shouldEqual OK
            result.protocol shouldEqual HttpProtocols.`HTTP/1.1`
            result should haveContentEncoding(gzip)
            result.header[TrailerHeader] shouldEqual Some(declaration)
            result.headers.exists(_.is(trailer.lowercaseName)) shouldBe false
            result.entity match {
              case chunked: HttpEntity.Chunked => chunked.chunks.runWith(Sink.seq)
              case other                       => fail(s"Expected a chunked response entity, got $other")
            }
          }
          .runWith(Sink.head)
          .awaitResult(timeout)

        val (receivedTE, receivedConnection) = receivedTrailerSupport.future.awaitResult(timeout)
        receivedTE shouldEqual Some(acceptsTrailers)
        withClue(s"Expected the TE connection option in $receivedConnection: ") {
          receivedConnection.exists(_.tokens.exists(_.equalsIgnoreCase("TE"))) shouldBe true
        }
        parts.lastOption shouldEqual Some(terminal)
        parts.collect { case last: LastChunk => last } shouldEqual Seq(terminal)
        val compressed = parts.foldLeft(ByteString.empty)(_ ++ _.data)
        Gzip.decode(compressed).awaitResult(timeout) shouldEqual (first ++ second)
      } finally binding.terminate(1.second.dilated).awaitResult(timeout)
    }
    "preserve gzip response bytes across an empty stream element" in {
      val first = ByteString("before ")
      val last = ByteString("after")
      // Include an empty element in the application source and check that the decoded surrounding bytes survive.
      val entity = HttpEntity.Default(ContentTypes.`text/plain(UTF-8)`, first.size + last.size,
        Source(List(first, ByteString.empty, last)))

      Post() ~> `Accept-Encoding`(gzip) ~> {
        encodeResponseWith(Gzip) {
          complete(entity)
        }
      } ~> check {
        response should haveContentEncoding(gzip)
        val compressed = chunks.foldLeft(ByteString.empty)(_ ++ _.data)
        Gzip.decode(compressed).awaitResult(1.second.dilated) shouldEqual (first ++ last)
      }
    }
    "correctly encode the chunk stream produced by an empty chunked response" in {
      val emptyChunkedEntity = HttpEntity.Chunked(ContentTypes.`text/plain(UTF-8)`, Source.empty)

      Post() ~> `Accept-Encoding`(gzip) ~> {
        encodeResponseWith(Gzip) {
          complete(emptyChunkedEntity)
        }
      } ~> check {
        response should haveContentEncoding(gzip)
        val bytes = chunks.foldLeft(ByteString.empty)(_ ++ _.data)
        Gzip.decode(bytes).awaitResult(1.second.dilated) should readAs("")
      }
    }
  }

  "response encoding negotiation across repeated Accept-Encoding header fields" should {
    // Each scenario lists Accept-Encoding field values, sent over the network either as separate header lines or
    // combined into one line, so that the server's parsing of repeated header lines is covered too.
    val scenarios = Seq(
      ("weight codings listed in different fields", Seq("gzip;q=0.5", "deflate;q=0.9"), Some(Deflate)),
      ("apply an explicit exclusion over a wildcard", Seq("*;q=0.8", "gzip;q=0"), Some(Deflate)),
      ("reject when all available codings are explicitly excluded", Seq("*", "gzip;q=0", "deflate;q=0"), None),
      // RFC 9110 doesn't define the meaning of a repeated coding; the range with the highest q-value wins,
      // regardless of field order.
      ("prefer the higher q-value when a coding is excluded and then accepted", Seq("gzip;q=0", "gzip"), Some(Gzip)),
      ("prefer the higher q-value when a coding is accepted and then excluded", Seq("gzip", "gzip;q=0"), Some(Gzip)))

    for {
      (description, fieldValues, expected) <- scenarios
      split <- Seq(false, true)
    } {
      s"$description (split=$split)" in {
        val headers =
          if (split) fieldValues.map(RawHeader("Accept-Encoding", _))
          else Seq(RawHeader("Accept-Encoding", fieldValues.mkString(", ")))
        val binding =
          Http().newServerAt("127.0.0.1", 0).bind(encodeResponseWith(Gzip, Deflate) { yeah })
            .awaitResult(3.seconds.dilated)
        try {
          val uri = s"http://127.0.0.1:${binding.localAddress.getPort}/"
          val response = Http().singleRequest(HttpRequest(uri = uri, headers = headers)).awaitResult(3.seconds.dilated)
          val data = response.entity.toStrict(3.seconds.dilated).awaitResult(3.seconds.dilated).data
          expected match {
            case Some(coder) =>
              response.status shouldEqual OK
              response should haveContentEncoding(coder.encoding)
              coder.decode(data).awaitResult(1.second.dilated) should readAs("Yeah!")
            case None =>
              response.status shouldEqual NotAcceptable
              response.header[`Content-Encoding`] shouldEqual None
          }
        } finally binding.terminate(1.second.dilated).awaitResult(3.seconds.dilated)
      }
    }
  }

  "the Zstd coder" should {
    "decode the request content if it has encoding 'zstd'" in {
      Post("/", compress("Hello", Zstd)) ~> `Content-Encoding`(zstd) ~> {
        decodeRequestWith(Zstd) { echoRequestContent }
      } ~> check { responseAs[String] shouldEqual "Hello" }
    }
    "encode the response content with zstd if the client accepts it" in {
      Post() ~> `Accept-Encoding`(zstd) ~> {
        encodeResponseWith(Zstd) { yeah }
      } ~> check {
        response should haveContentEncoding(zstd)
        Zstd.decode(strictify(responseEntity).data).awaitResult(1.second.dilated) should readAs("Yeah!")
      }
    }
    "prefer zstd if it is the first coder and the client accepts it" in {
      Post() ~> `Accept-Encoding`(gzip, zstd) ~> {
        encodeResponseWith(Zstd, Gzip) { yeah }
      } ~> check {
        response should haveContentEncoding(zstd)
      }
    }
    "not be used by the default coders" in {
      Post() ~> `Accept-Encoding`(zstd) ~> {
        encodeResponse { yeah }
      } ~> check {
        response should haveNoContentEncoding
      }
    }
  }

  "the encodeResponseWith(NoEncoding) directive" should {
    "produce a response if no Accept-Encoding is present in the request" in {
      Post() ~> encodeResponseWith(NoCoding) { completeOk } ~> check {
        response shouldEqual Ok
        response should haveNoContentEncoding
      }
    }
    "produce a not encoded response if the client only accepts non matching encodings" in {
      Post() ~> `Accept-Encoding`(gzip, identity) ~> {
        encodeResponseWith(NoCoding) { completeOk }
      } ~> check {
        response shouldEqual Ok
        response should haveNoContentEncoding
      }

      Post() ~> `Accept-Encoding`(gzip) ~> {
        encodeResponseWith(Deflate, NoCoding) { completeOk }
      } ~> check {
        response shouldEqual Ok
        response should haveNoContentEncoding
      }
    }
    "reject the request if the request has an 'Accept-Encoding: identity; q=0' header" in {
      Post() ~> `Accept-Encoding`(identity.withQValue(0f)) ~> {
        encodeResponseWith(NoCoding) { completeOk }
      } ~> check { rejection shouldEqual UnacceptedResponseEncodingRejection(identity) }
    }
  }

  "a (encodeResponse(Gzip) | encodeResponse(NoEncoding)) compound directive" should {
    lazy val encodeGzipOrIdentity = encodeResponseWith(Gzip) | encodeResponseWith(NoCoding)
    "produce a not encoded response if the request has no Accept-Encoding header" in {
      Post() ~> {
        encodeGzipOrIdentity { completeOk }
      } ~> check {
        response shouldEqual Ok
        response should haveNoContentEncoding
      }
    }
    "produce a GZIP encoded response if the request has an `Accept-Encoding: deflate;q=0.5, gzip` header" in {
      Post() ~> `Accept-Encoding`(deflate.withQValue(.5f), gzip) ~> {
        encodeGzipOrIdentity { yeah }
      } ~> check {
        response should haveContentEncoding(gzip)
        strictify(responseEntity) shouldEqual HttpEntity(ContentType(`text/plain`, `UTF-8`), yeahGzipped)
      }
    }
    "produce a non-encoded response if the request has an `Accept-Encoding: identity` header" in {
      Post() ~> `Accept-Encoding`(identity) ~> {
        encodeGzipOrIdentity { completeOk }
      } ~> check {
        response shouldEqual Ok
        response should haveNoContentEncoding
      }
    }
    "produce a non-encoded response if the request has an `Accept-Encoding: deflate` header" in {
      Post() ~> `Accept-Encoding`(deflate) ~> {
        encodeGzipOrIdentity { completeOk }
      } ~> check {
        response shouldEqual Ok
        response should haveNoContentEncoding
      }
    }
  }

  "the encodeResponse directive" should {
    "produce a non-encoded response if the request has no Accept-Encoding header" in {
      Get("/") ~> {
        encodeResponse { completeOk }
      } ~> check {
        response shouldEqual Ok
        response should haveNoContentEncoding
      }
    }
    "produce a GZIP encoded response if the request has an `Accept-Encoding: gzip, deflate` header" in {
      Get("/") ~> `Accept-Encoding`(gzip, deflate) ~> {
        encodeResponse { yeah }
      } ~> check {
        response should haveContentEncoding(gzip)
        strictify(responseEntity) shouldEqual HttpEntity(ContentType(`text/plain`, `UTF-8`), yeahGzipped)
      }
    }
    "produce a Deflate encoded response if the request has an `Accept-Encoding: deflate` header" in {
      Get("/") ~> `Accept-Encoding`(deflate) ~> {
        encodeResponse { yeah }
      } ~> check {
        response should haveContentEncoding(deflate)
        strictify(responseEntity) shouldEqual HttpEntity(ContentType(`text/plain`, `UTF-8`), yeahDeflated)
      }
    }
  }

  "the encodeResponseWith directive" should {
    "produce a response encoded with the specified Encoder if the request has a matching Accept-Encoding header" in {
      Get("/") ~> `Accept-Encoding`(gzip) ~> {
        encodeResponseWith(Gzip) { yeah }
      } ~> check {
        response should haveContentEncoding(gzip)
        strictify(responseEntity) shouldEqual HttpEntity(ContentType(`text/plain`, `UTF-8`), yeahGzipped)
      }
    }
    "produce a response encoded with one of the specified Encoders if the request has a matching Accept-Encoding header" in {
      Get("/") ~> `Accept-Encoding`(deflate) ~> {
        encodeResponseWith(Gzip, Deflate) { yeah }
      } ~> check {
        response should haveContentEncoding(deflate)
        strictify(responseEntity) shouldEqual HttpEntity(ContentType(`text/plain`, `UTF-8`), yeahDeflated)
      }
    }
    "produce a response encoded with the first of the specified Encoders if the request has no Accept-Encoding header" in {
      Get("/") ~> {
        encodeResponseWith(Gzip, Deflate) { yeah }
      } ~> check {
        response should haveContentEncoding(gzip)
        strictify(responseEntity) shouldEqual HttpEntity(ContentType(`text/plain`, `UTF-8`), yeahGzipped)
      }
    }
    "produce a response with no encoding if the request has an empty Accept-Encoding header" in {
      Get("/") ~> `Accept-Encoding`() ~> {
        encodeResponseWith(Gzip, Deflate, NoCoding) { yeah }
      } ~> check {
        responseAs[String] shouldEqual "Yeah!"
        response should haveNoContentEncoding
      }
    }
    "prefer an explicitly allowed identity coding over an excluded wildcard" in {
      val excludedWildcard = HttpEncodingRange.`*`(0f)
      val route = encodeResponseWith(Gzip, NoCoding) { yeah }

      Get("/") ~> `Accept-Encoding`(excludedWildcard, identity) ~> route ~> check {
        responseAs[String] shouldEqual "Yeah!"
        response should haveNoContentEncoding
      }

      Get("/") ~> `Accept-Encoding`(excludedWildcard) ~> route ~> check {
        rejection shouldEqual UnacceptedResponseEncodingRejection(Set(gzip, identity))
      }
    }
    "reject an empty Accept-Encoding header when no identity encoder is offered" in {
      Get("/") ~> `Accept-Encoding`() ~> {
        encodeResponseWith(Gzip, Deflate) { yeah }
      } ~> check {
        rejection shouldEqual UnacceptedResponseEncodingRejection(Set(gzip, deflate))
      }
    }
    "combine an empty Accept-Encoding field with a non-empty field in either order" in {
      for (fields <- List(List(`Accept-Encoding`(), `Accept-Encoding`(gzip)),
          List(`Accept-Encoding`(gzip), `Accept-Encoding`()))) {
        Get("/").withHeaders(fields) ~> {
          encodeResponseWith(Gzip, Deflate) { yeah }
        } ~> check {
          response should haveContentEncoding(gzip)
          strictify(responseEntity) shouldEqual HttpEntity(ContentType(`text/plain`, `UTF-8`), yeahGzipped)
        }
      }
    }
    "negotiate the correct content encoding" in {
      Get("/") ~> `Accept-Encoding`(identity.withQValue(.5f), deflate.withQValue(0f), gzip) ~> {
        encodeResponseWith(NoCoding, Deflate, Gzip) { yeah }
      } ~> check {
        response should haveContentEncoding(gzip)
        strictify(responseEntity) shouldEqual HttpEntity(ContentType(`text/plain`, `UTF-8`), yeahGzipped)
      }

      Get("/") ~> `Accept-Encoding`(HttpEncodingRange.`*`, deflate.withQValue(0.2)) ~> {
        encodeResponseWith(Deflate, Gzip) { yeah }
      } ~> check {
        response should haveContentEncoding(gzip)
        strictify(responseEntity) shouldEqual HttpEntity(ContentType(`text/plain`, `UTF-8`), yeahGzipped)
      }
    }
    "select an available response encoding when an unsupported extension has higher quality" in {
      val unavailable = HttpEncoding.custom("example-coding")
      Get("/") ~> `Accept-Encoding`(unavailable, gzip.withQValue(0.4f)) ~> {
        encodeResponseWith(Gzip) { yeah }
      } ~> check {
        status shouldBe OK
        response should haveContentEncoding(gzip)
        val compressed = strictify(responseEntity).data
        Gzip.decode(compressed).awaitResult(1.second.dilated) should readAs("Yeah!")
      }
    }
    "reject the request if it has an Accept-Encoding header with an encoding that doesn't match" in {
      Get("/") ~> `Accept-Encoding`(deflate) ~> {
        encodeResponseWith(Gzip) { yeah }
      } ~> check {
        rejection shouldEqual UnacceptedResponseEncodingRejection(gzip)
      }
    }
    "reject the request if it has an Accept-Encoding header with an encoding that matches but is blacklisted" in {
      Get("/") ~> `Accept-Encoding`(gzip.withQValue(0f)) ~> {
        encodeResponseWith(Gzip) { yeah }
      } ~> check {
        rejection shouldEqual UnacceptedResponseEncodingRejection(gzip)
      }
    }
  }

  "the decodeRequest directive" should {
    "decode the request content if it has a `Content-Encoding: gzip` header and the content is gzip encoded" in {
      Post("/", helloGzipped) ~> `Content-Encoding`(gzip) ~> {
        decodeRequest { echoRequestContent }
      } ~> check { responseAs[String] shouldEqual "Hello" }
    }
    "decode the request content if it has a `Content-Encoding: deflate` header and the content is deflate encoded" in {
      Post("/", helloDeflated) ~> `Content-Encoding`(deflate) ~> {
        decodeRequest { echoRequestContent }
      } ~> check { responseAs[String] shouldEqual "Hello" }
    }
    "decode the request content if it has a `Content-Encoding: identity` header and the content is not encoded" in {
      Post("/", "yes") ~> `Content-Encoding`(identity) ~> {
        decodeRequest { echoRequestContent }
      } ~> check { responseAs[String] shouldEqual "yes" }
    }
    "decode the request content using NoEncoding if no Content-Encoding header is present" in {
      Post("/", "yes") ~> decodeRequest { echoRequestContent } ~> check { responseAs[String] shouldEqual "yes" }
    }
    "reject the request if it has a `Content-Encoding: deflate` header but the request is encoded with Gzip" in {
      // make sure there are no extra error logs
      EventFilter[IllegalRequestException](occurrences = 0).intercept {
        EventFilter.warning(start = "Illegal request", occurrences = 1).intercept {
          Post("/", helloGzipped) ~> `Content-Encoding`(deflate) ~>
          decodeRequest {
            echoRequestContent
          } ~> check {
            status shouldEqual BadRequest
            responseAs[String] shouldEqual "The request's encoding is corrupt"
          }
        }
      }
    }
  }

  "the decodeRequestWith directive" should {
    "decode the request content if its `Content-Encoding` header matches the specified encoder" in {
      Post("/", helloGzipped) ~> `Content-Encoding`(gzip) ~> {
        decodeRequestWith(Gzip) { echoRequestContent }
      } ~> check { responseAs[String] shouldEqual "Hello" }
    }
    "reject the request if its `Content-Encoding` header doesn't match the specified encoder" in {
      Post("/", helloGzipped) ~> `Content-Encoding`(deflate) ~> {
        decodeRequestWith(Gzip) { echoRequestContent }
      } ~> check {
        rejection shouldEqual UnsupportedRequestEncodingRejection(gzip)
      }
    }
    "reject the request when decoding with GZIP and no Content-Encoding header is present" in {
      Post("/", "yes") ~> decodeRequestWith(Gzip) { echoRequestContent } ~> check {
        rejection shouldEqual UnsupportedRequestEncodingRejection(gzip)
      }
    }
  }

  "the (decodeRequest & encodeResponse) compound directive" should {
    lazy val decodeEncode = decodeRequest & encodeResponse
    "decode a GZIP encoded request and produce a none encoded response if the request has no Accept-Encoding header" in {
      Post("/", helloGzipped) ~> `Content-Encoding`(gzip) ~> {
        decodeEncode { echoRequestContent }
      } ~> check {
        response should haveNoContentEncoding
        strictify(responseEntity) shouldEqual HttpEntity(ContentType(`text/plain`, `UTF-8`), "Hello")
      }
    }
    "decode a GZIP encoded request and produce a Deflate encoded response if the request has an `Accept-Encoding: deflate` header" in {
      Post("/", helloGzipped) ~> `Content-Encoding`(gzip) ~> `Accept-Encoding`(deflate) ~> {
        decodeEncode { echoRequestContent }
      } ~> check {
        response should haveContentEncoding(deflate)
        strictify(responseEntity) shouldEqual HttpEntity(ContentType(`text/plain`, `UTF-8`), helloDeflated)
      }
    }
    "decode an unencoded request and produce a GZIP encoded response if the request has an `Accept-Encoding: gzip` header" in {
      Post("/", "Hello") ~> `Accept-Encoding`(gzip) ~> {
        decodeEncode { echoRequestContent }
      } ~> check {
        response should haveContentEncoding(gzip)
        strictify(responseEntity) shouldEqual HttpEntity(ContentType(`text/plain`, `UTF-8`), helloGzipped)
      }
    }
  }

  "the default marshaller" should {
    "allow compressed responses with no body for informational messages" in {
      Get() ~> `Accept-Encoding`(HttpEncodings.compress) ~> {
        encodeResponse {
          complete { StatusCodes.Continue }
        }
      } ~> check {
        status shouldBe StatusCodes.Continue
      }
    }
    "allow gzipped responses with no body for 204 messages" in {
      Get() ~> `Accept-Encoding`(HttpEncodings.gzip) ~> {
        encodeResponse {
          complete { StatusCodes.NoContent }
        }
      } ~> check {
        status shouldBe StatusCodes.NoContent
      }
    }
  }

  def compress(input: String, encoder: Encoder): ByteString =
    encoder.encodeAsync(ByteString(input)).awaitResult(3.seconds.dilated)

  def hexDump(bytes: Array[Byte]) = bytes.map("%02x" format _).mkString
  def fromHexDump(dump: String) = dump.grouped(2).toArray.map(chars => Integer.parseInt(new String(chars), 16).toByte)

  def haveNoContentEncoding: Matcher[HttpResponse] = be(None).compose { (_: HttpResponse).header[`Content-Encoding`] }
  def haveContentEncoding(encoding: HttpEncoding): Matcher[HttpResponse] =
    be(Some(`Content-Encoding`(encoding))).compose { (_: HttpResponse).header[`Content-Encoding`] }

  def readAs(string: String, charset: String = "UTF8") = be(string).compose { (_: ByteString).decodeString(charset) }

  def strictify(entity: HttpEntity) = entity.toStrict(1.second.dilated).awaitResult(1.second.dilated)
}
