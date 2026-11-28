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

package org.apache.pekko.http.impl.engine.client

import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.reflect.ClassTag

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.event.NoLogging
import pekko.http.impl.engine.rendering.{
  DateHeaderRendering,
  HttpResponseRendererFactory,
  ResponseRenderingContext,
  ResponseRenderingOutput
}
import pekko.http.impl.util._
import pekko.http.scaladsl.model.HttpEntity._
import pekko.http.scaladsl.model.HttpMethods._
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.model.headers._
import pekko.http.scaladsl.settings.{ ClientConnectionSettings, ConnectionPoolSettings }
import pekko.http.scaladsl.{ ClientTransport, Http }
import pekko.http.{ ExpectContinuePolicy, ExpectContinueRetryContext }
import pekko.stream.ClosedShape
import pekko.stream.TLSProtocol._
import pekko.stream.scaladsl._
import pekko.stream.testkit._
import pekko.testkit._
import pekko.util.ByteString

import org.scalatest.Inside

import com.typesafe.config.ConfigFactory

class LowLevelOutgoingConnectionSpec extends PekkoSpecWithMaterializer with Inside {
  implicit val dispatcher: ExecutionContext = system.dispatcher

  "The connection-level client implementation" should {

    "handle a request/response round-trip".which {

      "has a request with empty entity" in new TestSetup {
        sendStandardRequest()
        sendWireData(
          """HTTP/1.1 200 OK
            |Content-Length: 0
            |
            |""")

        expectResponse() shouldEqual HttpResponse()

        requestsSub.sendComplete()
        netOut.expectComplete()
        netInSub.sendComplete()
        responses.expectComplete()
      }

      "has a request with default entity" in new TestSetup {
        val probe = TestPublisher.manualProbe[ByteString]()
        requestsSub.sendNext(HttpRequest(PUT,
          entity = HttpEntity(ContentTypes.`application/octet-stream`, 8, Source.fromPublisher(probe))))
        expectWireData(
          """PUT / HTTP/1.1
            |Host: example.com
            |User-Agent: pekko-http/test
            |Content-Type: application/octet-stream
            |Content-Length: 8
            |
            |""")
        val sub = probe.expectSubscription()
        sub.expectRequest()
        sub.sendNext(ByteString("ABC"))
        expectWireData("ABC")
        sub.sendNext(ByteString("DEF"))
        expectWireData("DEF")
        sub.sendNext(ByteString("XY"))
        expectWireData("XY")
        sub.sendComplete()

        sendWireData(
          """HTTP/1.1 200 OK
            |Content-Length: 0
            |
            |""")

        expectResponse() shouldEqual HttpResponse()

        requestsSub.sendComplete()
        netOut.expectComplete()
        netInSub.sendComplete()
        responses.expectComplete()
      }

      "has a response with a chunked entity" in new TestSetup {
        sendStandardRequest()
        sendWireData(
          """HTTP/1.1 200 OK
            |Transfer-Encoding: chunked
            |
            |""")

        val HttpResponse(_, _, HttpEntity.Chunked(ct, chunks), _) = expectResponse()
        ct shouldEqual ContentTypes.`application/octet-stream`

        val probe = TestSubscriber.manualProbe[ChunkStreamPart]()
        chunks.runWith(Sink.fromSubscriber(probe))
        val sub = probe.expectSubscription()

        sendWireData("3\nABC\n")
        sub.request(1)
        probe.expectNext(HttpEntity.Chunk("ABC"))

        sendWireData("4\nDEFX\n")
        sub.request(1)
        probe.expectNext(HttpEntity.Chunk("DEFX"))

        sendWireData("0\n\n")
        sub.request(1)
        probe.expectNext(HttpEntity.LastChunk)
        sub.request(1)
        probe.expectComplete()

        requestsSub.sendComplete()
        netOut.expectComplete()
        responses.expectComplete()
      }

      "has a response with a chunked entity and Connection: close" in new TestSetup {
        sendStandardRequest()

        sendWireData(
          """HTTP/1.1 200 OK
            |Transfer-Encoding: chunked
            |Connection: close
            |
            |""")
        sendWireData("3\nABC\n")
        sendWireData("4\nDEFX\n")
        sendWireData("0\n\n")

        val HttpResponse(_, _, HttpEntity.Chunked(ct, chunks), _) = expectResponse()
        ct shouldEqual ContentTypes.`application/octet-stream`

        val probe = TestSubscriber.manualProbe[ChunkStreamPart]()
        chunks.runWith(Sink.fromSubscriber(probe))
        val sub = probe.expectSubscription()
        sub.request(4)
        probe.expectNext(HttpEntity.Chunk("ABC"))
        probe.expectNext(HttpEntity.Chunk("DEFX"))
        probe.expectNext(HttpEntity.LastChunk)
        probe.expectComplete()

        // explicit `requestsSub.sendComplete()` not needed
        netOut.expectComplete()
        responses.expectComplete()
      }

      "has a request with a chunked entity and Connection: close" in new TestSetup {
        requestsSub.sendNext(HttpRequest(
          entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, Source(List("ABC", "DEFX").map(ByteString(_)))),
          headers = List(Connection("close"))))

        expectWireData(
          """GET / HTTP/1.1
            |Connection: close
            |Host: example.com
            |User-Agent: pekko-http/test
            |Transfer-Encoding: chunked
            |Content-Type: text/plain; charset=UTF-8
            |
            |""")
        expectWireData("3\nABC\n")
        expectWireData("4\nDEFX\n")
        expectWireData("0\n\n")

        sendWireData(
          """HTTP/1.1 200 OK
            |Connection: close
            |Content-Length: 0
            |
            |""")

        expectResponse()

        // explicit `requestsSub.sendComplete()` not needed
        responses.expectComplete()
        netOut.expectComplete()
      }

      "has a request with a overridden User-Agent RawHeader" in new TestSetup {
        val request = HttpRequest().addHeader(RawHeader("User-Agent", "pekko-http/test-overridden"))
        requestsSub.sendNext(request)
        expectWireData(
          """GET / HTTP/1.1
            |User-Agent: pekko-http/test-overridden
            |Host: example.com
            |
            |""")

        sendWireData(
          """HTTP/1.1 200 OK
            |Content-Length: 0
            |
            |""")

        expectResponse() shouldEqual HttpResponse()

        requestsSub.sendComplete()
        netOut.expectComplete()
        netInSub.sendComplete()
        responses.expectComplete()
      }

      "has a request with a overridden Host RawHeader" in new TestSetup {
        val request = HttpRequest().addHeader(RawHeader("Host", "testhost.com"))
        requestsSub.sendNext(request)
        expectWireData(
          """GET / HTTP/1.1
            |Host: testhost.com
            |User-Agent: pekko-http/test
            |
            |""")

        sendWireData(
          """HTTP/1.1 200 OK
            |Content-Length: 0
            |
            |""")

        expectResponse() shouldEqual HttpResponse()

        requestsSub.sendComplete()
        netOut.expectComplete()
        netInSub.sendComplete()
        responses.expectComplete()
      }

      "exhibits eager request stream completion" in new TestSetup {
        requestsSub.sendNext(HttpRequest())
        requestsSub.sendComplete()
        expectWireData(
          """GET / HTTP/1.1
            |Host: example.com
            |User-Agent: pekko-http/test
            |
            |""")

        sendWireData(
          """HTTP/1.1 200 OK
            |Content-Length: 0
            |
            |""")

        expectResponse() shouldEqual HttpResponse()

        netOut.expectComplete()
        netInSub.sendComplete()
        responses.expectComplete()
      }
    }

    "close the connection if response entity stream has been cancelled" in new TestSetup {
      // two requests are sent in order to make sure that connection
      // isn't immediately closed after the first one by the server
      requestsSub.sendNext(HttpRequest())
      requestsSub.sendNext(HttpRequest())
      requestsSub.sendComplete()

      expectWireData(
        """GET / HTTP/1.1
          |Host: example.com
          |User-Agent: pekko-http/test
          |
          |""")

      // two chunks sent by server
      sendWireData(
        """HTTP/1.1 200 OK
          |Transfer-Encoding: chunked
          |
          |6
          |abcdef
          |6
          |abcdef
          |0
          |
          |""")

      inside(expectResponse()) {
        case HttpResponse(StatusCodes.OK, _, HttpEntity.Chunked(_, data), _) =>
          val dataProbe = TestSubscriber.manualProbe[ChunkStreamPart]()
          // but only one consumed by server
          data.take(1).to(Sink.fromSubscriber(dataProbe)).run()
          val sub = dataProbe.expectSubscription()
          sub.request(1)
          dataProbe.expectNext(Chunk(ByteString("abcdef")))
          dataProbe.expectComplete()
          // connection is closed once requested elements are consumed
          netInSub.expectCancellation()
      }
    }

    "proceed to next response once previous response's entity has been drained" in new TestSetup {
      def twice(action: => Unit): Unit = { action; action }

      twice {
        requestsSub.sendNext(HttpRequest())

        expectWireData(
          """GET / HTTP/1.1
            |Host: example.com
            |User-Agent: pekko-http/test
            |
            |""")

        sendWireData(
          """HTTP/1.1 200 OK
            |Transfer-Encoding: chunked
            |
            |6
            |abcdef
            |0
            |
            |""")

        val whenComplete = expectResponse().entity.dataBytes.runWith(Sink.ignore)
        whenComplete.futureValue should be(pekko.Done)
      }
    }

    "handle empty 205 responses on a persistent connection" should {
      for ((framing, body) <- Seq(
          "Content-Length: 0" -> "",
          "Transfer-Encoding: chunked" -> "0\n\n")) {
        s"consume $framing before the next response" in new TestSetup {
          sendStandardRequest()
          sendWireData(s"HTTP/1.1 205 Reset Content\n$framing\n\n$body")
          val reset = expectResponse()
          reset.status shouldEqual StatusCodes.ResetContent
          reset.entity.isKnownEmpty shouldBe true

          sendStandardRequest()
          sendWireData("HTTP/1.1 200 OK\nContent-Length: 0\n\n")
          expectResponse() shouldEqual HttpResponse()

          requestsSub.sendComplete()
          netOut.expectComplete()
          netInSub.sendComplete()
          responses.expectComplete()
        }
      }
    }

    "deliver an empty 205 without framing headers immediately on a persistent connection" in new TestSetup {
      sendStandardRequest()
      sendWireData("HTTP/1.1 205 Reset Content\n\n")
      val reset = expectResponse()
      reset.status shouldEqual StatusCodes.ResetContent
      reset.entity.isKnownEmpty shouldBe true

      // A server that keeps the connection open must not stall the next response.
      sendStandardRequest()
      sendWireData("HTTP/1.1 200 OK\nContent-Length: 0\n\n")
      expectResponse() shouldEqual HttpResponse()

      requestsSub.sendComplete()
      netOut.expectComplete()
      netInSub.sendComplete()
      responses.expectComplete()
    }

    "handle several requests on one persistent connection".which {
      "has a first response that was chunked" in new TestSetup {
        requestsSub.sendNext(HttpRequest())
        expectWireData(
          """GET / HTTP/1.1
            |Host: example.com
            |User-Agent: pekko-http/test
            |
            |""")

        sendWireData(
          """HTTP/1.1 200 OK
            |Transfer-Encoding: chunked
            |
            |""")

        val HttpResponse(_, _, HttpEntity.Chunked(_, chunks), _) = expectResponse()

        val probe = TestSubscriber.manualProbe[ChunkStreamPart]()
        chunks.runWith(Sink.fromSubscriber(probe))
        val sub = probe.expectSubscription()

        sendWireData("3\nABC\n")
        sub.request(1)
        probe.expectNext(HttpEntity.Chunk("ABC"))

        sendWireData("0\n\n")
        sub.request(1)
        probe.expectNext(HttpEntity.LastChunk)
        sub.request(1)
        probe.expectComplete()

        // simulate that response is received before method bypass reaches response parser
        sendWireData(
          """HTTP/1.1 200 OK
            |Content-Length: 0
            |
            |""")

        responsesSub.request(1)

        requestsSub.sendNext(HttpRequest())
        expectWireData(
          """GET / HTTP/1.1
            |Host: example.com
            |User-Agent: pekko-http/test
            |
            |""")
        requestsSub.sendComplete()
        responses.expectNext(HttpResponse())

        netOut.expectComplete()
        netInSub.sendComplete()
        responses.expectComplete()
      }
    }

    "process the illegal response header value properly".which {

      val illegalChar = '\u0001'
      val escapeChar = "\\u%04x".format(illegalChar.toInt)

      "catch illegal response header value by default" in new TestSetup {
        sendStandardRequest()
        sendWireData(
          s"""HTTP/1.1 200 OK
              |Some-Header: value1$illegalChar
              |Other-Header: value2
              |
              |""")

        responsesSub.request(1)
        val error @ IllegalResponseException(info) = responses.expectError()
        info.summary shouldEqual s"""Illegal character '$escapeChar' in header value"""
        netOut.expectError(error)
        requestsSub.expectCancellation()
        netInSub.expectCancellation()
      }

      val ignoreConfig =
        """
          pekko.http.parsing.illegal-response-header-value-processing-mode = ignore
        """
      "ignore illegal response header value if setting the config to ignore" in new TestSetup(config = ignoreConfig) {
        sendStandardRequest()
        sendWireData(
          s"""HTTP/1.1 200 OK
              |Some-Header: value1$illegalChar
              |Other-Header: value2
              |
              |""")

        val HttpResponse(_, headers, _, _) = expectResponse()
        val headerStr = headers.map(h => s"${h.name}: ${h.value}").mkString(",")
        headerStr shouldEqual "Some-Header: value1,Other-Header: value2"
      }

      val ignoreNameConfig =
        """
          pekko.http.parsing.illegal-response-header-name-processing-mode = ignore
        """
      "ignore illegal response header name if setting the config to ignore" in new TestSetup(
        config = ignoreNameConfig) {
        sendStandardRequest()
        sendWireData(
          s"""HTTP/1.1 200 OK
              |Some Header: value1
              |Other-Header: value2
              |
              |""")

        val HttpResponse(_, headers, _, _) = expectResponse()
        val headerStr = headers.map(h => s"${h.name}: ${h.value}").mkString(",")
        headerStr shouldEqual "Some Header: value1,Other-Header: value2"
      }

      val warnConfig =
        """
          pekko.http.parsing.illegal-response-header-value-processing-mode = warn
        """
      "ignore illegal response header value and log a warning message if setting the config to warn" in new TestSetup(
        config = warnConfig) {
        sendStandardRequest()
        sendWireData(
          s"""HTTP/1.1 200 OK
              |Some-Header: value1$illegalChar
              |Other-Header: value2
              |
              |""")

        val HttpResponse(_, headers, _, _) = expectResponse()
        val headerStr = headers.map(h => s"${h.name}: ${h.value}").mkString(",")
        headerStr shouldEqual "Some-Header: value1,Other-Header: value2"
      }
    }

    val warnNameConfig =
      """
          pekko.http.parsing.illegal-response-header-name-processing-mode = warn
        """
    "ignore illegal response header name and log a warning message if setting the config to warn" in new TestSetup(
      config = warnNameConfig) {
      sendStandardRequest()
      sendWireData(
        s"""HTTP/1.1 200 OK
              |Some Header: value1
              |Other-Header: value2
              |
              |""")

      val HttpResponse(_, headers, _, _) = expectResponse()
      val headerStr = headers.map(h => s"${h.name}: ${h.value}").mkString(",")
      headerStr shouldEqual "Some Header: value1,Other-Header: value2"
    }

    "produce proper errors".which {

      "catch the request entity stream being shorter than the Content-Length" in new TestSetup {
        val probe = TestPublisher.manualProbe[ByteString]()
        requestsSub.sendNext(HttpRequest(PUT,
          entity = HttpEntity(ContentTypes.`application/octet-stream`, 8, Source.fromPublisher(probe))))
        expectWireData(
          """PUT / HTTP/1.1
            |Host: example.com
            |User-Agent: pekko-http/test
            |Content-Type: application/octet-stream
            |Content-Length: 8
            |
            |""")
        val sub = probe.expectSubscription()
        sub.expectRequest()
        sub.sendNext(ByteString("ABC"))
        expectWireData("ABC")
        sub.sendNext(ByteString("DEF"))
        expectWireData("DEF")
        sub.sendComplete()

        val InvalidContentLengthException(info) = netOut.expectError()
        info.summary shouldEqual
        "HTTP message had declared Content-Length 8 but entity data stream amounts to 2 bytes less"
        netInSub.sendComplete()
        responsesSub.request(1)
        responses.expectError().getMessage should equal(
          "HTTP message had declared Content-Length 8 but entity data stream amounts to 2 bytes less")
      }

      "catch the request entity stream being longer than the Content-Length" in new TestSetup {
        val probe = TestPublisher.manualProbe[ByteString]()
        requestsSub.sendNext(HttpRequest(PUT,
          entity = HttpEntity(ContentTypes.`application/octet-stream`, 8, Source.fromPublisher(probe))))
        expectWireData(
          """PUT / HTTP/1.1
            |Host: example.com
            |User-Agent: pekko-http/test
            |Content-Type: application/octet-stream
            |Content-Length: 8
            |
            |""")
        val sub = probe.expectSubscription()
        sub.expectRequest()
        sub.sendNext(ByteString("ABC"))
        expectWireData("ABC")
        sub.sendNext(ByteString("DEF"))
        expectWireData("DEF")
        sub.sendNext(ByteString("XYZ"))

        val InvalidContentLengthException(info) = netOut.expectError()
        info.summary shouldEqual
        "HTTP message had declared Content-Length 8 but entity data stream amounts to more bytes"
        netInSub.sendComplete()
        responsesSub.request(1)
        responses.expectError().getMessage should equal(
          "HTTP message had declared Content-Length 8 but entity data stream amounts to more bytes")
      }

      "catch illegal response starts" in new TestSetup {
        sendStandardRequest()
        sendWireData(
          """HTTP/1.2 200 OK
            |
            |""")

        responsesSub.request(1)
        val error @ IllegalResponseException(info) = responses.expectError()
        info.formatPretty shouldEqual
        "The server-side protocol or HTTP version is not supported: start of response: [48 54 54 50 2F 31 2E 32 20 32 30 30 20 4F 4B 0D  | HTTP/1.2 200 OK.]"
        netOut.expectError(error)
        requestsSub.expectCancellation()
        netInSub.expectCancellation()
      }

      "catch illegal response chunks" in new TestSetup {
        sendStandardRequest()
        sendWireData(
          """HTTP/1.1 200 OK
            |Transfer-Encoding: chunked
            |
            |""")

        responsesSub.request(1)
        val HttpResponse(_, _, HttpEntity.Chunked(ct, chunks), _) = responses.expectNext()
        ct shouldEqual ContentTypes.`application/octet-stream`

        val probe = TestSubscriber.manualProbe[ChunkStreamPart]()
        chunks.runWith(Sink.fromSubscriber(probe))
        val sub = probe.expectSubscription()

        sendWireData("3\nABC\n")
        sub.request(1)
        probe.expectNext(HttpEntity.Chunk("ABC"))

        sendWireData("4\nDEFXX")
        sub.request(1)
        val _ @EntityStreamException(info) = probe.expectError()
        info.summary shouldEqual "Illegal chunk termination"

        responses.expectComplete()
        netOut.expectComplete()
        requestsSub.expectCancellation()
        netInSub.expectCancellation()
      }

      "fail a chunked response entity when the connection closes before the first chunk" in new TestSetup {
        sendStandardRequest()

        // The peer starts a normal response, but its application has not produced a body chunk yet.
        val response = HttpResponse(entity = Chunked(ContentTypes.`application/octet-stream`, Source.maybe))
        val ResponseRenderingOutput.HttpData(initialHeaders) = Source.single(ResponseRenderingContext(response))
          .via(new HttpResponseRendererFactory(None, 256, NoLogging, DateHeaderRendering()).renderer)
          .runWith(Sink.head)
          .awaitResult(3.seconds.dilated)
        sendWireData(initialHeaders)

        val received = expectResponse()
        received.status shouldBe StatusCodes.OK
        val Chunked(_, chunks) = received.entity
        val probe = TestSubscriber.manualProbe[ChunkStreamPart]()
        chunks.runWith(Sink.fromSubscriber(probe))
        probe.expectSubscription().request(1)

        closeNetworkInput()

        probe.expectError() shouldBe an[EntityStreamException]
        responses.expectComplete()
        netOut.expectComplete()
        requestsSub.expectCancellation()
      }

      for ((phase, lineEnding) <- List(
          "before the status line ends" -> "",
          "after the status line but before the headers end" -> "\r\n"))
        s"catch a response start truncation $phase" in new TestSetup {
          sendStandardRequest()
          sendWireData(s"HTTP/1.1 200 OK$lineEnding")
          netInSub.sendComplete()

          responsesSub.request(1)
          val error @ IllegalResponseException(info) = responses.expectError()
          info.summary shouldEqual "Illegal HTTP message start"
          netOut.expectError(error)
          requestsSub.expectCancellation()
        }
    }

    def isDefinedVia = afterWord("is defined via")
    "support response length verification".which(isDefinedVia {
      import HttpEntity._

      class LengthVerificationTest(maxContentLength: Int) extends TestSetup(maxContentLength) {
        val entityBase = "0123456789ABCD"

        def sendStrictResponseWithLength(bytes: Int) =
          sendWireData(
            s"""HTTP/1.1 200 OK
               |Content-Length: $bytes
               |
               |${entityBase.take(bytes)}""")
        def sendDefaultResponseWithLength(bytes: Int) = {
          sendWireData(
            s"""HTTP/1.1 200 OK
               |Content-Length: $bytes
               |
               |${entityBase.take(3)}""")
          sendWireData(entityBase.slice(3, 7))
          sendWireData(entityBase.slice(7, bytes))
        }
        def sendChunkedResponseWithLength(bytes: Int) =
          sendWireData(
            s"""HTTP/1.1 200 OK
               |Transfer-Encoding: chunked
               |
               |3
               |${entityBase.take(3)}
               |4
               |${entityBase.slice(3, 7)}
               |${bytes - 7}
               |${entityBase.slice(7, bytes)}
               |0
               |
               |""")
        def sendCloseDelimitedResponseWithLength(bytes: Int) = {
          sendWireData(
            s"""HTTP/1.1 200 OK
               |
               |${entityBase.take(3)}""")
          sendWireData(entityBase.slice(3, 7))
          sendWireData(entityBase.slice(7, bytes))
          netInSub.sendComplete()
        }

        implicit class XResponse(response: HttpResponse) {
          val timeout = 500.millis.dilated

          def expectStrictEntityWithLength(bytes: Int) =
            response shouldEqual HttpResponse(
              entity = Strict(ContentTypes.`application/octet-stream`, ByteString(entityBase.take(bytes))))

          def expectEntity[T <: HttpEntity: ClassTag](bytes: Int) =
            inside(response) {
              case HttpResponse(_, _, entity: T, _) =>
                entity.toStrict(100.millis.dilated).awaitResult(timeout).data.utf8String shouldEqual entityBase.take(
                  bytes)
            }

          def expectSizeErrorInEntityOfType[T <: HttpEntity: ClassTag](limit: Int, actualSize: Option[Long] = None) =
            inside(response) {
              case HttpResponse(_, _, entity: T, _) =>
                def gatherBytes = entity.dataBytes.runFold(ByteString.empty)(_ ++ _).awaitResult(timeout)
                (the[RuntimeException] thrownBy gatherBytes).getCause shouldEqual EntityStreamSizeException(limit,
                  actualSize)
            }
        }
      }

      "the config setting (strict entity)" in new LengthVerificationTest(maxContentLength = 10) {
        sendStandardRequest()
        sendStrictResponseWithLength(10)
        expectResponse().expectStrictEntityWithLength(10)

        // entities that would be strict but have a Content-Length > the configured maximum are delivered
        // as single element Default entities!
        sendStandardRequest()
        sendStrictResponseWithLength(11)
        expectResponse().expectSizeErrorInEntityOfType[Default](limit = 10, actualSize = Some(11))
      }

      "the config setting (default entity)" in new LengthVerificationTest(maxContentLength = 10) {
        sendStandardRequest()
        sendDefaultResponseWithLength(10)
        expectResponse().expectEntity[Default](10)

        sendStandardRequest()
        sendDefaultResponseWithLength(11)
        expectResponse().expectSizeErrorInEntityOfType[Default](limit = 10, actualSize = Some(11))
      }

      "the config setting (chunked entity)" in new LengthVerificationTest(maxContentLength = 10) {
        sendStandardRequest()
        sendChunkedResponseWithLength(10)
        expectResponse().expectEntity[Chunked](10)

        sendStandardRequest()
        sendChunkedResponseWithLength(11)
        expectResponse().expectSizeErrorInEntityOfType[Chunked](limit = 10)
      }

      "a header value limit smaller than a valid response chunk" in new TestSetup(maxResponseContentLength = 128) {
        override def settings: ClientConnectionSettings = {
          val base = super.settings
          base.withParserSettings(base.parserSettings.withMaxHeaderValueLength(16).withMaxChunkSize(64))
        }

        val body = ByteString("the payload is not a header value")
        body.length should be > settings.parserSettings.maxHeaderValueLength
        sendStandardRequest()
        sendWireData(
          s"""HTTP/1.1 200 OK
             |Transfer-Encoding: chunked
             |
             |${Integer.toHexString(body.length)}
             |${body.utf8String}
             |0
             |
             |""")

        val response = expectResponse()
        response.status shouldBe StatusCodes.OK
        inside(response.entity) {
          case Chunked(_, chunks) =>
            chunks.runWith(Sink.seq).awaitResult(1.second.dilated) shouldEqual Seq(Chunk(body), LastChunk)
        }

        requestsSub.sendComplete()
        netOut.expectComplete()
        netInSub.sendComplete()
        responses.expectComplete()
      }

      "the config setting (close-delimited entity)" in {
        new LengthVerificationTest(maxContentLength = 10) {
          sendStandardRequest()
          sendCloseDelimitedResponseWithLength(10)
          expectResponse().expectEntity[CloseDelimited](10)
        }
        new LengthVerificationTest(maxContentLength = 10) {
          sendStandardRequest()
          sendCloseDelimitedResponseWithLength(11)
          expectResponse().expectSizeErrorInEntityOfType[CloseDelimited](limit = 10)
        }
      }

      "a smaller programmatically-set limit (strict entity)" in new LengthVerificationTest(maxContentLength = 12) {
        sendStandardRequest()
        sendStrictResponseWithLength(10)
        expectResponse().mapEntity(_.withSizeLimit(10)).expectStrictEntityWithLength(10)

        // entities that would be strict but have a Content-Length > the configured maximum are delivered
        // as single element Default entities!
        sendStandardRequest()
        sendStrictResponseWithLength(11)
        expectResponse().mapEntity(_.withSizeLimit(10))
          .expectSizeErrorInEntityOfType[Default](limit = 10, actualSize = Some(11))
      }

      "a smaller programmatically-set limit (default entity)" in new LengthVerificationTest(maxContentLength = 12) {
        sendStandardRequest()
        sendDefaultResponseWithLength(10)
        expectResponse().mapEntity(_.withSizeLimit(10)).expectEntity[Default](10)

        sendStandardRequest()
        sendDefaultResponseWithLength(11)
        expectResponse().mapEntity(_.withSizeLimit(10))
          .expectSizeErrorInEntityOfType[Default](limit = 10, actualSize = Some(11))
      }

      "a smaller programmatically-set limit (chunked entity)" in new LengthVerificationTest(maxContentLength = 12) {
        sendStandardRequest()
        sendChunkedResponseWithLength(10)
        expectResponse().mapEntity(_.withSizeLimit(10)).expectEntity[Chunked](10)

        sendStandardRequest()
        sendChunkedResponseWithLength(11)
        expectResponse().mapEntity(_.withSizeLimit(10)).expectSizeErrorInEntityOfType[Chunked](limit = 10)
      }

      "a smaller programmatically-set limit (close-delimited entity)" in {
        new LengthVerificationTest(maxContentLength = 12) {
          sendStandardRequest()
          sendCloseDelimitedResponseWithLength(10)
          expectResponse().mapEntity(_.withSizeLimit(10)).expectEntity[CloseDelimited](10)
        }
        new LengthVerificationTest(maxContentLength = 12) {
          sendStandardRequest()
          sendCloseDelimitedResponseWithLength(11)
          expectResponse().mapEntity(_.withSizeLimit(10)).expectSizeErrorInEntityOfType[CloseDelimited](limit = 10)
        }
      }

      "a larger programmatically-set limit (strict entity)" in new LengthVerificationTest(maxContentLength = 8) {
        // entities that would be strict but have a Content-Length > the configured maximum are delivered
        // as single element Default entities!
        sendStandardRequest()
        sendStrictResponseWithLength(10)
        expectResponse().mapEntity(_.withSizeLimit(10)).expectEntity[Default](10)

        sendStandardRequest()
        sendStrictResponseWithLength(11)
        expectResponse().mapEntity(_.withSizeLimit(10))
          .expectSizeErrorInEntityOfType[Default](limit = 10, actualSize = Some(11))
      }

      "a larger programmatically-set limit (default entity)" in new LengthVerificationTest(maxContentLength = 8) {
        sendStandardRequest()
        sendDefaultResponseWithLength(10)
        expectResponse().mapEntity(_.withSizeLimit(10)).expectEntity[Default](10)

        sendStandardRequest()
        sendDefaultResponseWithLength(11)
        expectResponse().mapEntity(_.withSizeLimit(10))
          .expectSizeErrorInEntityOfType[Default](limit = 10, actualSize = Some(11))
      }

      "a larger programmatically-set limit (chunked entity)" in new LengthVerificationTest(maxContentLength = 8) {
        sendStandardRequest()
        sendChunkedResponseWithLength(10)
        expectResponse().mapEntity(_.withSizeLimit(10)).expectEntity[Chunked](10)

        sendStandardRequest()
        sendChunkedResponseWithLength(11)
        expectResponse().mapEntity(_.withSizeLimit(10))
          .expectSizeErrorInEntityOfType[Chunked](limit = 10)
      }

      "a larger programmatically-set limit (close-delimited entity)" in {
        new LengthVerificationTest(maxContentLength = 8) {
          sendStandardRequest()
          sendCloseDelimitedResponseWithLength(10)
          expectResponse().mapEntity(_.withSizeLimit(10)).expectEntity[CloseDelimited](10)
        }
        new LengthVerificationTest(maxContentLength = 8) {
          sendStandardRequest()
          sendCloseDelimitedResponseWithLength(11)
          expectResponse().mapEntity(_.withSizeLimit(10)).expectSizeErrorInEntityOfType[CloseDelimited](limit = 10)
        }
      }
    })

    "support requests with an `Expect: 100-continue` headers".which {

      "have a strict entity and receive a `100 Continue` response" in new TestSetup {
        requestsSub.sendNext(HttpRequest(POST, headers = List(Expect.`100-continue`), entity = "ABCDEF"))
        expectWireData(
          """POST / HTTP/1.1
            |Expect: 100-continue
            |Host: example.com
            |User-Agent: pekko-http/test
            |Content-Type: text/plain; charset=UTF-8
            |Content-Length: 6
            |
            |""")
        netOutSub.request(1)
        netOut.expectNoMessage(50.millis)

        sendWireData(
          """HTTP/1.1 100 Continue
            |
            |""")

        netOut.expectNext().utf8String shouldEqual "ABCDEF"

        sendWireData(
          """HTTP/1.1 200 OK
            |Content-Length: 0
            |
            |""")

        expectResponse() shouldEqual HttpResponse()

        requestsSub.sendComplete()
        netOut.expectComplete()
        netInSub.sendComplete()
        responses.expectComplete()
      }

      "have a default entity and receive a `100 Continue` response" in new TestSetup {
        val entityParts = List("ABC", "DE", "FGH").map(ByteString(_))
        requestsSub.sendNext(HttpRequest(POST, headers = List(Expect.`100-continue`),
          entity = HttpEntity(ContentTypes.`application/octet-stream`, 8, Source(entityParts))))
        expectWireData(
          """POST / HTTP/1.1
            |Expect: 100-continue
            |Host: example.com
            |User-Agent: pekko-http/test
            |Content-Type: application/octet-stream
            |Content-Length: 8
            |
            |""")
        netOutSub.request(1)
        netOut.expectNoMessage(50.millis)

        sendWireData(
          """HTTP/1.1 100 Continue
            |
            |""")

        netOut.expectNext().utf8String shouldEqual "ABC"
        expectWireData("DE")
        expectWireData("FGH")

        sendWireData(
          """HTTP/1.1 200 OK
            |Content-Length: 0
            |
            |""")

        expectResponse() shouldEqual HttpResponse()

        requestsSub.sendComplete()
        netOut.expectComplete()
        netInSub.sendComplete()
        responses.expectComplete()
      }

      "receive a normal response" in new TestSetup {
        requestsSub.sendNext(HttpRequest(POST, headers = List(Expect.`100-continue`), entity = "ABCDEF"))
        expectWireData(
          """POST / HTTP/1.1
            |Expect: 100-continue
            |Host: example.com
            |User-Agent: pekko-http/test
            |Content-Type: text/plain; charset=UTF-8
            |Content-Length: 6
            |
            |""")
        netOutSub.request(1)
        netOut.expectNoMessage(50.millis)

        sendWireData(
          """HTTP/1.1 200 OK
            |Content-Length: 0
            |
            |""")

        expectResponse() shouldEqual HttpResponse()

        expectWireData("ABCDEF")

        requestsSub.sendComplete()
        netOut.expectComplete()
        netInSub.sendComplete()
        responses.expectComplete()
      }

      "receive an error response" in new TestSetup {
        requestsSub.sendNext(HttpRequest(POST, headers = List(Expect.`100-continue`), entity = "ABCDEF"))
        requestsSub.sendComplete()
        expectWireData(
          """POST / HTTP/1.1
            |Expect: 100-continue
            |Host: example.com
            |User-Agent: pekko-http/test
            |Content-Type: text/plain; charset=UTF-8
            |Content-Length: 6
            |
            |""")
        netOutSub.request(1)
        netOut.expectNoMessage(50.millis)

        sendWireData(
          """HTTP/1.1 400 Bad Request
            |Content-Length: 0
            |
            |""")

        expectResponse() shouldEqual HttpResponse(400)

        netOut.expectComplete()
        netInSub.sendComplete()
        responses.expectComplete()
      }
    }

    "observe the native Expect gate for an internally prepared request".which {
      class PreparedPut extends TestSetup {
        val body = "document"
        val request = HttpRequest(PUT, "/document", headers = List(Expect.`100-continue`), entity = body)
        val (prepared, observation) = Http1ExpectContinueAttempt.prepare(request,
          ExpectContinuePolicy.retryIdempotentOnce).get

        def sendRequestHead(): Unit = {
          requestsSub.sendNext(prepared)
          requestsSub.sendComplete()
          expectWireData(
            """PUT /document HTTP/1.1
              |Expect: 100-continue
              |Host: example.com
              |User-Agent: pekko-http/test
              |Content-Type: text/plain; charset=UTF-8
              |Content-Length: 8
              |
              |""")
        }

        def receiveFinalRejection(): HttpResponse = {
          sendWireData(
            """HTTP/1.1 417 Expectation Failed
              |Connection: close
              |Content-Length: 0
              |
              |""")
          val response = expectResponse()
          response.status shouldBe StatusCodes.ExpectationFailed
          response.entity.isKnownEmpty shouldBe true
          netOut.expectComplete()
          netInSub.sendComplete()
          responses.expectComplete()
          response
        }
      }

      "observe a final 417 before sending the body only once" in new PreparedPut {
        sendRequestHead()
        // Keep demand outstanding so a stray body element cannot hide behind backpressure.
        netOutSub.request(1)
        val response = receiveFinalRejection()
        observation.takeRejectionBeforeBody(response) shouldBe true
        observation.takeRejectionBeforeBody(response) shouldBe false
      }

      "not observe a pre-body rejection after an ordinary 100 releases the upload" in new PreparedPut {
        sendRequestHead()
        sendWireData(
          """HTTP/1.1 100 Continue
            |
            |""")
        expectWireData(body)
        netOutSub.request(1)
        // An interim response from the first hop does not preclude a final 417 from the response chain.
        val response = receiveFinalRejection()
        observation.takeRejectionBeforeBody(response) shouldBe false
      }
    }

    "dispatch a privately selected one-request Expect recovery".which {
      "finish a streamed early 417 on its old connection and recover once on the same origin" in
      new ManagedExpectSetup {
        checkRecovery(executor)
      }

      "recover and return one final response through the public Scala request function" in new ManagedExpectSetup {
        checkRecovery(scalaExecutor)
      }

      "recover and return one final response through the public Java request function" in new ManagedExpectSetup {
        checkRecovery(javaExecutor)
      }

      "give a vetoed streamed 417 to the caller without subscribing to its explanation" in new ManagedExpectSetup {
        val veto: ExpectContinuePolicy = _ => false
        val result = executor(ExpectContinuePolicy.retryIdempotentOnce)
          .apply(request.addAttribute(ExpectContinuePolicy.attributeKey, veto))
        val first = connected()
        first.expectRequest(expectContinue = true)
        first.toServer.request(1)
        first.sendRejectionHead()
        val selected = result.awaitResult(3.seconds.dilated)
        selected.status shouldBe StatusCodes.ExpectationFailed
        selected.entity.isDefault shouldBe true
        selected.header[Connection] shouldBe Some(Connection("close"))
        val received = selected.entity.dataBytes.runFold(ByteString.empty)(_ ++ _)
        first.fromServer.sendNext(ByteString(explanation))
        received.awaitResult(3.seconds.dilated) shouldBe ByteString(explanation)
        first.toServer.expectComplete()
        first.fromServer.expectCancellation()
        materialized.get() shouldBe 1
        delegated.get() shouldBe 0
      }

      "delegate stock disabled, unselected and application-targeted requests unchanged" in new ManagedExpectSetup {
        (executor(ExpectContinuePolicy.disabled) should be).theSameInstanceAs(ordinary)
        val missingExpect = request.removeHeader("expect")
        val differentPort = request.withUri(Uri("http://example.com:8081/document"))
        val explicitHost = request.addHeader(Host("example.com", 8080))
        val rawHost = request.addHeader(RawHeader("hOsT", "example.com:8080"))
        val explicitTarget = request.addHeader(`Raw-Request-URI`("/document"))
        val rawTarget = request.addHeader(RawHeader("rAw-ReQuEsT-uRi", "/document"))
        val nonIdempotent = request.withMethod(POST)
        val streamed = request.withEntity(
          HttpEntity(ContentTypes.`text/plain(UTF-8)`, Source.single(ByteString(body))))
        val permissivePolicy: ExpectContinuePolicy = _ => true
        val execute = executor(permissivePolicy)
        for (unselected <- List(missingExpect, differentPort, explicitHost, rawHost, explicitTarget, rawTarget,
            nonIdempotent, streamed)) {
          execute(unselected).awaitResult(3.seconds.dilated) shouldBe
          ordinaryResponse
          (ordinaryRequests.expectMsg(unselected) should be).theSameInstanceAs(unselected)
        }
        materialized.get() shouldBe 0
        delegated.get() shouldBe 8
      }
    }

    "ignore interim 1xx responses" in new TestSetup {
      sendStandardRequest()
      sendWireData(
        """HTTP/1.1 102 Processing
          |Content-Length: 0
          |
          |""")
      sendWireData(
        """HTTP/1.1 102 Processing
          |Content-Length: 0
          |
          |""")
      sendWireData(
        """HTTP/1.1 200 OK
          |Content-Length: 0
          |
          |""")

      expectResponse() shouldEqual HttpResponse()

      requestsSub.sendComplete()
      netOut.expectComplete()
      netInSub.sendComplete()
      responses.expectComplete()
    }

    "receive Content-Length for HEAD requests" in new TestSetup {
      requestsSub.sendNext(HttpRequest(method = HttpMethods.HEAD))
      expectWireData(
        """HEAD / HTTP/1.1
          |Host: example.com
          |User-Agent: pekko-http/test
          |
          |""")
      sendWireData(
        """HTTP/1.1 200 OK
          |Content-Length: 6
          |
          |""")

      val HttpResponse(StatusCodes.OK, _, HttpEntity.Default(_, contentLength, data), _) = expectResponse()
      contentLength shouldEqual 6

      val chunks = data.runWith(Sink.seq).awaitResult(3.seconds.dilated)
      chunks.length shouldEqual 0

      requestsSub.sendComplete()
      netOut.expectComplete()
      netInSub.sendComplete()
      responses.expectComplete()
    }
  }

  private class ManagedExpectSetup {
    val body = "document"
    val explanation = "This endpoint does not support the expectation."
    val origin = Uri("http://example.com:8080")
    val request = HttpRequest(PUT, origin.withPath(Uri.Path("/document")), headers = List(Expect.`100-continue`),
      entity = body)

    val materialized = new AtomicInteger()
    val delegated = new AtomicInteger()
    private val opened = TestProbe()
    val ordinaryRequests = TestProbe()
    val ordinaryResponse = HttpResponse(StatusCodes.Accepted)
    val ordinary: HttpRequest => Future[HttpResponse] = r => {
      delegated.incrementAndGet()
      ordinaryRequests.ref ! r
      Future.successful(ordinaryResponse)
    }

    final class Wire(val host: String, val port: Int) {
      val fromServer: TestPublisher.Probe[ByteString] = TestPublisher.probe[ByteString]()
      val toServer: TestSubscriber.Probe[ByteString] = TestSubscriber.probe[ByteString]()

      def expectRequest(expectContinue: Boolean): Unit = {
        val expect = if (expectContinue) "Expect: 100-continue\r\n" else ""
        val content = if (expectContinue) "" else body
        toServer.requestNext().utf8String shouldBe (
          "PUT /document HTTP/1.1\r\n" + expect +
          "Host: example.com:8080\r\nUser-Agent: pekko-http/test\r\nContent-Type: text/plain; charset=UTF-8\r\n" +
          "Content-Length: 8\r\n\r\n" + content)
      }

      def sendRejectionHead(): Unit =
        fromServer.sendNext(ByteString(
          s"HTTP/1.1 417 Expectation Failed\r\nConnection: close\r\nContent-Length: ${explanation.length}\r\n\r\n"))
    }

    private val transport = new ClientTransport {
      override def connectTo(host: String, port: Int, settings: ClientConnectionSettings)(
          implicit actorSystem: ActorSystem): Flow[ByteString, ByteString, Future[Http.OutgoingConnection]] = {
        val wire = new Wire(host, port)
        Flow.fromSinkAndSource(Sink.fromSubscriber(wire.toServer),
          Source.fromPublisher(wire.fromServer)).mapMaterializedValue { _ =>
          materialized.incrementAndGet()
          opened.ref ! wire
          Future.successful(Http.OutgoingConnection(InetSocketAddress.createUnresolved("client", 12345),
            InetSocketAddress.createUnresolved(host, port)))
        }
      }
    }

    def connected(): Wire = {
      val next = opened.expectMsgType[Wire]
      next.host shouldBe "example.com"
      next.port shouldBe 8080
      next
    }

    private val settings = ClientConnectionSettings(system)
      .withUserAgentHeader(Some(`User-Agent`(List(ProductVersion("pekko-http", "test")))))
      .withTransport(transport)
    private val poolSettings = ConnectionPoolSettings(system).withConnectionSettings(settings)
    private val budget = 15.seconds.dilated

    def executor(policy: ExpectContinuePolicy): HttpRequest => Future[HttpResponse] = {
      val http = Http()
      Http1ExpectContinueExecutor.wrap(ordinary, origin, policy, budget, http,
        http.defaultClientHttpsContext, settings, NoLogging)
    }

    def scalaExecutor(policy: ExpectContinuePolicy): HttpRequest => Future[HttpResponse] = {
      val http = Http()
      http.singleRequestWithExpectContinueRetry(origin, policy, budget, http.defaultClientHttpsContext,
        poolSettings, NoLogging)
    }

    def javaExecutor(policy: ExpectContinuePolicy): HttpRequest => Future[HttpResponse] = {
      import scala.jdk.FutureConverters._
      val http = pekko.http.javadsl.Http.get(system)
      val execute = http.singleRequestWithExpectContinueRetry(
        pekko.http.javadsl.model.Uri.create(origin),
        policy,
        java.time.Duration.ofNanos(budget.toNanos),
        http.defaultClientHttpsContext,
        poolSettings,
        NoLogging)
      request => execute.apply(request).asScala.map(JavaMapping.HttpResponse.toScala)
    }

    def checkRecovery(createExecutor: ExpectContinuePolicy => (HttpRequest => Future[HttpResponse])): Unit = {
      val verdict = Promise[ExpectContinueRetryContext]()
      val observePolicy: ExpectContinuePolicy = context => {
        verdict.success(context)
        ExpectContinuePolicy.retryIdempotentOnce.shouldRetry(context)
      }

      val result = createExecutor(observePolicy).apply(request)
      val first = connected()
      first.expectRequest(expectContinue = true)
      first.toServer.request(1)
      first.sendRejectionHead()
      val context = verdict.future.awaitResult(3.seconds.dilated)
      context.getMethod shouldBe PUT
      context.getRequestUri shouldBe request.getUri
      context.getProtocol shouldBe HttpProtocols.`HTTP/1.1`
      context.getContentLength shouldBe body.length.toLong
      first.fromServer.sendNext(ByteString(explanation))
      first.toServer.expectComplete()
      first.fromServer.expectCancellation()

      val second = connected()
      second.expectRequest(expectContinue = false)
      result.isCompleted shouldBe false
      second.fromServer.sendNext(ByteString("HTTP/1.1 200 OK\r\nConnection: close\r\nContent-Length: 6\r\n\r\n"))
      val selected = result.awaitResult(3.seconds.dilated)
      selected.status shouldBe StatusCodes.OK
      selected.entity.isDefault shouldBe true
      val received = selected.entity.dataBytes.runFold(ByteString.empty)(_ ++ _)
      second.fromServer.sendNext(ByteString("stored"))
      received.awaitResult(3.seconds.dilated) shouldBe ByteString("stored")
      second.toServer.expectComplete()
      second.fromServer.expectCancellation()
      materialized.get() shouldBe 2
      delegated.get() shouldBe 0
    }
  }

  class TestSetup(maxResponseContentLength: Int = -1, config: String = "") {
    val requests = TestPublisher.manualProbe[HttpRequest]()
    val responses = TestSubscriber.manualProbe[HttpResponse]()

    def settings = {
      val s = ClientConnectionSettings(
        ConfigFactory.parseString(config).withFallback(system.settings.config)).withUserAgentHeader(
        Some(`User-Agent`(List(ProductVersion("pekko-http", "test")))))
      if (maxResponseContentLength < 0) s
      else s.withParserSettings(s.parserSettings.withMaxContentLength(maxResponseContentLength))
    }

    val (netOut, netIn) = {
      val netOut = TestSubscriber.manualProbe[ByteString]()
      val netIn = TestPublisher.manualProbe[ByteString]()

      RunnableGraph.fromGraph(GraphDSL.createGraph(OutgoingConnectionBlueprint(Host("example.com"), settings,
        NoLogging)) { implicit b => client =>
        import GraphDSL.Implicits._
        Source.fromPublisher(netIn)    ~> Flow[ByteString].map(SessionBytes(null, _))             ~> client.in2
        client.out1                    ~> Flow[SslTlsOutbound].collect { case SendBytes(x) => x } ~> Sink.fromSubscriber(netOut)
        Source.fromPublisher(requests) ~> client.in1
        client.out2                    ~> Sink.fromSubscriber(responses)
        ClosedShape
      }).run()

      netOut -> netIn
    }

    def wipeDate(string: String) =
      string.fastSplit('\n').map {
        case s if s.startsWith("Date:") => "Date: XXXX\r"
        case s                          => s
      }.mkString("\n")

    val netInSub = netIn.expectSubscription()
    val netOutSub = netOut.expectSubscription()
    val requestsSub = requests.expectSubscription()
    val responsesSub = responses.expectSubscription()

    requestsSub.expectRequest(16)
    netInSub.expectRequest(16)

    def sendWireData(data: String): Unit = sendWireData(ByteString(data.stripMarginWithNewline("\r\n"), "ASCII"))
    def sendWireData(data: ByteString): Unit = netInSub.sendNext(data)

    def expectWireData(s: String) = {
      netOutSub.request(1)
      netOut.expectNext().utf8String shouldEqual s.stripMarginWithNewline("\r\n")
    }

    def closeNetworkInput(): Unit = netInSub.sendComplete()

    def sendStandardRequest() = {
      requestsSub.sendNext(HttpRequest())
      expectWireData(
        """GET / HTTP/1.1
          |Host: example.com
          |User-Agent: pekko-http/test
          |
          |""")
    }

    def expectResponse() = {
      responsesSub.request(1)
      responses.expectNext()
    }

  }
}
