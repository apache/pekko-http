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
import pekko.stream.scaladsl.{ Sink, Source }
import pekko.util.ByteString

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

/**
 * Tests for CRLF line-ending handling via the public [[EventStreamParser]] API.
 * Covers issue https://github.com/apache/pekko-http/issues/797.
 */
final class EventStreamParserCrlfSpec extends AsyncWordSpec with Matchers with BaseUnmarshallingSpec {

  private val maxLineSize = 1048576
  private val maxEventSize = 1048576

  "EventStreamParser" when {

    "receiving a stream with CRLF line endings" should {

      "parse a single event with CRLF-terminated data line" in {
        val input = ByteString("data: hello\r\n\r\n")
        Source.single(input)
          .via(EventStreamParser(maxLineSize, maxEventSize))
          .runWith(Sink.seq)
          .map(_ shouldBe Vector(ServerSentEvent("hello")))
      }

      "parse multiple events all using CRLF line endings" in {
        val input = ByteString("data: event1\r\n\r\ndata: event2\r\n\r\ndata: event3\r\n\r\n")
        Source.single(input)
          .via(EventStreamParser(maxLineSize, maxEventSize))
          .runWith(Sink.seq)
          .map(_ shouldBe Vector(
            ServerSentEvent("event1"),
            ServerSentEvent("event2"),
            ServerSentEvent("event3")))
      }

      "parse all SSE field types with CRLF line endings" in {
        val input = ByteString(
          "data: the data\r\n" +
          "event: my-event\r\n" +
          "id: 99\r\n" +
          "retry: 3000\r\n" +
          "\r\n")
        Source.single(input)
          .via(EventStreamParser(maxLineSize, maxEventSize))
          .runWith(Sink.seq)
          .map(_ shouldBe Vector(
            ServerSentEvent("the data", Some("my-event"), Some("99"), Some(3000))))
      }

      "parse multi-line data fields with CRLF line endings" in {
        val input = ByteString(
          "data: line1\r\n" +
          "data: line2\r\n" +
          "data: line3\r\n" +
          "\r\n")
        Source.single(input)
          .via(EventStreamParser(maxLineSize, maxEventSize))
          .runWith(Sink.seq)
          .map(_ shouldBe Vector(ServerSentEvent("line1\nline2\nline3")))
      }

      "ignore comment lines with CRLF endings" in {
        val input = ByteString(
          "data: event1\r\n" +
          ":this is a comment\r\n" +
          "\r\n")
        Source.single(input)
          .via(EventStreamParser(maxLineSize, maxEventSize))
          .runWith(Sink.seq)
          .map(_ shouldBe Vector(ServerSentEvent("event1")))
      }

      "not emit events with no data field when emitEmptyEvents is false" in {
        val input = ByteString(
          "data: real\r\n" +
          "\r\n" +
          "\r\n" +
          "data: also real\r\n" +
          "\r\n")
        Source.single(input)
          .via(EventStreamParser(maxLineSize, maxEventSize, emitEmptyEvents = false))
          .runWith(Sink.seq)
          .map(_ shouldBe Vector(
            ServerSentEvent("real"),
            ServerSentEvent("also real")))
      }

      "emit empty events (heartbeats) when emitEmptyEvents is true with CRLF" in {
        // A heartbeat is a data field with an empty value (i.e. "data: " or "data:"),
        // not merely a blank separator line.
        val input = ByteString(
          "data: before\r\n" +
          "\r\n" +
          "data: \r\n" +
          "\r\n" +
          "data: after\r\n" +
          "\r\n")
        Source.single(input)
          .via(EventStreamParser(maxLineSize, maxEventSize, emitEmptyEvents = true))
          .runWith(Sink.seq)
          .map(_ shouldBe Vector(
            ServerSentEvent("before"),
            ServerSentEvent.heartbeat,
            ServerSentEvent("after")))
      }
    }

    "receiving a stream with CR-only (\\r) line endings" should {

      "parse a single event with CR-only line endings" in {
        val input = ByteString("data: hello\r\r")
        Source.single(input)
          .via(EventStreamParser(maxLineSize, maxEventSize))
          .runWith(Sink.seq)
          .map(_ shouldBe Vector(ServerSentEvent("hello")))
      }

      "parse multiple events with CR-only line endings" in {
        val input = ByteString("data: event1\r\rdata: event2\r\r")
        Source.single(input)
          .via(EventStreamParser(maxLineSize, maxEventSize))
          .runWith(Sink.seq)
          .map(_ shouldBe Vector(
            ServerSentEvent("event1"),
            ServerSentEvent("event2")))
      }

      "parse all SSE field types with CR-only line endings" in {
        val input = ByteString("data: the data\revent: my-event\rid: 42\rretry: 1000\r\r")
        Source.single(input)
          .via(EventStreamParser(maxLineSize, maxEventSize))
          .runWith(Sink.seq)
          .map(_ shouldBe Vector(
            ServerSentEvent("the data", Some("my-event"), Some("42"), Some(1000))))
      }
    }

    "receiving a stream with mixed line endings" should {

      "parse events correctly when line endings vary within the stream" in {
        // Mix of LF-only, CR-only, and CRLF
        val input = ByteString(
          "data: lf-event\n" +
          "\n" +
          "data: cr-event\r" +
          "\r" +
          "data: crlf-event\r\n" +
          "\r\n")
        Source.single(input)
          .via(EventStreamParser(maxLineSize, maxEventSize))
          .runWith(Sink.seq)
          .map(_ shouldBe Vector(
            ServerSentEvent("lf-event"),
            ServerSentEvent("cr-event"),
            ServerSentEvent("crlf-event")))
      }

      "parse a single event whose fields use different line endings" in {
        // Each field line uses a different terminator; the event is terminated by a lone LF
        val input = ByteString(
          "data: the data\r\n" +
          "event: my-event\n" +
          "id: 7\r\n" +
          "\n")
        Source.single(input)
          .via(EventStreamParser(maxLineSize, maxEventSize))
          .runWith(Sink.seq)
          .map(_ shouldBe Vector(
            ServerSentEvent("the data", Some("my-event"), Some("7"))))
      }
    }

    "receiving a CRLF stream delivered in multiple small chunks" should {

      "parse events correctly when CRLF is split across chunk boundaries" in {
        // The \r and \n of the CRLF pair for event1 arrive in separate ByteString chunks
        val chunks = Vector(
          ByteString("data: event1\r"),     // ends with \r
          ByteString("\n\r\n"),             // \n completes the CRLF; \r\n is the event separator
          ByteString("data: event2\r\n\r\n"))
        Source(chunks)
          .via(EventStreamParser(maxLineSize, maxEventSize))
          .runWith(Sink.seq)
          .map(_ shouldBe Vector(
            ServerSentEvent("event1"),
            ServerSentEvent("event2")))
      }

      "parse events correctly when data arrives byte by byte with CRLF" in {
        val bytes = ByteString("data: hello\r\n\r\n")
        Source(bytes.map(ByteString(_)))
          .via(EventStreamParser(maxLineSize, maxEventSize))
          .runWith(Sink.seq)
          .map(_ shouldBe Vector(ServerSentEvent("hello")))
      }

      "reassemble a multi-event CRLF stream delivered in arbitrary chunks" in {
        val fullStream =
          "data: first\r\nevent: alpha\r\nid: 1\r\n\r\n" +
          "data: second\r\nid: 2\r\n\r\n"
        // split into 5-byte chunks
        val chunks = ByteString(fullStream).grouped(5).map(ByteString(_)).toVector
        Source(chunks)
          .via(EventStreamParser(maxLineSize, maxEventSize))
          .runWith(Sink.seq)
          .map(_ shouldBe Vector(
            ServerSentEvent("first", Some("alpha"), Some("1")),
            ServerSentEvent("second", None, Some("2"))))
      }
    }
  }
}
