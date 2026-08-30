/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.http.impl.engine.http2

import org.apache.pekko
import pekko.http.impl.util.{ ExampleHttpContexts, PekkoSpecWithMaterializer }
import pekko.http.scaladsl.Http
import pekko.http.scaladsl.model.{
  AttributeKey,
  HttpProtocols,
  HttpRequest,
  HttpResponse,
  RequestResponseAssociation,
  StatusCodes
}
import pekko.http.scaladsl.settings.{ ClientConnectionSettings, ServerSettings }
import pekko.http.scaladsl.unmarshalling.Unmarshal
import pekko.stream.scaladsl.{ Sink, Source }
import pekko.stream.testkit.{ TestPublisher, TestSubscriber }
import pekko.testkit.TestProbe

import scala.concurrent.Future
import org.scalatest.concurrent.ScalaFutures

/**
 * Covers `OutgoingConnectionBuilder.http2WithFallback`, which offers both `h2` and `http/1.1` over ALPN and picks
 * the client stack from what the server selected.
 */
class Http2ClientFallbackSpec extends PekkoSpecWithMaterializer("""
    pekko.http.server.log-unencrypted-network-bytes = 100
    pekko.http.client.log-unencrypted-network-bytes = 100
    pekko.actor.serialize-messages = false
  """) with ScalaFutures {

  case class RequestId(id: String) extends RequestResponseAssociation
  val requestIdAttr = AttributeKey[RequestId]("requestId")

  "The HTTP/2 client with ALPN fallback" should {

    "negotiate HTTP/2 against a server that supports it" in new TestSetup {
      val response = roundTrip()
      response.status shouldBe StatusCodes.OK
      Unmarshal(response.entity).to[String].futureValue shouldBe "pong"

      // the stream id attribute is only ever set by the HTTP/2 server stack
      serverSeenRequest.attribute(Http2.streamId) shouldBe Symbol("nonEmpty")
      serverSeenRequest.protocol shouldBe HttpProtocols.`HTTP/2.0`
    }

    "fall back to HTTP/1.1 against a server that does not support HTTP/2" in new TestSetup {
      override def serverSettings: ServerSettings = super.serverSettings.withEnableHttp2(false)

      val response = roundTrip()
      response.status shouldBe StatusCodes.OK
      Unmarshal(response.entity).to[String].futureValue shouldBe "pong"

      serverSeenRequest.attribute(Http2.streamId) shouldBe Symbol("empty")
      serverSeenRequest.protocol shouldBe HttpProtocols.`HTTP/1.1`
    }
  }

  class TestSetup {
    def serverSettings: ServerSettings = ServerSettings(system)
    def clientSettings: ClientConnectionSettings = ClientConnectionSettings(system)

    private val serverRequestProbe = TestProbe()

    lazy val binding =
      Http().newServerAt("localhost", 0)
        .enableHttps(ExampleHttpContexts.exampleServerContext)
        .withSettings(serverSettings)
        .bind { request =>
          serverRequestProbe.ref ! request
          Future.successful(HttpResponse(entity = "pong"))
        }.futureValue

    lazy val clientFlow =
      Http().connectionTo("pekko.example.org")
        .withCustomHttpsConnectionContext(ExampleHttpContexts.exampleClientContext)
        .withClientConnectionSettings(
          clientSettings.withTransport(ExampleHttpContexts.proxyTransport(binding.localAddress)))
        .http2WithFallback()

    lazy val requestsOut = TestPublisher.probe[HttpRequest]()
    lazy val responsesIn = TestSubscriber.probe[HttpResponse]()
    Source.fromPublisher(requestsOut)
      .via(clientFlow)
      .runWith(Sink.fromSubscriber(responsesIn))

    def roundTrip(): HttpResponse = {
      requestsOut.sendNext(HttpRequest().addAttribute(requestIdAttr, RequestId("request-1")))
      responsesIn.requestNext()
    }

    lazy val serverSeenRequest: HttpRequest = serverRequestProbe.expectMsgType[HttpRequest]
  }
}
