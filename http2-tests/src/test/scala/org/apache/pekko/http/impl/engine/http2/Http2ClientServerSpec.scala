/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.impl.engine.http2

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.zip.InflaterInputStream

import scala.concurrent.{ Future, Promise }
import scala.concurrent.duration._
import scala.util.Using

import org.apache.pekko
import pekko.Done
import pekko.http.impl.engine.HttpIdleTimeoutException
import pekko.http.impl.engine.ws.ByteStringSinkProbe
import pekko.http.impl.util.{ ExampleHttpContexts, PekkoSpecWithMaterializer }
import pekko.http.scaladsl.Http
import pekko.http.scaladsl.coding.Coders
import pekko.http.scaladsl.model.{
  headers,
  AttributeKey,
  ContentType,
  ContentTypes,
  HttpCharsets,
  HttpEntity,
  HttpHeader,
  HttpMethod,
  HttpMethods,
  HttpProtocol,
  HttpProtocols,
  HttpRequest,
  HttpResponse,
  MediaTypes,
  RequestResponseAssociation,
  StatusCode,
  StatusCodes,
  Uri
}
import pekko.http.scaladsl.model.headers.HttpEncodings
import pekko.http.scaladsl.server.{ Directives, Route }
import pekko.http.scaladsl.settings.ClientConnectionSettings
import pekko.http.scaladsl.settings.ServerSettings
import pekko.http.scaladsl.unmarshalling.Unmarshal
import pekko.stream.StreamTcpException
import pekko.stream.scaladsl.{ Flow, Sink, Source }
import pekko.stream.testkit.{ TestPublisher, TestSubscriber }
import pekko.stream.testkit.Utils.TE
import pekko.testkit.EventFilter
import pekko.testkit.TestProbe
import pekko.util.ByteString

import org.scalatest.concurrent.ScalaFutures

class Http2ClientServerSpec extends PekkoSpecWithMaterializer(
      """pekko.http.server.http2.log-frames = on
     pekko.http.server.log-unencrypted-network-bytes = 100
     pekko.http.client.http2.log-frames = on
     pekko.http.client.log-unencrypted-network-bytes = 100
     pekko.actor.serialize-messages = false
  """) with ScalaFutures {
  override protected def failOnSevereMessages: Boolean = true

  case class RequestId(id: String) extends RequestResponseAssociation
  val requestIdAttr = AttributeKey[RequestId]("requestId")

  "HTTP 2 implementation" should {
    "support simple round-trips" in new TestSetup {
      sendClientRequest(
        HttpRequest(
          method = HttpMethods.POST,
          entity = "ping",
          headers = headers.`Accept-Encoding`(HttpEncodings.gzip) :: Nil)
          .addAttribute(requestIdAttr, RequestId("request-1")))

      val serverRequest = expectServerRequest()
      serverRequest.request.attribute(Http2.streamId) shouldBe Symbol("nonEmpty")
      serverRequest.request.method shouldBe HttpMethods.POST
      serverRequest.request.header[headers.`Accept-Encoding`] should not be empty
      Unmarshal(serverRequest.request.entity).to[String].futureValue shouldBe "ping"
      serverRequest.sendResponse(HttpResponse(entity = "pong"))

      val response = expectClientResponse()
      Unmarshal(response.entity).to[String].futureValue shouldBe "pong"
      response.attribute(requestIdAttr).get.id shouldBe "request-1"
    }
    "preserve repeated Set-Cookie field order across an HTTP/2 response" in new TestSetup {
      try {
        val theme = headers.`Set-Cookie`(headers.HttpCookie("theme", "dark"))
        val language = headers.`Set-Cookie`(headers.HttpCookie("language", "en"))
        val marker = headers.RawHeader("x-example-marker", "kept")
        val http2 = pekko.http.scaladsl.model.HttpProtocols.`HTTP/2.0`

        sendClientRequest(HttpRequest(uri = "/preferences"))
        val serverRequest = expectServerRequest()
        serverRequest.request.protocol shouldBe http2
        serverRequest.sendResponse(HttpResponse(headers = List(theme, marker, language)))

        val response = expectClientResponse()
        response.protocol shouldBe http2
        response.status shouldBe StatusCodes.OK
        response.headers.filter(_.is("set-cookie")) shouldEqual Seq(theme, language)
        response.header(marker.name) shouldBe Some(marker)
      } finally {
        clientRequestsOut.sendComplete()
        binding.terminate(1.second).futureValue(timeout(3.seconds))
      }
    }
    "wait for the streamed response to finish when discarding and then serve another request" in new TestSetup {
      sendClientRequest(HttpRequest(uri = "/discard"))
      val firstRequest = expectServerRequest()
      firstRequest.request.uri.path.toString shouldBe "/discard"
      firstRequest.request.attribute(Http2.streamId) shouldBe Some(1)
      val responseBytes = firstRequest.sendResponseWithEntityStream()

      val firstResponse = expectClientResponse()
      firstResponse.status shouldBe StatusCodes.OK
      val consumed = ByteStringSinkProbe()
      val observedEntity = firstResponse.entity.transformDataBytes(Flow[ByteString].alsoTo(consumed.sink))
      val completion = TestProbe()
      firstResponse.withEntity(observedEntity).discardEntityBytes().future
        .foreach(result => completion.ref ! result)(system.dispatcher)

      responseBytes.sendNext(ByteString("not "))
      responseBytes.sendNext(ByteString("needed"))
      consumed.expectUtf8EncodedString("not needed")
      // The producer stays open until the test sends EOF, so eager completion is not a successful drain.
      completion.expectNoMessage(100.millis)
      responseBytes.sendComplete()
      completion.expectMsg(Done)
      consumed.expectComplete()

      // Independently verify reuse of this direct connection; the small body does not block another stream.
      sendClientRequest(HttpRequest(uri = "/next"))
      val secondRequest = expectServerRequest()
      secondRequest.request.uri.path.toString shouldBe "/next"
      secondRequest.request.attribute(Http2.streamId) shouldBe Some(3)
      secondRequest.sendResponse(HttpResponse(entity = "next response"))

      val secondResponse = expectClientResponse()
      secondResponse.status shouldBe StatusCodes.OK
      Unmarshal(secondResponse.entity).to[String].futureValue shouldBe "next response"
    }
    "relay a streamed entity across HTTP/2 connections before the source completes" in {
      val http = Http()
      val prefix = ByteString("first part ")
      val suffix = ByteString("last part")
      val tail = Promise[ByteString]()
      val allowRead = Promise[Unit]()
      val downstreamRequest = Promise[HttpRequest]()
      val received = ByteStringSinkProbe()

      def bind(handler: HttpRequest => Future[HttpResponse]) =
        http.newServerAt("127.0.0.1", 0).bind(handler)

      def responses(binding: Http.ServerBinding, request: HttpRequest) =
        Source.single(request)
          .via(http.connectionTo("127.0.0.1").toPort(binding.localAddress.getPort).http2WithPriorKnowledge())
          .runWith(Sink.queue[HttpResponse]())

      val origin = bind { _ =>
        val entity = HttpEntity(ContentTypes.`application/octet-stream`,
          Source.single(prefix).concat(Source.future(tail.future)))
        Future.successful(HttpResponse(entity = entity))
      }
      val destination = bind { request =>
        downstreamRequest.success(request)
        allowRead.future.flatMap { _ =>
          request.entity.dataBytes
            .alsoTo(received.sink)
            .runFold(ByteString.empty)(_ ++ _)
            .map(bytes => HttpResponse(entity = HttpEntity(bytes)))(system.dispatcher)
        }(system.dispatcher)
      }

      try {
        val sourceResponses = responses(origin.futureValue, HttpRequest(uri = "/source"))
        try {
          val sourceResponse = sourceResponses.pull().futureValue.get
          sourceResponse.status shouldBe StatusCodes.OK
          sourceResponse.protocol shouldBe HttpProtocols.`HTTP/2.0`

          val forwardedEntity =
            HttpEntity(sourceResponse.entity.contentType, sourceResponse.entity.dataBytes)
          val request = HttpRequest(HttpMethods.POST, "/destination", entity = forwardedEntity)
          val destinationResponses = responses(destination.futureValue, request)
          try {
            downstreamRequest.future.futureValue.protocol shouldBe HttpProtocols.`HTTP/2.0`
            allowRead.success(())
            received.expectBytes(prefix)
            tail.success(suffix)
            received.expectBytes(suffix)
            received.expectComplete()

            val response = destinationResponses.pull().futureValue.get
            response.status shouldBe StatusCodes.OK
            response.entity.toStrict(3.seconds).futureValue.data shouldBe (prefix ++ suffix)
            destinationResponses.pull().futureValue shouldBe None
            sourceResponses.pull().futureValue shouldBe None
          } finally destinationResponses.cancel()
        } finally sourceResponses.cancel()
      } finally {
        allowRead.trySuccess(())
        tail.trySuccess(ByteString.empty)
        val terminated = Seq(origin, destination).map(_.flatMap(_.terminate(1.second))(system.dispatcher))
        try terminated.head.futureValue
        finally terminated.last.futureValue
      }
    }

    "stream a built-in deflate-coded request over HTTP/2" in {
      val http = Http()
      val first = ByteString("Summary for the first month\n" * 72)
      val last = ByteString("Summary for the second month\n" * 72)
      val tail = Promise[ByteString]()
      val firstData = Promise[Done]()
      val received = Promise[(HttpProtocol, Option[headers.`Content-Encoding`], ByteString)]()

      val binding = http.newServerAt("127.0.0.1", 0).bind { request =>
        request.entity.dataBytes
          .map { bytes =>
            firstData.trySuccess(Done)
            bytes
          }
          .runFold(ByteString.empty)(_ ++ _)
          .map { encoded =>
            received.success((request.protocol, request.header[headers.`Content-Encoding`], encoded))
            HttpResponse()
          }(system.dispatcher)
      }.futureValue

      try {
        val entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`,
          Source.single(first).concat(Source.future(tail.future)))
        val request = HttpRequest(HttpMethods.POST, "/summary", entity = entity)
        val responses = Source.single(Coders.Deflate.encodeMessage(request))
          .via(http.connectionTo("127.0.0.1").toPort(binding.localAddress.getPort).http2WithPriorKnowledge())
          .runWith(Sink.seq)

        // The compressor must deliver bytes before the application completes the request body.
        firstData.future.futureValue
        tail.success(last)

        val (protocol, encoding, encoded) = received.future.futureValue
        protocol shouldBe HttpProtocols.`HTTP/2.0`
        encoding shouldBe Some(headers.`Content-Encoding`(HttpEncodings.deflate))
        // Use an independent inflater to check the zlib wrapper as well as the full payload.
        val decoded = Using.resource(new InflaterInputStream(new ByteArrayInputStream(encoded.toArray))) { input =>
          ByteString(input.readAllBytes())
        }
        decoded shouldBe (first ++ last)
        responses.futureValue.map(response => response.protocol -> response.status) shouldBe
        Seq(HttpProtocols.`HTTP/2.0` -> StatusCodes.OK)
      } finally {
        tail.trySuccess(ByteString.empty)
        binding.terminate(1.second).futureValue
      }
    }

    "preserve the charset of a streamed HTTP/2 text response" in new TestSetup {
      try {
        val charset = HttpCharsets.`ISO-8859-1`
        val contentType = ContentType(MediaTypes.`text/plain`, charset)
        val body = Source(List(ByteString("caf"), ByteString("\u00e9", StandardCharsets.ISO_8859_1)))

        sendClientRequest(HttpRequest(uri = "/cafe"))
        val serverRequest = expectServerRequest()
        serverRequest.sendResponse(HttpResponse(entity = HttpEntity(contentType, body)))

        val response = expectClientResponse()
        response.protocol shouldBe HttpProtocols.`HTTP/2.0`
        response.status shouldBe StatusCodes.OK
        response.entity shouldBe a[HttpEntity.Chunked]
        response.entity.contentType shouldBe contentType
        val javaEntity: pekko.http.javadsl.model.HttpEntity = response.entity
        javaEntity.getContentType.getCharsetOption shouldBe java.util.Optional.of(charset)
        Unmarshal(response.entity).to[String].futureValue shouldBe "caf\u00e9"
      } finally {
        try clientRequestsOut.sendComplete()
        finally binding.terminate(1.second).futureValue(timeout(3.seconds))
      }
    }

    "support multiple interleaved concurrent streams" in new TestSetup {
      val reqPub1 = sendClientRequestWithEntityStream("request-1")

      val serverRequest1 = expectServerRequest()
      serverRequest1.request.attribute(Http2.streamId) shouldBe Symbol("nonEmpty")
      serverRequest1.request.method shouldBe HttpMethods.POST

      val reqSub1 = serverRequest1.expectRequestEntityStream()
      reqPub1.sendNext(ByteString("ping"))
      reqSub1.expectUtf8EncodedString("ping")

      val reqPub2 = sendClientRequestWithEntityStream("request-2")
      val serverRequest2 = expectServerRequest()
      serverRequest2.request.attribute(Http2.streamId) shouldBe Symbol("nonEmpty")
      serverRequest2.request.method shouldBe HttpMethods.POST
      val reqSub2 = serverRequest2.expectRequestEntityStream()
      reqPub2.sendNext(ByteString("blub"))
      reqSub2.expectUtf8EncodedString("blub")

      // send and receive response to second request first

      val resPub2 = serverRequest2.sendResponseWithEntityStream()

      val (response2, resSub2) = expectClientResponseWithStream()
      response2.attribute(requestIdAttr).get.id shouldBe "request-2"
      resPub2.sendNext(ByteString("blip blup"))
      resSub2.expectUtf8EncodedString("blip blup")

      serverRequest1.sendResponse(HttpResponse(entity = "pong"))
      val response1 = expectClientResponse()
      response1.attribute(requestIdAttr).get.id shouldBe "request-1"
      Unmarshal(response1.entity).to[String].futureValue shouldBe "pong"
    }
    "support server-side idle-timeout" in new TestSetup {
      override def serverSettings: ServerSettings = super.serverSettings.mapTimeouts(_.withIdleTimeout(100.millis))

      clientResponsesIn.ensureSubscription()
      Thread.sleep(500)
      clientRequestsOut.expectCancellation()
      // expect idle timeout connection abort exception to propagate to user
      clientResponsesIn.expectError() shouldBe a[StreamTcpException]
    }
    "support client-side idle-timeout" in new TestSetup {
      override def clientSettings: ClientConnectionSettings = super.clientSettings.withIdleTimeout(100.millis)

      clientResponsesIn.ensureSubscription()
      Thread.sleep(500)
      clientRequestsOut.expectCancellation()
      // expect idle timeout exception to propagate to user
      clientResponsesIn.expectError() shouldBe a[HttpIdleTimeoutException]
    }

    "return bad request response when header parsing fails" in new TestSetup {
      val badRequest = HttpRequest(
        // easiest valid way to cause parsing to fail - unknown method
        method = HttpMethod.custom("UNKNOWN_TO_SERVER"),
        uri = "http://www.example.com/test"
      )
      sendClientRequest(badRequest)
      val response = expectClientResponse()
      response.status should be(StatusCodes.BadRequest)
    }

    "keep the connection usable after a header parsing failure" in new TestSetup {
      sendClientRequest(HttpRequest(
        method = HttpMethod.custom("UNKNOWN_TO_SERVER"),
        uri = "http://www.example.com/test").addAttribute(requestIdAttr, RequestId("bad")))
      expectClientResponse().status should be(StatusCodes.BadRequest)

      // the failing header must not have left the HPACK dynamic table out of step with the client's, nor the
      // decoder's state machine part way through the previous block
      sendClientRequest(
        HttpRequest(uri = "http://www.example.com/afterwards").addAttribute(requestIdAttr, RequestId("good")))
      val serverRequest = expectServerRequest()
      serverRequest.request.uri.path.toString shouldBe "/afterwards"
      serverRequest.sendResponse(HttpResponse(entity = "pong"))
      expectClientResponse().status should be(StatusCodes.OK)
    }

    "return internal server error when handler future fails" in new TestSetup {
      sendClientRequest()
      val serverRequest = expectServerRequest()

      EventFilter[TE](message = "boom", occurrences = 1).intercept {
        serverRequest.promise.failure(TE("boom"))
        val response = expectClientResponse()
        response.status should be(StatusCodes.InternalServerError)
      }

      sendClientRequest()
      val nextServerRequest = expectServerRequest()
      nextServerRequest.sendResponse(HttpResponse())
      expectClientResponse().status should be(StatusCodes.OK)
    }

    "complete a concurrent request when another route fails" in new TestSetup {
      override def handler: HttpRequest => Future[HttpResponse] = {
        import Directives._
        val applicationHandler = super.handler
        Route.toFunction(extractRequest(request => complete(applicationHandler(request))))
      }

      def sendAndObserve(path: String): ServerRequest = {
        sendClientRequest(HttpRequest(uri = path).addAttribute(requestIdAttr, RequestId(path)))
        val observed = expectServerRequest()
        observed.request.uri.path.toString shouldBe path
        observed
      }

      try {
        val failing = sendAndObserve("/failing")
        val healthy = sendAndObserve("/healthy")

        // Both requests reached the application before either response was released.
        EventFilter[TE](occurrences = 1).intercept {
          failing.promise.failure(TE("application failure"))
          val response = expectClientResponse()
          response.attribute(requestIdAttr) shouldBe Some(RequestId("/failing"))
          response.status shouldBe StatusCodes.InternalServerError
          response.discardEntityBytes().future.futureValue
        }

        healthy.sendResponse(HttpResponse(entity = "healthy response"))
        val response = expectClientResponse()
        response.attribute(requestIdAttr) shouldBe Some(RequestId("/healthy"))
        response.status shouldBe StatusCodes.OK
        Unmarshal(response.entity).to[String].futureValue shouldBe "healthy response"
      } finally {
        try clientRequestsOut.sendComplete()
        finally binding.terminate(1.second).futureValue(timeout(3.seconds))
      }
    }

    "return internal server error when handler throws synchronously" in new TestSetup {
      override def handler: HttpRequest => Future[HttpResponse] = _ => throw TE("boom-sync")

      EventFilter[TE](message = "boom-sync", occurrences = 1).intercept {
        sendClientRequest()
        expectClientResponse().status should be(StatusCodes.InternalServerError)
      }
    }
  }

  case class ServerRequest(request: HttpRequest, promise: Promise[HttpResponse]) {
    def sendResponse(response: HttpResponse): Unit =
      promise.success(response.addAttribute(Http2.streamId, request.attribute(Http2.streamId).get))

    def sendResponseWithEntityStream(
        status: StatusCode = StatusCodes.OK,
        headers: Seq[HttpHeader] = Nil): TestPublisher.Probe[ByteString] = {
      val probe = TestPublisher.probe[ByteString]()
      sendResponse(HttpResponse(status, headers,
        HttpEntity(ContentTypes.`application/octet-stream`, Source.fromPublisher(probe))))
      probe
    }

    def expectRequestEntityStream(): ByteStringSinkProbe = {
      val probe = ByteStringSinkProbe()
      request.entity.dataBytes.runWith(probe.sink)
      probe
    }
  }
  class TestSetup {
    def serverSettings: ServerSettings = ServerSettings(system)
    def clientSettings: ClientConnectionSettings = ClientConnectionSettings(system)
    private lazy val serverRequestProbe = TestProbe()
    def handler: HttpRequest => Future[HttpResponse] = { req =>
      val p = Promise[HttpResponse]()
      serverRequestProbe.ref ! ServerRequest(req, p)
      p.future
    }
    lazy val binding =
      Http().newServerAt("localhost", 0)
        .enableHttps(ExampleHttpContexts.exampleServerContext)
        .withSettings(serverSettings)
        .bind(handler).futureValue
    lazy val clientFlow =
      Http().connectionTo("pekko.example.org")
        .withCustomHttpsConnectionContext(ExampleHttpContexts.exampleClientContext)
        .withClientConnectionSettings(
          clientSettings.withTransport(ExampleHttpContexts.proxyTransport(binding.localAddress)))
        .http2()
    lazy val clientRequestsOut = TestPublisher.probe[HttpRequest]()
    lazy val clientResponsesIn = TestSubscriber.probe[HttpResponse]()
    Source.fromPublisher(clientRequestsOut)
      .via(clientFlow)
      .runWith(Sink.fromSubscriber(clientResponsesIn))

    // client-side
    def sendClientRequestWithEntityStream(
        requestId: String,
        method: HttpMethod = HttpMethods.POST,
        uri: Uri = Uri./,
        headers: Seq[HttpHeader] = Nil): TestPublisher.Probe[ByteString] = {
      val probe = TestPublisher.probe[ByteString]()
      sendClientRequest(
        HttpRequest(method, uri, headers,
          HttpEntity(ContentTypes.`application/octet-stream`, Source.fromPublisher(probe)))
          .addAttribute(requestIdAttr, RequestId(requestId)))
      probe
    }
    def sendClientRequest(request: HttpRequest = HttpRequest()): Unit = clientRequestsOut.sendNext(request)
    def expectClientResponse(): HttpResponse = clientResponsesIn.requestNext()
    def expectClientResponseWithStream(): (HttpResponse, ByteStringSinkProbe) = {
      val res = expectClientResponse()
      val probe = ByteStringSinkProbe()
      res.entity.dataBytes.runWith(probe.sink)
      res -> probe
    }

    // server-side
    def expectServerRequest(): ServerRequest = serverRequestProbe.expectMsgType[ServerRequest]
  }
}
