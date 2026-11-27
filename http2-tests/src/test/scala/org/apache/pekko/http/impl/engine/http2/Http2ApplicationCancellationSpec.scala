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

import scala.concurrent.Promise
import scala.concurrent.duration._

import org.apache.pekko
import pekko.Done
import pekko.http.impl.engine.ws.ByteStringSinkProbe
import pekko.http.impl.util.{ ExampleHttpContexts, PekkoSpecWithMaterializer }
import pekko.http.scaladsl.Http
import pekko.http.scaladsl.model.{ ContentTypes, HttpEntity, HttpProtocols, HttpRequest, HttpResponse, StatusCodes }
import pekko.http.scaladsl.settings.ClientConnectionSettings
import pekko.http.scaladsl.unmarshalling.Unmarshal
import pekko.stream.scaladsl.{ Sink, Source }
import pekko.stream.testkit.{ TestPublisher, TestSubscriber }
import pekko.stream.testkit.Utils.assertAllStagesStopped
import pekko.testkit.TestProbe
import pekko.util.ByteString

import org.scalatest.concurrent.ScalaFutures

class Http2ApplicationCancellationSpec extends PekkoSpecWithMaterializer with ScalaFutures {
  override protected def failOnSevereMessages: Boolean = true

  "A direct HTTP/2 connection" should {
    "remain usable after the client cancels a streamed response entity" in assertAllStagesStopped {
      val before = ByteString("summary so far")
      val inFlight = ByteString("summary continued")
      val emitted = Promise[Done]()
      val producer = TestPublisher.probe[ByteString]()
      val streamed = HttpEntity(ContentTypes.`application/octet-stream`,
        Source.fromPublisher(producer).map { data =>
          if (data == inFlight) emitted.success(Done)
          data
        })
      val incoming = TestProbe()
      val binding = Http().newServerAt("127.0.0.1", 0)
        .enableHttps(ExampleHttpContexts.exampleServerContext)
        .bindSync { request =>
          incoming.ref ! request
          if (request.uri.path.toString == "/streamed-summary") HttpResponse(entity = streamed)
          else HttpResponse(entity = "next summary")
        }.futureValue
      val requests = TestPublisher.probe[HttpRequest]()
      val responses = TestSubscriber.probe[HttpResponse]()

      try {
        val connection = Http().connectionTo("pekko.example.org")
          .withCustomHttpsConnectionContext(ExampleHttpContexts.exampleClientContext)
          .withClientConnectionSettings(
            ClientConnectionSettings(system).withTransport(ExampleHttpContexts.proxyTransport(binding.localAddress)))
          .http2()
        Source.fromPublisher(requests).via(connection).runWith(Sink.fromSubscriber(responses))

        requests.sendNext(HttpRequest(uri = "/streamed-summary"))
        val first = incoming.expectMsgType[HttpRequest]
        first.protocol shouldBe HttpProtocols.`HTTP/2.0`
        first.attribute(Http2.streamId) shouldBe Some(1)
        val response = responses.requestNext()
        response.protocol shouldBe HttpProtocols.`HTTP/2.0`
        response.status shouldBe StatusCodes.OK
        val bytes = ByteStringSinkProbe(response.entity.dataBytes)
        try {
          producer.sendNext(before)
          bytes.expectBytes(before)
          // Let a second ordinary chunk enter the response pipeline while the entity remains open.
          producer.sendNext(inFlight)
          emitted.future.futureValue
        } finally bytes.cancel()
        producer.expectCancellation()

        requests.sendNext(HttpRequest(uri = "/next-summary"))
        val second = incoming.expectMsgType[HttpRequest]
        second.uri.path.toString shouldBe "/next-summary"
        second.attribute(Http2.streamId) shouldBe Some(3)
        val next = responses.requestNext()
        next.protocol shouldBe HttpProtocols.`HTTP/2.0`
        next.status shouldBe StatusCodes.OK
        Unmarshal(next.entity).to[String].futureValue shouldBe "next summary"

        requests.sendComplete()
        responses.expectComplete()
      } finally {
        try responses.cancel()
        finally binding.terminate(1.second).futureValue
      }
    }
  }
}
