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

import java.net.InetSocketAddress

import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko
import pekko.http.impl.engine.http2.FrameEvent.{ DataFrame, ParsedHeadersFrame, SettingsAckFrame, SettingsFrame }
import pekko.http.impl.engine.http2.client.PersistentConnection
import pekko.http.impl.engine.parsing.HttpHeaderParser
import pekko.http.impl.util.PekkoSpecWithMaterializer
import pekko.http.scaladsl.Http.OutgoingConnection
import pekko.http.scaladsl.model.{ HttpEntity, HttpRequest, HttpResponse, StatusCodes }
import pekko.http.scaladsl.model.headers.RawHeader
import pekko.http.scaladsl.settings.ClientConnectionSettings
import pekko.stream.scaladsl.{ Flow, Keep }
import pekko.stream.testkit.{ TestPublisher, TestSubscriber }
import pekko.stream.testkit.scaladsl.{ TestSink, TestSource }
import pekko.stream.testkit.Utils.assertAllStagesStopped
import pekko.testkit.{ ExplicitlyTriggeredScheduler, TestProbe }
import pekko.util.ByteString

class Http2ClientReadCompletionSpec extends PekkoSpecWithMaterializer(
      """
        pekko.scheduler.implementation = "org.apache.pekko.testkit.ExplicitlyTriggeredScheduler"
      """) {
  private val drainTimeout = 1.second

  "An HTTP/2 client at clean transport read completion" should {
    "deliver the completed response body and trailers after the transport has closed" in assertAllStagesStopped {
      val client = new Setup
      val response = client.receiveCompletedResponse()
      val followingId = client.sendRequest("/following")
      client.peer.sendNext(headers(followingId, endStream = true, ":status" -> "204"))
      val following = client.responses.expectNext()
      following.status shouldBe StatusCodes.NoContent
      following.entity.isKnownEmpty shouldBe true
      client.finishReading()

      expectBodyAndTrailer(response)
    }

    "deliver an accepted response before failing a direct connection with an unanswered request" in
    assertAllStagesStopped {
      val client = directClientWithUnansweredRequest()
      client.responses.request(1)
      client.responses.expectError() shouldBe theSameInstanceAs(Http2StreamHandling.ConnectionWasAbortedException)
    }

    "fail a direct connection by the drain deadline when there is no further response demand" in
    assertAllStagesStopped {
      val client = directClientWithUnansweredRequest()
      advanceToDrainDeadline()
      client.responses.expectError() shouldBe theSameInstanceAs(Http2StreamHandling.ConnectionWasAbortedException)
    }

    "preserve an already delivered entity when the response stream is cancelled after read completion" in
    assertAllStagesStopped {
      val client = new Setup(initialResponseDemand = 1)
      val response = client.receiveCompletedResponse()
      val acceptedId = client.sendRequest("/accepted")
      client.peer.sendNext(headers(acceptedId, endStream = true, ":status" -> "204"))
      client.finishTransport()
      client.responses.cancel()

      expectBodyAndTrailer(response)
    }

    "give a managed client the accepted responses before reconnecting for a subsequent request" in
    assertAllStagesStopped {
      val connections = TestProbe()
      val addresses = new InetSocketAddress("127.0.0.1", 0)
      val connection = testConnection.mapMaterializedValue {
        case (wire, peer) =>
          connections.ref ! new Transport(wire, peer)
          Future.successful(OutgoingConnection(addresses, addresses))
      }
      val (requests, responses) = TestSource[HttpRequest]()
        .via(PersistentConnection.managedConnection(connection, settings.http2Settings))
        .toMat(TestSink[HttpResponse]())(Keep.both)
        .run()

      // Leave demand for one response; the second accepted response must survive request-input cancellation.
      responses.request(1)
      requests.sendNext(request("/complete"))
      val first = connections.expectMsgType[Transport]
      val firstId = first.startForRequest("/complete")
      requests.sendNext(request("/accepted"))
      val acceptedId = first.expectRequest("/accepted")
      first.sendCompletedBody(firstId)
      val response = responses.expectNext()
      response.status shouldBe StatusCodes.OK
      first.peer.sendNext(headers(acceptedId, endStream = true, ":status" -> "204"))
      first.peer.sendComplete()
      first.wire.expectComplete()

      responses.request(1)
      responses.expectNext().status shouldBe StatusCodes.NoContent
      responses.request(1)
      requests.sendNext(request("/after-eof"))
      val second = connections.expectMsgType[Transport]
      val nextId = second.startForRequest("/after-eof")
      second.peer.sendNext(headers(nextId, endStream = true, ":status" -> "204"))
      responses.expectNext().status shouldBe StatusCodes.NoContent
      requests.sendComplete()
      responses.expectComplete()
      second.wire.expectComplete()

      expectBodyAndTrailer(response)
    }

    "release a completed response that the application does not consume by the drain deadline" in {
      val response = assertAllStagesStopped {
        val client = new Setup
        val response = client.receiveCompletedResponse()
        client.finishReading()
        advanceToDrainDeadline()
        response
      }
      assertAllStagesStopped {
        val bytes = response.entity.dataBytes.runWith(TestSink[ByteString]())
        bytes.request(1)
        bytes.expectError() shouldBe theSameInstanceAs(Http2StreamHandling.ConnectionWasAbortedException)
      }
    }
  }

  private def directClientWithUnansweredRequest(): Setup = {
    val client = new Setup(initialResponseDemand = 0)
    val acceptedId = client.sendRequest("/accepted")
    client.sendRequest("/unanswered")
    client.peer.sendNext(headers(acceptedId, endStream = true, ":status" -> "204"))
    client.finishTransport()

    // The first response must still be available even though the transport has already closed.
    client.responses.request(1)
    val accepted = client.responses.expectNext()
    accepted.status shouldBe StatusCodes.NoContent
    accepted.entity.isKnownEmpty shouldBe true
    client
  }

  private def advanceToDrainDeadline(): Unit =
    system.scheduler.asInstanceOf[ExplicitlyTriggeredScheduler].timePasses(drainTimeout)

  private def headers(streamId: Int, endStream: Boolean, fields: (String, String)*): ParsedHeadersFrame =
    ParsedHeadersFrame(streamId, endStream, fields, None, None)

  private val settings = {
    val defaults = ClientConnectionSettings(system)
    // Keep the standard initial window in this fixture so unrelated flow-credit frames do not enter the probe.
    defaults.withHttp2Settings(defaults.http2Settings
      .withCompletionTimeout(drainTimeout)
      .withIncomingConnectionLevelBufferSize(Http2Protocol.InitialWindowSize))
  }

  private def request(path: String): HttpRequest = HttpRequest(uri = s"https://example.invalid$path")

  private def testConnection = {
    val parser = HttpHeaderParser(settings.parserSettings, system.log)
    val transport = Flow.fromSinkAndSourceMat(TestSink[FrameEvent](), TestSource[FrameEvent]())(Keep.both)
    Http2Blueprint
      .httpLayerClient(parser, settings, system.log)
      .atop(Http2Blueprint.clientDemux(settings.http2Settings, parser))
      .joinMat(transport)(Keep.right)
  }

  private def expectBodyAndTrailer(response: HttpResponse): Unit = {
    val entity = response.entity.asInstanceOf[HttpEntity.Chunked]
    val chunks = entity.chunks.runWith(TestSink[HttpEntity.ChunkStreamPart]())
    chunks.request(2)
    chunks.expectNext(HttpEntity.Chunk(ByteString("body")))
    chunks.expectNext(HttpEntity.LastChunk(trailer = List(RawHeader("x-check", "complete"))))
    chunks.expectComplete()
  }

  private class Transport(val wire: TestSubscriber.Probe[FrameEvent], val peer: TestPublisher.Probe[FrameEvent]) {
    private def sendInitialSettings(): Unit = {
      wire.request(16)
      wire.expectNext() shouldBe a[SettingsFrame]
      peer.sendNext(SettingsFrame(Nil))
    }

    def start(): Unit = {
      sendInitialSettings()
      wire.expectNext() shouldBe SettingsAckFrame(Nil)
    }

    def startForRequest(path: String): Int = {
      sendInitialSettings()
      // A managed connection is materialized by this request; the settings acknowledgement may follow it.
      val first = wire.expectNext()
      val second = wire.expectNext()
      if (first == SettingsAckFrame(Nil)) expectRequest(path, second)
      else {
        second shouldBe SettingsAckFrame(Nil)
        expectRequest(path, first)
      }
    }

    def expectRequest(path: String): Int = expectRequest(path, wire.expectNext())

    private def expectRequest(path: String, frame: FrameEvent): Int = {
      val request = frame.asInstanceOf[ParsedHeadersFrame]
      request.endStream shouldBe true
      request.keyValuePairs should contain(":path" -> path)
      request.streamId
    }

    def sendCompletedBody(streamId: Int): Unit = {
      peer.sendNext(headers(streamId, endStream = false, ":status" -> "200"))
      peer.sendNext(DataFrame(streamId, endStream = false, ByteString("body")))
      peer.sendNext(headers(streamId, endStream = true, "x-check" -> "complete"))
    }
  }

  private class Setup(initialResponseDemand: Int = 16) {
    val ((requests, (wire, peer)), responses) = TestSource[HttpRequest]()
      .viaMat(testConnection)(Keep.both)
      .toMat(TestSink[HttpResponse]())(Keep.both)
      .run()
    private val transport = new Transport(wire, peer)

    if (initialResponseDemand > 0) responses.request(initialResponseDemand)
    transport.start()

    def sendRequest(path: String): Int = {
      requests.sendNext(request(path))
      transport.expectRequest(path)
    }

    def receiveCompletedResponse(): HttpResponse = {
      val streamId = sendRequest("/complete")
      transport.sendCompletedBody(streamId)
      val response = responses.expectNext()
      response.status shouldBe StatusCodes.OK
      response
    }

    def finishTransport(): Unit = {
      peer.sendComplete()
      requests.expectCancellation()
      wire.expectComplete()
    }

    def finishReading(): Unit = {
      finishTransport()
      responses.expectComplete()
    }
  }
}
