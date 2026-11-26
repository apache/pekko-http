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

import scala.concurrent.duration._

import org.apache.pekko
import pekko.http.impl.engine.http2.FrameEvent.{
  DataFrame,
  ParsedHeadersFrame,
  Setting,
  SettingsAckFrame,
  SettingsFrame,
  WindowUpdateFrame
}
import pekko.http.impl.engine.http2.Http2Protocol.SettingIdentifier
import pekko.http.impl.engine.parsing.HttpHeaderParser
import pekko.http.impl.engine.rendering.DateHeaderRendering
import pekko.http.impl.util.PekkoSpecWithMaterializer
import pekko.http.scaladsl.model.{ ErrorInfo, HttpMethods, HttpRequest, HttpResponse, Uri }
import pekko.http.scaladsl.model.headers.Expect
import pekko.http.scaladsl.settings.{ Http2ServerSettings, ServerSettings }
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
  "Http2ServerDemux" should {
    "send the final response first when the request head is already rejected" in {
      val settings = ServerSettings(system).mapHttp2Settings(_.withMinCollectStrictEntitySize(0))
      val headerParser = HttpHeaderParser(settings.parserSettings, system.log)
      val server = Http2Blueprint.httpLayer(settings, system.log, DateHeaderRendering()).atop(
        Http2Blueprint.serverDemux(settings.http2Settings, Nil, upgraded = false, Some(settings -> headerParser)))
      val ((responseProducer, (frameConsumer, frameProducer)), requestConsumer) = TestSource[HttpResponse]()
        .viaMat(server.joinMat(Flow.fromSinkAndSourceMat(TestSink[FrameEvent](), TestSource[FrameEvent]())(
          Keep.both))(Keep.right))(Keep.both)
        .toMat(TestSink[HttpRequest]())(Keep.both)
        .run()

      try {
        frameConsumer.request(10)
        requestConsumer.request(1)
        responseProducer.ensureSubscription()
        frameProducer.ensureSubscription()
        frameConsumer.expectNext() shouldBe a[SettingsFrame]
        frameProducer.sendNext(SettingsFrame(Nil))
        frameConsumer.expectNext() shouldBe SettingsAckFrame(Nil)

        // Start after header decoding with its typed error result, not an invalid wire fixture.
        val fields = Seq[(String, AnyRef)](
          ":method" -> HttpMethods.POST,
          ":scheme" -> "https",
          ":authority" -> Uri.Authority(Uri.Host("example.com")),
          ":path" -> (Uri.Path("/upload"), None),
          "expect" -> Expect.`100-continue`)
        frameProducer.sendNext(
          ParsedHeadersFrame(1, endStream = false, fields, None, Some(ErrorInfo("Rejected request head"))))

        // Do not filter informational HEADERS out: they must not precede the final response.
        frameConsumer.expectNext() match {
          case firstResponse: ParsedHeadersFrame =>
            firstResponse.streamId shouldBe 1
            firstResponse.keyValuePairs.toMap.get(":status") shouldBe Some("400")
          case other => fail(s"Expected final response HEADERS, got $other")
        }
        requestConsumer.expectNoMessage(100.millis)
      } finally {
        requestConsumer.cancel()
        frameConsumer.cancel()
      }
    }

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

    "drain a completed request entity in order when consumption starts after the final DATA" in {
      val settings = Http2ServerSettings("").withMinCollectStrictEntitySize(0).withRequestEntityChunkSize(3)
      val bidi = BidiFlow.fromGraph(new Http2ServerDemux(settings, Nil, upgraded = false))

      val ((responseProducer, (frameConsumer, frameProducer)), requestConsumer) = TestSource[Http2SubStream]()
        .viaMat(bidi.joinMat(Flow.fromSinkAndSourceMat(TestSink[FrameEvent](), TestSource[FrameEvent]())(
          Keep.both))(Keep.right))(Keep.both)
        .toMat(TestSink[Http2SubStream]())(Keep.both)
        .run()

      try {
        frameConsumer.request(8)
        val serverSettings = frameConsumer.expectNext().asInstanceOf[SettingsFrame]
        frameProducer.sendNext(SettingsFrame(Nil))
        frameProducer.sendNext(FrameEvent.SettingsAckFrame(serverSettings.settings))
        frameConsumer.expectNext() shouldBe FrameEvent.SettingsAckFrame(Nil)

        val requestHeaders = ParsedHeadersFrame(1, endStream = false,
          Seq(":method" -> "POST", ":scheme" -> "https", ":authority" -> "example.test", ":path" -> "/first"),
          None, None)
        frameProducer.sendNext(requestHeaders)
        val request = requestConsumer.requestNext()
        request.initialHeaders shouldBe requestHeaders

        val response = ParsedHeadersFrame(1, endStream = true, Seq(":status" -> "200"), None, None)
        responseProducer.sendNext(Http2SubStream(response, OptionVal.None, Left(ByteString.empty), Map.empty))
        frameConsumer.expectNext() shouldBe response

        frameProducer.sendNext(FrameEvent.DataFrame(1, endStream = false, ByteString("ab")))
        frameProducer.sendNext(FrameEvent.DataFrame(1, endStream = false, ByteString("cde")))
        frameProducer.sendNext(FrameEvent.DataFrame(1, endStream = true, ByteString("fghi")))

        // Observing the later request proves the final DATA was handled before we start consuming the first entity.
        val siblingHeaders = ParsedHeadersFrame(3, endStream = true,
          Seq(":method" -> "GET", ":scheme" -> "https", ":authority" -> "example.test", ":path" -> "/second"),
          None, None)
        frameProducer.sendNext(siblingHeaders)
        requestConsumer.requestNext().initialHeaders shouldBe siblingHeaders

        val body = request.createEntity(-1L, OptionVal.None).dataBytes
          .fold(ByteString.empty)(_ ++ _)
          .runWith(TestSink[ByteString]())
        body.request(1)
        body.expectNext(ByteString("abcdefghi"))
        body.expectComplete()
      } finally {
        frameConsumer.cancel()
        requestConsumer.cancel()
      }
    }

    "release only the response on the stream granted more send credit" in {
      val bidi = BidiFlow.fromGraph(new Http2ServerDemux(Http2ServerSettings(""), Nil, upgraded = false))
      val ((responses, (outgoingFrames, incomingFrames)), requests) = TestSource[Http2SubStream]()
        .viaMat(bidi.joinMat(Flow.fromSinkAndSourceMat(TestSink[FrameEvent](), TestSource[FrameEvent]())(
          Keep.both))(Keep.right))(Keep.both)
        .toMat(TestSink[Http2SubStream]())(Keep.both)
        .run()

      try {
        outgoingFrames.request(8)
        requests.request(2)
        outgoingFrames.expectNext() shouldBe a[SettingsFrame]
        val initialCredit = 2
        val remoteSettings = Seq(Setting(SettingIdentifier.SETTINGS_INITIAL_WINDOW_SIZE, initialCredit))
        incomingFrames.sendNext(SettingsFrame(remoteSettings))
        outgoingFrames.expectNext() shouldBe SettingsAckFrame(remoteSettings)
        incomingFrames.sendNext(SettingsAckFrame(Nil))

        def startResponse(streamId: Int, data: ByteString): Unit = {
          val uri = Uri(s"https://example.test/response-$streamId")
          val request = ParsedHeadersFrame(streamId, endStream = true,
            Seq(
              ":method" -> HttpMethods.GET,
              ":scheme" -> uri.scheme,
              ":authority" -> uri.authority,
              ":path" -> (uri.path, uri.rawQueryString)), None, None)
          incomingFrames.sendNext(request)
          requests.expectNext().initialHeaders shouldBe request

          val response = ParsedHeadersFrame(streamId, endStream = false, Seq(":status" -> "200"), None, None)
          responses.sendNext(Http2SubStream(response, OptionVal.None, Left(data), Map.empty))
          outgoingFrames.expectNext() shouldBe response
          outgoingFrames.expectNext() shouldBe DataFrame(streamId, endStream = false, data.take(initialCredit))
        }

        val firstData = ByteString("alpha")
        val secondData = ByteString("orange")
        startResponse(1, firstData)
        startResponse(3, secondData)

        val firstRemainder = firstData.drop(initialCredit)
        incomingFrames.sendNext(WindowUpdateFrame(1, firstRemainder.length))
        outgoingFrames.expectNext() shouldBe DataFrame(1, endStream = true, firstRemainder)
        // Demand is still pending on the connection; response 3 needs a grant of its own.
        outgoingFrames.expectNoMessage(100.millis)

        val secondRemainder = secondData.drop(initialCredit)
        incomingFrames.sendNext(WindowUpdateFrame(3, secondRemainder.length))
        outgoingFrames.expectNext() shouldBe DataFrame(3, endStream = true, secondRemainder)
      } finally {
        outgoingFrames.cancel()
        requests.cancel()
      }
    }

    "share connection send credit between two response bodies" in {
      val demux = BidiFlow.fromGraph(new Http2ServerDemux(Http2ServerSettings(""), Nil, upgraded = false))
      val ((responses, (outgoingFrames, incomingFrames)), requests) = TestSource[Http2SubStream]()
        .viaMat(demux.joinMat(Flow.fromSinkAndSourceMat(TestSink[FrameEvent](), TestSource[FrameEvent]())(
          Keep.both))(Keep.right))(Keep.both)
        .toMat(TestSink[Http2SubStream]())(Keep.both)
        .run()

      try {
        requests.request(3)
        val serverSettings = outgoingFrames.requestNext() match {
          case frame: SettingsFrame => frame.settings
          case other                => fail(s"Expected server SETTINGS, got $other")
        }
        incomingFrames.sendNext(SettingsFrame(Nil))
        outgoingFrames.requestNext() shouldBe SettingsAckFrame(Nil)
        incomingFrames.sendNext(SettingsAckFrame(serverSettings))

        def submitResponse(streamId: Int, body: ByteString): ParsedHeadersFrame = {
          val uri = Uri(s"https://example.test/report-$streamId")
          val request = ParsedHeadersFrame(streamId, endStream = true,
            Seq(
              ":method" -> HttpMethods.GET,
              ":scheme" -> uri.scheme,
              ":authority" -> uri.authority,
              ":path" -> (uri.path, uri.rawQueryString)), None, None)
          incomingFrames.sendNext(request)
          requests.expectNext().initialHeaders shouldBe request

          val response = ParsedHeadersFrame(streamId, endStream = false, Seq(":status" -> "200"), None, None)
          responses.sendNext(Http2SubStream(response, OptionVal.None, Left(body), Map.empty))
          response
        }

        def dataFrame(event: FrameEvent): DataFrame = event match {
          case frame: DataFrame => frame
          case other            => fail(s"Expected a DATA frame, got $other")
        }

        // Only sending DATA can reduce the fixed initial connection window (RFC 9113 section 6.9.1).
        val sharedCredit = 8
        val earlierBody = ByteString(Array.fill[Byte](Http2Protocol.InitialWindowSize - sharedCredit)('a'.toByte))
        val earlierHeaders = submitResponse(1, earlierBody)
        outgoingFrames.requestNext() shouldBe earlierHeaders
        within(3.seconds) {
          var offset = 0
          var ended = false
          while (!ended) {
            val frame = dataFrame(outgoingFrames.requestNext())
            frame.streamId shouldBe 1
            val nextOffset = offset + frame.payload.length
            frame.payload shouldBe earlierBody.slice(offset, nextOffset)
            offset = nextOffset
            ended = frame.endStream
          }
          offset shouldBe earlierBody.size
        }

        val bodies = Map(3 -> ByteString("violet"), 5 -> ByteString("orange"))
        val expectedHeaders = Map(3 -> submitResponse(3, bodies(3)), 5 -> submitResponse(5, bodies(5)))
        var received = Map(3 -> ByteString.empty, 5 -> ByteString.empty)
        var receivedHeaders = Set.empty[Int]
        var ended = Set.empty[Int]
        def receiveFrame(): Unit = outgoingFrames.expectNext() match {
          case frame: ParsedHeadersFrame =>
            frame shouldBe expectedHeaders(frame.streamId)
            receivedHeaders should not contain (frame.streamId)
            receivedHeaders += frame.streamId
          case frame: DataFrame =>
            val expected = bodies(frame.streamId)
            receivedHeaders should contain(frame.streamId)
            ended should not contain (frame.streamId)
            val data = received(frame.streamId) ++ frame.payload
            data shouldBe expected.take(data.length)
            received += frame.streamId -> data
            if (frame.endStream) {
              data shouldBe expected
              ended += frame.streamId
            }
          case other => fail(s"Expected response HEADERS or DATA, got $other")
        }

        // Either response can use the shared allowance first. Leave downstream demand pending.
        outgoingFrames.request(16)
        def bytesReceived: Int = received.valuesIterator.map(_.length).sum
        within(3.seconds) {
          while (bytesReceived < sharedCredit) receiveFrame()
          bytesReceived shouldBe sharedCredit
          receivedHeaders shouldBe bodies.keySet
          outgoingFrames.expectNoMessage(100.millis)
        }

        // Replenish exactly the bytes still queued, at connection scope only.
        val queuedBytes = bodies.valuesIterator.map(_.size).sum - sharedCredit
        incomingFrames.sendNext(WindowUpdateFrame(Http2Protocol.NoStreamId, queuedBytes))
        within(3.seconds) {
          while (ended.size < bodies.size) receiveFrame()
        }
        received shouldBe bodies
      } finally {
        outgoingFrames.cancel()
        requests.cancel()
      }
    }
  }
}
