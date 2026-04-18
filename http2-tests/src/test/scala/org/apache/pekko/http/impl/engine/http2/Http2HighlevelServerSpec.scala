/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.impl.engine.http2

import org.apache.pekko
import pekko.http.impl.engine.ws.ByteStringSinkProbe
import pekko.http.impl.util.{ ExampleHttpContexts, PekkoSpecWithMaterializer }
import pekko.http.scaladsl.Http
import pekko.http.scaladsl.model.AttributeKey
import pekko.http.scaladsl.model.ContentTypes
import pekko.http.scaladsl.model.HttpEntity
import pekko.http.scaladsl.model.HttpHeader
import pekko.http.scaladsl.model.HttpMethod
import pekko.http.scaladsl.model.HttpMethods
import pekko.http.scaladsl.model.HttpRequest
import pekko.http.scaladsl.model.HttpResponse
import pekko.http.scaladsl.model.RequestResponseAssociation
import pekko.http.scaladsl.model.StatusCode
import pekko.http.scaladsl.model.StatusCodes
import pekko.http.scaladsl.model.Uri
import pekko.http.scaladsl.settings.ClientConnectionSettings
import pekko.http.scaladsl.settings.ServerSettings
import pekko.stream.scaladsl.Sink
import pekko.stream.scaladsl.Source
import pekko.stream.testkit.TestPublisher
import pekko.stream.testkit.TestSubscriber
import pekko.stream.testkit.Utils.TE
import pekko.testkit.TestProbe
import pekko.util.ByteString
import org.scalatest.concurrent.ScalaFutures

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.Promise

class Http2HighlevelServerSpec extends PekkoSpecWithMaterializer(
      """pekko.http.server.remote-address-header = on
         pekko.http.server.http2.log-frames = on
         pekko.http.server.log-unencrypted-network-bytes = 100
         pekko.http.server.enable-http2 = on
         pekko.http.client.http2.log-frames = on
         pekko.http.client.log-unencrypted-network-bytes = 100
         pekko.actor.serialize-messages = false
      """) with ScalaFutures {

  case class RequestId(id: String) extends RequestResponseAssociation
  val requestIdAttr = AttributeKey[RequestId]("requestId")

  "A HTTP 2 server" should {

    "return internal error when handler throws" in new TestSetup {
      sendClientRequest()
      val serverRequest = expectServerRequest()
      serverRequest.promise.failure(TE("boom"))
      val response = expectClientResponse()
      response.status should be(StatusCodes.InternalServerError)

      // stream not torn down so we can send another one
      sendClientRequest()
      val serverRequest2 = expectServerRequest()
      serverRequest2.promise.success(HttpResponse())
      val response2 = expectClientResponse()
      response2.status should be(StatusCodes.OK)

    }
  }

  case class ServerRequest(request: HttpRequest, promise: Promise[HttpResponse]) {
    def sendResponse(response: HttpResponse): Unit =
      promise.success(response.addAttribute(Http2.streamId, request.attribute(Http2.streamId).get))

    def sendResponseWithEntityStream(
        status: StatusCode = StatusCodes.OK,
        headers: immutable.Seq[HttpHeader] = Nil): TestPublisher.Probe[ByteString] = {
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
    private lazy val handler: HttpRequest => Future[HttpResponse] = { req =>
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
        headers: immutable.Seq[HttpHeader] = Nil): TestPublisher.Probe[ByteString] = {
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
