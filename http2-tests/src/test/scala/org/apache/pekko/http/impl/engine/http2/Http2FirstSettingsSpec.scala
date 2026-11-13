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

import org.apache.pekko.NotUsed
import org.apache.pekko.http.impl.engine.http2.FrameEvent._
import org.apache.pekko.http.impl.engine.http2.Http2Protocol.ErrorCode
import org.apache.pekko.http.impl.engine.http2.framing.{ FrameRenderer, Http2FrameParsing }
import org.apache.pekko.http.impl.util.PekkoSpecWithMaterializer
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.settings.{ ClientConnectionSettings, ServerSettings }
import org.apache.pekko.stream.scaladsl.{ Flow, Sink, Source }
import org.apache.pekko.stream.testkit.TestSubscriber.{ OnComplete, OnError, OnNext }
import org.apache.pekko.stream.testkit.scaladsl.StreamTestKit
import org.apache.pekko.stream.testkit.{ TestPublisher, TestSubscriber }
import org.apache.pekko.util.ByteString

class Http2FirstSettingsSpec extends PekkoSpecWithMaterializer("") {
  for {
    server <- Seq(true, false)
    scenario <- Seq("empty", "nonempty", "fragmented", "ACK", "early request")
    if !server || scenario != "early request"
  } {
    s"${if (server) "Server" else "Client"} first peer SETTINGS $scenario" in {
      val firstIsAck = scenario == "ACK"
      StreamTestKit.assertAllStagesStopped {
        var finishApp: () => Unit = () => ()
        val connection: Flow[ByteString, ByteString, Any] = if (server) {
          val responses = Source.maybe[HttpResponse].mapMaterializedValue { p =>
            finishApp = () => { p.trySuccess(None); () }
            NotUsed
          }
          Http2Blueprint.serverStack(ServerSettings(system), system.log, telemetry = NoOpTelemetry,
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
          Http2Blueprint.clientStack(ClientConnectionSettings(system), system.log, NoOpTelemetry).reversed
            .join(Flow.fromSinkAndSource(Sink.ignore, appRequests))
        }
        val input = TestPublisher.probe[ByteString]()
        val output = TestSubscriber.probe[FrameEvent]()
        Source.fromPublisher(input).via(connection)
          .via(new Http2FrameParsing(!server, system.log))
          .runWith(Sink.fromSubscriber(output))
        try {
          output.requestNext() shouldBe a[SettingsFrame]
          if (scenario == "early request") output.requestNext() shouldBe a[HeadersFrame]
          val pingData = ByteString("12345678")
          val settings = if (scenario == "nonempty" || scenario == "fragmented")
            Seq(Setting(Http2Protocol.SettingIdentifier.SETTINGS_MAX_CONCURRENT_STREAMS, 2))
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
            output.request(1).expectEvent() match {
              case OnNext(frame: GoAwayFrame) =>
                info("Observed PROTOCOL_ERROR GOAWAY followed by connection completion")
                frame.errorCode shouldBe ErrorCode.PROTOCOL_ERROR
                output.request(1).expectComplete()
              case OnComplete =>
                info("Observed connection completion without GOAWAY, permitted by RFC 9113 section 3.4")
                succeed
              case OnError(ex) =>
                fail("Termination error requires provenance review before classification", ex)
              case other => fail(s"Expected connection termination for an invalid preface, observed $other")
            }
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
