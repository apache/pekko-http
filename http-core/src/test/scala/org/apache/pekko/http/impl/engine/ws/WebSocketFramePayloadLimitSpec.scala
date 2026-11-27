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

package org.apache.pekko.http.impl.engine.ws

import scala.concurrent.duration._

import org.apache.pekko
import pekko.http.impl.settings.WebSocketSettingsImpl
import pekko.http.impl.util.PekkoSpecWithMaterializer
import pekko.http.scaladsl.Http
import pekko.http.scaladsl.model.AttributeKeys.webSocketUpgrade
import pekko.http.scaladsl.model.StatusCodes
import pekko.http.scaladsl.model.ws.{ BinaryMessage, Message, WebSocketRequest }
import pekko.http.scaladsl.settings.{ ClientConnectionSettings, ServerSettings }
import pekko.stream.scaladsl.{ Flow, Keep, Sink, Source }
import pekko.stream.testkit.scaladsl.{ TestSink, TestSource }
import pekko.util.ByteString

import com.typesafe.config.ConfigFactory

class WebSocketFramePayloadLimitSpec extends PekkoSpecWithMaterializer() {
  "The incoming WebSocket frame payload limit" must {
    "keep distinct settings for clients and servers without changing the unlimited defaults" in {
      val defaultConfig = system.settings.config
      val client = ClientConnectionSettings(defaultConfig).websocketSettings
      val server = ServerSettings(defaultConfig).websocketSettings
      client.maxInboundFramePayloadLength shouldBe Long.MaxValue
      server.maxInboundFramePayloadLength shouldBe Long.MaxValue

      val configured = ConfigFactory.parseString("""
        pekko.http.client.websocket.max-inbound-frame-payload-length = 256
        pekko.http.server.websocket.max-inbound-frame-payload-length = 512
      """).withFallback(defaultConfig)
      ClientConnectionSettings(configured).websocketSettings.maxInboundFramePayloadLength shouldBe 256L
      ServerSettings(configured).websocketSettings.maxInboundFramePayloadLength shouldBe 512L

      val oldClientConfig = defaultConfig.getConfig("pekko.http.client.websocket")
        .withoutPath("max-inbound-frame-payload-length")
      WebSocketSettingsImpl.client(oldClientConfig).maxInboundFramePayloadLength shouldBe Long.MaxValue
    }

    "retain the configured value when copied by legacy settings methods" in {
      val settings = ServerSettings(system).websocketSettings.withMaxInboundFramePayloadLength(512)
        .asInstanceOf[WebSocketSettingsImpl]
      settings.withLogFrames(true).maxInboundFramePayloadLength shouldBe 512L
      settings.copy(
        settings.randomFactory,
        settings.periodicKeepAliveMode,
        settings.periodicKeepAliveMaxIdle,
        settings.periodicKeepAliveData,
        true,
        settings.compression,
        settings.receiveIdleTimeout,
        settings.sendIdleTimeout,
        settings.closeTimeout,
        settings.openingTimeout).maxInboundFramePayloadLength shouldBe 512L
    }

    "stream an ordinary frame at the limit without waiting for its full payload" in {
      val payload = ByteString("a" * 256)
      val frame = FrameEvent.fullFrame(Protocol.Opcode.Binary, None, payload, fin = true)
      val bytes = Source.single(frame).via(new FrameEventRenderer).runWith(Sink.head).futureValue
      val split = bytes.length - 20
      val (source, sink) = TestSource[ByteString]()
        .via(FrameEventParserStage.withPayloadLimit(payload.length.toLong))
        .toMat(TestSink[FrameEvent]())(Keep.both)
        .run()

      sink.request(2)
      source.sendNext(bytes.take(split))
      sink.expectNext(FrameStart(frame.header, payload.dropRight(20)))
      source.sendNext(bytes.drop(split))
      sink.expectNext(FrameData(payload.takeRight(20), lastPart = true))
      source.sendComplete()
      sink.expectComplete()
    }

    "deliver application messages with both receiver limits enabled" in {
      val server = ServerSettings(system)
        .mapWebsocketSettings(_.withMaxInboundFramePayloadLength(512))
      val clientDefaults = ClientConnectionSettings(system)
      val client = clientDefaults.withWebsocketSettings(
        clientDefaults.websocketSettings.withMaxInboundFramePayloadLength(256))
      val http = Http()
      val binding = http.newServerAt("127.0.0.1", 0).withSettings(server).bindSync {
        _.attribute(webSocketUpgrade).get.handleMessages(Flow[Message], None)
      }.futureValue
      try {
        val payload = ByteString("a" * 256)
        val exchange = http.webSocketClientFlow(
          WebSocketRequest(s"ws://127.0.0.1:${binding.localAddress.getPort}"), settings = client)
        val (upgrade, echo) = Source.single[Message](BinaryMessage(payload))
          .concat(Source.maybe[Message])
          .viaMat(exchange)(Keep.right)
          .toMat(Sink.head[Message])(Keep.both)
          .run()
        upgrade.futureValue.response.status shouldBe StatusCodes.SwitchingProtocols
        echo.futureValue.asBinaryMessage.asScala.toStrict(3.seconds).futureValue.data shouldBe payload
      } finally binding.unbind().futureValue
    }
  }
}
