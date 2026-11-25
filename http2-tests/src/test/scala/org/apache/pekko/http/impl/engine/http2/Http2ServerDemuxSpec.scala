/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2021-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.impl.engine.http2

import org.apache.pekko
import pekko.http.impl.engine.http2.FrameEvent.{ ParsedHeadersFrame, Setting, SettingsFrame }
import pekko.http.impl.engine.http2.Http2Protocol.SettingIdentifier
import pekko.http.impl.util.PekkoSpecWithMaterializer
import pekko.http.scaladsl.settings.Http2ServerSettings
import pekko.stream.scaladsl.{ BidiFlow, Flow, Keep }
import pekko.stream.testkit.scaladsl.{ TestSink, TestSource }
import pekko.util.{ ByteString, OptionVal }

/**
 * low-level tests testing Http2ServerDemux in isolation
 */
class Http2ServerDemuxSpec extends PekkoSpecWithMaterializer("""
    pekko.http.server.http2.log-frames = on
    pekko.stream.materializer.debug.fuzzing-mode = on
  """) {
  "Isolated incoming flow control" should {
    "never advertise credit for bytes already buffered or credited" in {
      val budget = Int.MaxValue
      val controller = IncomingFlowController.isolated(budget, budget / 2)
      val freeSpace = Vector(0, 1, 32767, 65535, budget)
      for {
        free <- freeSpace
        outstanding <- Vector(0, free / 2, free).distinct
      } {
        val buffered = budget - free
        val increment = controller.onConnectionDataReceived(outstanding, buffered)
        increment should be >= 0
        (increment.toLong + outstanding + buffered) should be <= budget.toLong
      }
      controller.onConnectionDataReceived(0, budget) shouldBe 0
      controller.onConnectionDataReceived(0, budget - 1) shouldBe 1
      controller.onStreamDataDispatched(0, budget - 1, 0, budget / 2 - 1) shouldBe
      IncomingFlowController.WindowIncrements(1, 1)
    }
  }

  "Http2ServerDemux" should {
    "not pull twice when started with initial RemoteSettings from HTTP/1.1 Upgrade" in {
      val settings = Http2ServerSettings("")
      val initialRemoteSettings = Seq[Setting](
        (SettingIdentifier.SETTINGS_MAX_CONCURRENT_STREAMS, 100),
        (SettingIdentifier.SETTINGS_INITIAL_WINDOW_SIZE, 335),
        (SettingIdentifier.SETTINGS_ENABLE_PUSH, 0))
      val bidi = BidiFlow.fromGraph(new Http2ServerDemux(settings, initialRemoteSettings, upgraded = true))

      val ((substreamProducer, (frameConsumer, frameProducer)), substreamConsumer) = TestSource[Http2SubStream]()
        .viaMat(bidi.joinMat(Flow.fromSinkAndSourceMat(TestSink[FrameEvent](), TestSource[FrameEvent]())(
          Keep.both))(Keep.right))(Keep.both)
        .toMat(TestSink[Http2SubStream]())(Keep.both)
        .run()

      frameConsumer.request(1000)
      substreamProducer.ensureSubscription()
      substreamConsumer.request(1000)
      frameProducer.ensureSubscription()

      // The request is taken from the HTTP/1.1 request that had the Upgrade
      // header and is passed to the handler code 'directly', bypassing the demux stage,
      // so the first thing the demux stage sees of this request is the response:
      val response = ParsedHeadersFrame(streamId = 1, endStream = true, Seq((":status", "200")), None, None)
      substreamProducer.sendNext(new Http2SubStream(
        response,
        OptionVal.None,
        Left(ByteString.empty),
        Map.empty))

      frameConsumer.expectNext() shouldBe an[SettingsFrame]
      // The client could send an 'ack' here, but doesn't need to
      // frameProducer.sendNext(SettingsAckFrame(frame.asInstanceOf[SettingsFrame].settings))

      frameConsumer.expectNext() shouldBe response
    }
  }
}
