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

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future, Promise }

import org.apache.pekko
import pekko.Done
import pekko.http.impl.engine.http2.FrameEvent.{ ParsedHeadersFrame, SettingsAckFrame, SettingsFrame }
import pekko.http.impl.engine.parsing.HttpHeaderParser
import pekko.http.impl.engine.rendering.DateHeaderRendering
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.model.headers.RawHeader
import pekko.http.scaladsl.settings.ServerSettings
import pekko.stream.scaladsl.{ Flow, Keep }
import pekko.stream.testkit.scaladsl.{ TestSink, TestSource }
import pekko.util.ByteString

class Http2ServerEarlyHintsSpec extends Http2SpecWithMaterializer("""
  pekko.http.server.http2.early-hints-enabled = on
  """) {

  "An HTTP/2 server with application Early Hints enabled" should {
    "send ordered 103 Early Hints from a typed handler before its final response".inAssertAllStagesStopped(
      new TestSetup with HandlerFunctionSupport {
        private lazy val nextHint = Promise[Unit]()
        private lazy val finalResponse = Promise[HttpResponse]()
        private val style = "</main.css>; rel=preload; as=style"
        private val script = "</main.js>; rel=preload; as=script"
        private val font = "</main.woff2>; rel=preload; as=font"

        override def handler: HttpRequest => Future[HttpResponse] = request => {
          val sender = EarlyHintsSender.from(request).get
          sender.send(List(RawHeader("Link", style), RawHeader("Link", script)))
            .flatMap(_ => nextHint.future)
            .flatMap(_ => sender.send(List(RawHeader("Link", font))))
            .flatMap(_ => finalResponse.future)
        }

        def expectHint(expected: (String, String)*): Unit =
          network.decodeHeaders(network.expectHeaderBlock(1, endStream = false)) shouldBe
          ((":status" -> "103") +: expected)

        try {
          network.sendRequest(1, HttpRequest(uri = "https://www.example.com/index"))
          expectHint("link" -> style, "link" -> script)
          nextHint.success(())
          expectHint("link" -> font)
          finalResponse.success(HttpResponse(headers = List(RawHeader("Link", style)), entity = "final body"))
          val finalFields = network.expectDecodedResponseHEADERSPairs(1, endStream = false)
          finalFields.head shouldBe (":status" -> "200")
          finalFields.filter(_._1 == "link") shouldBe Seq("link" -> style)
          finalFields should contain("content-length" -> "10")
          network.expectDATA(1, endStream = true, ByteString("final body"))
        } finally {
          nextHint.trySuccess(())
          finalResponse.trySuccess(HttpResponse())
        }
      })

    "keep automatic 100 Continue separate from enabled Early Hints".inAssertAllStagesStopped(
      new TestSetup with RequestResponseProbes {
        override def settings: ServerSettings = super.settings.mapHttp2Settings(
          _.withMinCollectStrictEntitySize(16).withIncomingConnectionLevelBufferSize(Http2Protocol.InitialWindowSize))

        val requestHeaders = Seq(
          ":method" -> "POST", ":scheme" -> "https", ":authority" -> "www.example.com", ":path" -> "/upload",
          "content-length" -> "5", "expect" -> "100-continue")
        network.sendHEADERS(1, endStream = false, endHeaders = true, network.encodeHeaderPairs(requestHeaders))
        network.expectDecodedResponseHEADERSPairs(1, endStream = false) shouldBe Seq(":status" -> "100")
        network.sendDATA(1, endStream = true, ByteString("hello"))
        val request = user.expectRequest()
        Await.result(request.entity.toStrict(3.seconds), 3.seconds).data shouldBe ByteString("hello")
        val link = "</ready.css>; rel=preload; as=style"
        val send = EarlyHintsSender.from(request).get.send(List(RawHeader("Link", link)))
        network.expectDecodedResponseHEADERSPairs(1, endStream = false) shouldBe Seq(":status" -> "103", "link" -> link)
        Await.result(send, 3.seconds)
        user.emitResponse(1, HttpResponse())
        network.expectDecodedResponseHEADERSPairs(1, endStream = true).head shouldBe (":status" -> "200")
      })

    "hold an Early Hints offer until frame demand and discard it if the final wins" in {
      val settings = ServerSettings(system).mapHttp2Settings(_.withOutgoingControlFrameBufferSize(1))
      val headerParser = HttpHeaderParser(settings.parserSettings, system.log)
      val server = Http2Blueprint.httpLayer(settings, system.log, DateHeaderRendering()).atop(
        Http2Blueprint.serverDemux(settings.http2Settings, Nil, upgraded = false, Some(settings -> headerParser)))
      val ((responses, (outgoing, incoming)), requests) = TestSource[HttpResponse]()
        .viaMat(server.joinMat(Flow.fromSinkAndSourceMat(TestSink[FrameEvent](), TestSource[FrameEvent]())(
          Keep.both))(Keep.right))(Keep.both)
        .toMat(TestSink[HttpRequest]())(Keep.both)
        .run()

      val link = RawHeader("Link", "</style.css>; rel=preload; as=style")
      val expectedPairs = Seq(":status" -> "103", "link" -> link.value)
      try {
        // Consume exactly the handshake frames and do not pull the direct frame outlet again.
        outgoing.request(2)
        outgoing.expectNext() shouldBe a[SettingsFrame]
        incoming.sendNext(SettingsFrame(Nil))
        outgoing.expectNext() shouldBe SettingsAckFrame(Nil)

        def open(id: Int): EarlyHintsSender = {
          val uri = Uri(s"https://example.test/page-$id")
          incoming.sendNext(ParsedHeadersFrame(id, endStream = true,
            Seq(":method" -> HttpMethods.GET, ":scheme" -> uri.scheme, ":authority" -> uri.authority,
              ":path" -> (uri.path, uri.rawQueryString)), None, None))
          EarlyHintsSender.from(requests.requestNext()).get
        }

        def emitFinal(id: Int): Unit =
          responses.sendNext(HttpResponse().addAttribute(Http2.streamId, id))

        def expectFinal(id: Int): Unit = {
          val frame = outgoing.requestNext().asInstanceOf[ParsedHeadersFrame]
          frame.streamId shouldBe id
          frame.endStream shouldBe true
          frame.keyValuePairs.head shouldBe (":status" -> "200")
        }

        val first = open(1)
        val sibling = open(3)
        val pending = first.send(List(link))
        Await.result(first.send(List(link)).failed, 3.seconds).getMessage should include("already pending")
        // Both offers enter the same callback. The second stream reaching the queue limit proves the first
        // is already buffered, so the first future's state is not an assertion about thread timing.
        Await.result(sibling.send(List(link)).failed, 3.seconds).getMessage should include("cannot buffer")
        pending.isCompleted shouldBe false

        outgoing.requestNext() shouldBe ParsedHeadersFrame(1, endStream = false, expectedPairs, None, None)
        Await.result(pending, 3.seconds) shouldBe Done

        val racing = first.send(List(link))
        Await.result(sibling.send(List(link)).failed, 3.seconds).getMessage should include("cannot buffer")
        emitFinal(1)
        Await.result(racing.failed, 3.seconds).getMessage should include("closed")
        expectFinal(1) // No unsent 103 may be delivered after the final response is committed.
        Await.result(first.send(List(link)).failed, 3.seconds).getMessage should include("closed")

        val independent = sibling.send(List(link))
        outgoing.requestNext() shouldBe ParsedHeadersFrame(3, endStream = false, expectedPairs, None, None)
        Await.result(independent, 3.seconds) shouldBe Done
        emitFinal(3)
        expectFinal(3)
      } finally {
        requests.cancel()
        outgoing.cancel()
      }
    }
  }
}
