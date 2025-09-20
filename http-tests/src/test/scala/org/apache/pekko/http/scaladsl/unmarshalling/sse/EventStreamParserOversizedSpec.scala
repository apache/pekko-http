/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package org.apache.pekko.http
package scaladsl
package unmarshalling
package sse

import org.apache.pekko
import pekko.http.scaladsl.model.sse.ServerSentEvent
import pekko.http.scaladsl.settings.OversizedSseStrategy
import pekko.stream.scaladsl.{ Sink, Source }
import pekko.util.ByteString

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

final class EventStreamParserOversizedSpec extends AsyncWordSpec with Matchers with BaseUnmarshallingSpec {

  "An EventStreamParser with oversized message handling" should {

    "parse normal SSE messages correctly with all strategies" in {
      val normalSseData = ByteString(
        """data: event1
          |
          |data: event2
          |event: custom
          |id: 123
          |
          |data: event3
          |
          |""".stripMargin)

      val expected = Vector(
        ServerSentEvent("event1"),
        ServerSentEvent("event2", Some("custom"), Some("123")),
        ServerSentEvent("event3"))

      for {
        failStreamResult <- Source.single(normalSseData)
          .via(EventStreamParser(100, 1000, emitEmptyEvents = false, OversizedSseStrategy.FailStream))
          .runWith(Sink.seq)
        logAndSkipResult <- Source.single(normalSseData)
          .via(EventStreamParser(100, 1000, emitEmptyEvents = false, OversizedSseStrategy.LogAndSkip))
          .runWith(Sink.seq)
        truncateResult <- Source.single(normalSseData)
          .via(EventStreamParser(100, 1000, emitEmptyEvents = false, OversizedSseStrategy.Truncate))
          .runWith(Sink.seq)
        deadLetterResult <- Source.single(normalSseData)
          .via(EventStreamParser(100, 1000, emitEmptyEvents = false, OversizedSseStrategy.DeadLetter))
          .runWith(Sink.seq)
      } yield {
        failStreamResult shouldBe expected
        logAndSkipResult shouldBe expected
        truncateResult shouldBe expected
        deadLetterResult shouldBe expected
      }
    }

    "fail the stream when using FailStream strategy with oversized SSE line" in {
      val oversizedSseData = ByteString(
        s"""data: before
           |
           |data: ${"x" * 200}
           |
           |data: after
           |
           |""".stripMargin)

      recoverToExceptionIf[IllegalStateException] {
        Source.single(oversizedSseData)
          .via(EventStreamParser(50, 1000, emitEmptyEvents = false, OversizedSseStrategy.FailStream))
          .runWith(Sink.seq)
      }.map { exception =>
        exception.getMessage should include("SSE line size")
        exception.getMessage should include("exceeds max-line-size: 50")
      }
    }

    "skip oversized SSE lines and continue processing with LogAndSkip strategy" in {
      val oversizedSseData = ByteString(
        s"""data: before
           |
           |data: ${"x" * 200}
           |
           |data: after
           |
           |""".stripMargin)

      Source.single(oversizedSseData)
        .via(EventStreamParser(50, 1000, emitEmptyEvents = false, OversizedSseStrategy.LogAndSkip))
        .runWith(Sink.seq)
        .map { result =>
          result shouldBe Vector(
            ServerSentEvent("before"),
            ServerSentEvent("after"))
        }
    }

    "truncate oversized SSE lines and continue processing with Truncate strategy" in {
      val oversizedLine = "x" * 200
      val oversizedSseData = ByteString(
        s"""data: before
           |
           |data: $oversizedLine
           |
           |data: after
           |
           |""".stripMargin)

      Source.single(oversizedSseData)
        .via(EventStreamParser(50, 1000, emitEmptyEvents = false, OversizedSseStrategy.Truncate))
        .runWith(Sink.seq)
        .map { result =>
          result should have size 3
          result(0) shouldBe ServerSentEvent("before")
          result(1).data shouldBe "x" * 44 // truncated to line size limit
          result(2) shouldBe ServerSentEvent("after")
        }
    }

    "send oversized SSE lines to dead letters and continue processing with DeadLetter strategy" in {
      val oversizedSseData = ByteString(
        s"""data: before
           |
           |data: ${"x" * 200}
           |
           |data: after
           |
           |""".stripMargin)

      Source.single(oversizedSseData)
        .via(EventStreamParser(50, 1000, emitEmptyEvents = false, OversizedSseStrategy.DeadLetter))
        .runWith(Sink.seq)
        .map { result =>
          result shouldBe Vector(
            ServerSentEvent("before"),
            ServerSentEvent("after"))
        }
    }

    "handle multiple oversized lines in complex SSE events with LogAndSkip strategy" in {
      val oversizedSseData = ByteString(
        s"""data: event1
           |
           |data: ${"x" * 100}
           |event: ${"y" * 100}
           |id: normal-id
           |
           |data: event2
           |
           |event: ${"z" * 100}
           |data: event3
           |
           |""".stripMargin)

      Source.single(oversizedSseData)
        .via(EventStreamParser(50, 1000, emitEmptyEvents = false, OversizedSseStrategy.LogAndSkip))
        .runWith(Sink.seq)
        .map { result =>
          result shouldBe Vector(
            ServerSentEvent("event1"),
            ServerSentEvent("event2"),
            ServerSentEvent("event3"))
        }
    }

    "handle multiline data with some oversized lines using Truncate strategy" in {
      val oversizedSseData = ByteString(
        s"""data: line1
           |data: ${"x" * 100}
           |data: line3
           |
           |data: after
           |
           |""".stripMargin)

      Source.single(oversizedSseData)
        .via(EventStreamParser(50, 1000, emitEmptyEvents = false, OversizedSseStrategy.Truncate))
        .runWith(Sink.seq)
        .map { result =>
          result should have size 2
          // According to SSE spec, multiple data fields are joined with newlines
          result(0).data shouldBe s"line1\n${"x" * 44}\nline3" // truncated middle line
          result(1) shouldBe ServerSentEvent("after")
        }
    }

    "handle streaming SSE data with oversized content across chunks" in {
      val chunk1 = ByteString("data: before\n\ndata: ")
      val chunk2 = ByteString("x" * 100 + "\n\ndata: after\n\n")

      Source(Vector(chunk1, chunk2))
        .via(EventStreamParser(50, 1000, emitEmptyEvents = false, OversizedSseStrategy.LogAndSkip))
        .runWith(Sink.seq)
        .map { result =>
          result shouldBe Vector(
            ServerSentEvent("before"),
            ServerSentEvent("after"))
        }
    }

    "handle event field oversizing with different strategies" in {
      val oversizedEventType = "x" * 100
      val oversizedSseData = ByteString(
        s"""data: before
           |
           |data: middle
           |event: $oversizedEventType
           |id: 123
           |
           |data: after
           |
           |""".stripMargin)

      for {
        logSkipResult <- Source.single(oversizedSseData)
          .via(EventStreamParser(50, 1000, emitEmptyEvents = false, OversizedSseStrategy.LogAndSkip))
          .runWith(Sink.seq)
        truncateResult <- Source.single(oversizedSseData)
          .via(EventStreamParser(50, 1000, emitEmptyEvents = false, OversizedSseStrategy.Truncate))
          .runWith(Sink.seq)
        deadLetterResult <- Source.single(oversizedSseData)
          .via(EventStreamParser(50, 1000, emitEmptyEvents = false, OversizedSseStrategy.DeadLetter))
          .runWith(Sink.seq)
      } yield {
        // All should preserve the before/after events
        (logSkipResult.map(_.data) should contain).allOf("before", "after")
        (truncateResult.map(_.data) should contain).allOf("before", "after")
        (deadLetterResult.map(_.data) should contain).allOf("before", "after")

        // Middle event behavior differs by strategy
        logSkipResult.find(_.data == "middle") shouldBe Some(ServerSentEvent("middle", None, Some("123")))
        (truncateResult.find(_.data == "middle").get.eventType.get should have).length(43) // truncated event type (50 - "event: ".length)
        deadLetterResult.find(_.data == "middle") shouldBe Some(ServerSentEvent("middle", None, Some("123")))
      }
    }

    "work with unlimited line sizes when maxLineSize is 0" in {
      val veryLongData = "x" * 10000
      val oversizedSseData = ByteString(
        s"""data: before
           |
           |data: $veryLongData
           |
           |data: after
           |
           |""".stripMargin)

      Source.single(oversizedSseData)
        .via(EventStreamParser(0, 20000, emitEmptyEvents = false, OversizedSseStrategy.FailStream)) // 0 = unlimited
        .runWith(Sink.seq)
        .map { result =>
          result should have size 3
          result(0) shouldBe ServerSentEvent("before")
          result(1) shouldBe ServerSentEvent(veryLongData)
          result(2) shouldBe ServerSentEvent("after")
        }
    }

    "fail the stream when using FailStream strategy with oversized SSE event (max-event-size)" in {
      // Create an event that definitely exceeds 30 bytes total
      val oversizedEventData = ByteString(
        s"""data: ${"x" * 20}
           |event: ${"y" * 20}
           |id: ${"z" * 20}
           |
           |data: after
           |
           |""".stripMargin)

      recoverToExceptionIf[IllegalStateException] {
        Source.single(oversizedEventData)
          .via(EventStreamParser(200, 30, emitEmptyEvents = false, OversizedSseStrategy.FailStream)) // Very small max-event-size
          .runWith(Sink.seq)
      }.map { exception =>
        exception.getMessage should include("Oversized SSE Event")
        exception.getMessage should include("exceeds max-event-size")
        exception.getMessage should include("30")
      }
    }

    "skip oversized SSE events and continue processing with LogAndSkip strategy (max-event-size)" in {
      val oversizedEventData = ByteString(
        s"""data: before
           |
           |data: ${"x" * 20}
           |event: ${"y" * 20}
           |
           |data: after
           |
           |""".stripMargin)

      Source.single(oversizedEventData)
        .via(EventStreamParser(200, 30, emitEmptyEvents = false, OversizedSseStrategy.LogAndSkip)) // Small max-event-size
        .runWith(Sink.seq)
        .map { result =>
          result shouldBe Vector(
            ServerSentEvent("before"),
            ServerSentEvent("after"))
        }
    }

    "truncate oversized SSE events and continue processing with Truncate strategy (max-event-size)" in {
      val oversizedEventData = ByteString(
        s"""data: before
           |
           |data: ${"x" * 20}
           |event: test
           |
           |event: test
           |data: ${"x" * 20}
           |
           |data: after
           |
           |""".stripMargin)

      Source.single(oversizedEventData)
        .via(EventStreamParser(200, 30, emitEmptyEvents = false, OversizedSseStrategy.Truncate)) // Small max-event-size
        .runWith(Sink.seq)
        .map { result =>
          result should have size 4
          result(0) shouldBe ServerSentEvent("before")
          // First event: data field only, event field dropped due to size limit
          result(1).data shouldBe "x" * 20
          result(1).eventType shouldBe None
          // Second event: event field only, data field dropped due to size limit
          result(2).data shouldBe ""
          result(2).eventType shouldBe Some("test")
          result(3) shouldBe ServerSentEvent("after")
        }
    }

    "send oversized SSE events to dead letters and continue processing with DeadLetter strategy (max-event-size)" in {
      val oversizedEventData = ByteString(
        s"""data: before
           |
           |data: ${"x" * 20}
           |event: ${"y" * 20}
           |
           |data: after
           |
           |""".stripMargin)

      Source.single(oversizedEventData)
        .via(EventStreamParser(200, 30, emitEmptyEvents = false, OversizedSseStrategy.DeadLetter)) // Small max-event-size
        .runWith(Sink.seq)
        .map { result =>
          result shouldBe Vector(
            ServerSentEvent("before"),
            ServerSentEvent("after"))
        }
    }

    "handle events that exceed max-event-size during construction with DeadLetter strategy" in {
      // This tests the proactive check - event exceeds limit while being built
      val eventData = ByteString(
        s"""data: before
           |
           |data: line1
           |data: line2
           |data: ${"x" * 20}
           |
           |data: after
           |
           |""".stripMargin)

      Source.single(eventData)
        .via(EventStreamParser(200, 20, emitEmptyEvents = false, OversizedSseStrategy.DeadLetter)) // Very small max-event-size
        .runWith(Sink.seq)
        .map { result =>
          result shouldBe Vector(
            ServerSentEvent("before"),
            ServerSentEvent("after"))
        }
    }

    "work with unlimited event sizes when maxEventSize is 0" in {
      val veryLongEvent = ByteString(
        s"""data: before
           |
           |data: ${"x" * 1000}
           |event: ${"y" * 1000}
           |id: ${"z" * 1000}
           |
           |data: after
           |
           |""".stripMargin)

      Source.single(veryLongEvent)
        .via(EventStreamParser(2000, 0, emitEmptyEvents = false, OversizedSseStrategy.FailStream)) // 0 = unlimited event size
        .runWith(Sink.seq)
        .map { result =>
          result should have size 3
          result(0) shouldBe ServerSentEvent("before")
          result(1).data shouldBe "x" * 1000
          result(1).eventType shouldBe Some("y" * 1000)
          result(1).id shouldBe Some("z" * 1000)
          result(2) shouldBe ServerSentEvent("after")
        }
    }

    "handle mixed line and event size violations with consistent strategies" in {
      val mixedOversizedData = ByteString(
        s"""data: before
           |
           |data: ${"line-too-long-" * 10}
           |event: normal
           |
           |data: line1
           |data: line2
           |data: line3
           |event: ${"event-too-long-" * 5}
           |
           |data: after
           |
           |""".stripMargin)

      Source.single(mixedOversizedData)
        .via(EventStreamParser(50, 80, emitEmptyEvents = false, OversizedSseStrategy.LogAndSkip))
        .runWith(Sink.seq)
        .map { result =>
          // With line-level and event-level violations:
          // 1. First event: "before" (normal)
          // 2. Second event: oversized data line skipped, normal event type skipped, then multi-line data with oversized event field → event emitted with only data
          // 3. Third event: "after" (normal)
          result should have size 3
          result(0) shouldBe ServerSentEvent("before")
          result(1).data shouldBe "line1\nline2\nline3" // oversized fields were skipped
          result(1).eventType shouldBe None
          result(2) shouldBe ServerSentEvent("after")
        }
    }

    "skip remaining lines in event after hitting size limit during construction" in {
      val eventData = ByteString(
        s"""data: before
           |
           |data: line1
           |data: ${"x" * 20}
           |data: line3-should-be-skipped
           |event: event-type-should-be-skipped
           |id: id-should-be-skipped
           |
           |data: after
           |
           |""".stripMargin)

      Source.single(eventData)
        .via(EventStreamParser(200, 20, emitEmptyEvents = false, OversizedSseStrategy.LogAndSkip)) // Very small max-event-size
        .runWith(Sink.seq)
        .map { result =>
          result shouldBe Vector(
            ServerSentEvent("before"),
            ServerSentEvent("after"))
          // The middle event should be completely skipped - no partial event with line1 should be emitted
        }
    }
  }
}
