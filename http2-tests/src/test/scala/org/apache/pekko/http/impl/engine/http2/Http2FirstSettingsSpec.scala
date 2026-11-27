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
import pekko.NotUsed
import pekko.http.impl.engine.http2.FrameEvent._
import pekko.http.impl.engine.http2.Http2Protocol.{ ErrorCode, SettingIdentifier }
import pekko.http.impl.engine.http2.framing.{ FrameRenderer, Http2FrameParsing }
import pekko.http.impl.util.PekkoSpecWithMaterializer
import pekko.http.scaladsl.Http
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.settings.{ ClientConnectionSettings, ServerSettings }
import pekko.stream.scaladsl.{ Flow, Sink, Source }
import pekko.stream.testkit.scaladsl.StreamTestKit
import pekko.stream.testkit.{ TestPublisher, TestSubscriber }
import pekko.util.ByteString

class Http2FirstSettingsSpec extends PekkoSpecWithMaterializer("") {
  "Server completes when the peer closes before sending any HTTP/2 bytes" in {
    StreamTestKit.assertAllStagesStopped {
      val requests = TestSubscriber.probe[HttpRequest]()
      val responses = TestPublisher.probe[HttpResponse]()
      val connection = Http2Blueprint.serverStack(ServerSettings(system), system.log, telemetry = NoOpTelemetry,
        dateHeaderRendering = Http().dateHeaderRendering).reversed
        .join(Flow.fromSinkAndSource(Sink.fromSubscriber(requests), Source.fromPublisher(responses)))

      val input = TestPublisher.probe[ByteString]()
      val output = TestSubscriber.probe[FrameEvent]()
      Source.fromPublisher(input).via(connection)
        .via(new Http2FrameParsing(false, system.log))
        .runWith(Sink.fromSubscriber(output))

      requests.request(1)
      // The server can send its preface without waiting for the client's preface.
      output.requestNext() shouldBe a[SettingsFrame]
      input.sendComplete()

      output.request(1).expectComplete()
      requests.expectComplete()
      responses.expectCancellation()
    }
  }

  for {
    server <- Seq(true, false)
    scenario <- Seq("empty", "nonempty", "fragmented", "ACK", "early request")
    if !server || scenario != "early request"
  } {
    s"${if (server) "Server" else "Client"} first peer SETTINGS $scenario" in {
      val firstIsAck = scenario == "ACK"
      val configuredMaxConcurrentStreams = Option.when(scenario == "nonempty")(if (server) 128 else 192)
      StreamTestKit.assertAllStagesStopped {
        var finishApp: () => Unit = () => ()
        val connection: Flow[ByteString, ByteString, Any] = if (server) {
          val responses = Source.maybe[HttpResponse].mapMaterializedValue { p =>
            finishApp = () => { p.trySuccess(None); () }
            NotUsed
          }
          val defaults = ServerSettings(system)
          val settings = configuredMaxConcurrentStreams.fold(defaults) { limit =>
            defaults.mapHttp2Settings(_.withMaxConcurrentStreams(limit))
          }
          Http2Blueprint.serverStack(settings, system.log, telemetry = NoOpTelemetry,
            dateHeaderRendering = Http().dateHeaderRendering).reversed
            .join(Flow.fromSinkAndSource(Sink.ignore, responses))
        } else {
          val requests = Source.maybe[HttpRequest].mapMaterializedValue { p =>
            finishApp = () => { p.trySuccess(None); () }
            NotUsed
          }
          val appRequests = if (scenario == "early request")
            Source.single(HttpRequest(uri = "https://example.test/")).concat(requests)
          else requests
          val defaults = ClientConnectionSettings(system)
          val settings = configuredMaxConcurrentStreams.fold(defaults) { limit =>
            defaults.mapHttp2Settings(_.withMaxConcurrentStreams(limit))
          }
          Http2Blueprint.clientStack(settings, system.log, NoOpTelemetry).reversed
            .join(Flow.fromSinkAndSource(Sink.ignore, appRequests))
        }
        val input = TestPublisher.probe[ByteString]()
        val output = TestSubscriber.probe[FrameEvent]()
        Source.fromPublisher(input).via(connection)
          .via(new Http2FrameParsing(!server, system.log))
          .runWith(Sink.fromSubscriber(output))
        try {
          output.requestNext() match {
            case SettingsFrame(settings) =>
              configuredMaxConcurrentStreams.foreach { limit =>
                val advertised = settings.collect {
                  case Setting(SettingIdentifier.SETTINGS_MAX_CONCURRENT_STREAMS, value) => value
                }
                advertised shouldBe Seq(limit)
              }
            case other => fail(s"Expected initial SETTINGS, got $other")
          }
          if (scenario == "early request") output.requestNext() shouldBe a[HeadersFrame]
          val pingData = ByteString("12345678")
          val settings = if (scenario == "nonempty" || scenario == "fragmented")
            Seq(Setting(SettingIdentifier.SETTINGS_MAX_CONCURRENT_STREAMS, 2))
          else Nil
          val first: FrameEvent = if (firstIsAck) SettingsAckFrame(Nil) else SettingsFrame(settings)
          val prefix = if (server) Http2Protocol.ClientConnectionPreface else ByteString.empty
          val initial = prefix ++ FrameRenderer.render(first)
          val following = (if (firstIsAck) ByteString.empty else FrameRenderer.render(SettingsAckFrame(Nil))) ++
            FrameRenderer.render(PingFrame(false, pingData))
          if (scenario == "fragmented") {
            // Split within the SETTINGS payload so the parser must retry the first-frame step.
            input.sendNext(initial.dropRight(1))
            input.sendNext(initial.takeRight(1) ++ following)
          } else input.sendNext(initial ++ following)
          if (!firstIsAck) {
            output.requestNext() shouldBe a[SettingsAckFrame]
            output.requestNext() shouldBe PingFrame(true, pingData)
          } else {
            // The invalid preface fails the stage, and Http2Blueprint.errorHandling renders the GOAWAY.
            // No stream can have been processed yet, so the last stream id is 0.
            output.requestNext() shouldBe GoAwayFrame(0, ErrorCode.PROTOCOL_ERROR)
            output.request(1).expectComplete()
          }
        } finally {
          finishApp()
          input.sendComplete()
          output.cancel()
        }
      }
    }
  }

  "Generic frame parsing still accepts a standalone PING without a connection preface" in {
    StreamTestKit.assertAllStagesStopped {
      val ping = PingFrame(false, ByteString("12345678"))
      Source.single(FrameRenderer.render(ping))
        .via(new Http2FrameParsing(false, system.log))
        .runWith(Sink.seq).futureValue shouldBe Seq(ping)
    }
  }

}
