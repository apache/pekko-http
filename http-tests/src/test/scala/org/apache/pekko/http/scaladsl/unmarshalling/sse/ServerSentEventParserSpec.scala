/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
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

final class ServerSentEventParserSpec extends AsyncWordSpec with Matchers with BaseUnmarshallingSpec {

  "A ServerSentEventParser" should {
    "parse ServerSentEvents correctly (and emit empty events)" in {
      val input = """|data: event 1 line 1
                     |data:event 1 line 2
                     |
                     |data: event 2
                     |:This is a comment and must be ignored
                     |ignore: this is an ignored field
                     |event: Only the last event should be considered
                     |event: event 2 event
                     |id: Only the last id should be considered
                     |id: 42
                     |retry: 123
                     |retry: 512
                     |
                     |
                     |event
                     |:no data means event gets ignored
                     |
                     |data:
                     |
                     |data
                     |:empty data is considered an event
                     |
                     |data:
                     |:empty data means event gets ignored
                     |
                     |data: event 3
                     |id
                     |event
                     |retry
                     |:empty id is possible
                     |:empty event is ignored
                     |:empty retry is ignored
                     |
                     |data: event 4
                     |event:
                     |retry: not numeric
                     |:empty event is ignored
                     |:invalid retry is ignored
                     |
                     |data: incomplete
                     |""".stripMargin
      Source(input.split(f"%n").toVector)
        .via(new ServerSentEventParser(1048576, emitEmptyEvents = false))
        .runWith(Sink.seq)
        .map(
          _ shouldBe Vector(
            ServerSentEvent("event 1 line 1\nevent 1 line 2"),
            ServerSentEvent("event 2", Some("event 2 event"), Some("42"), Some(512)),
            ServerSentEvent("event 3", None, Some("")),
            ServerSentEvent("event 4")))
    }
    "parse ServerSentEvents correctly (and pass empty events)" in {
      val input = """|data: event 1 line 1
                     |data:event 1 line 2
                     |
                     |data: event 2
                     |:This is a comment and must be ignored
                     |ignore: this is an ignored field
                     |event: Only the last event should be considered
                     |event: event 2 event
                     |id: Only the last id should be considered
                     |id: 42
                     |retry: 123
                     |retry: 512
                     |
                     |
                     |event
                     |:no data means event gets ignored
                     |
                     |data:
                     |
                     |data
                     |:empty data is considered an event
                     |
                     |data:
                     |:empty data means event gets ignored
                     |
                     |data: event 3
                     |id
                     |event
                     |retry
                     |:empty id is possible
                     |:empty event is ignored
                     |:empty retry is ignored
                     |
                     |data: event 4
                     |event:
                     |retry: not numeric
                     |:empty event is ignored
                     |:invalid retry is ignored
                     |
                     |data: incomplete
                     |""".stripMargin
      Source(input.split(f"%n").toVector)
        .via(new ServerSentEventParser(1048576, emitEmptyEvents = true))
        .runWith(Sink.seq)
        .map(
          _ shouldBe Vector(
            ServerSentEvent("event 1 line 1\nevent 1 line 2"),
            ServerSentEvent("event 2", Some("event 2 event"), Some("42"), Some(512)),
            ServerSentEvent.heartbeat,
            ServerSentEvent.heartbeat,
            ServerSentEvent("event 3", None, Some("")),
            ServerSentEvent("event 4")))
    }
    "parse ServerSentEvents with CRLF line endings" in {
      Source(
        Vector(
          "data: event 1\r",
          "data: event 1 line 2\r",
          "\r",
          "data: event 2\r",
          "event: my-event\r",
          "id: 42\r",
          "\r",
          "\r"))
        .via(new ServerSentEventParser(1048576, emitEmptyEvents = false))
        .runWith(Sink.seq)
        .map(
          _ shouldBe Vector(
            ServerSentEvent("event 1\nevent 1 line 2"),
            ServerSentEvent("event 2", Some("my-event"), Some("42"))))
    }
    "parse ServerSentEvents from a CRLF byte stream" in {
      val input = ByteString("data: event 1\r\ndata: event 1 line 2\r\n\r\ndata: event 2\r\nevent: my-event\r\nid: 42\r\n\r\n")
      Source
        .single(input)
        .via(EventStreamParser(1048576, 1048576, emitEmptyEvents = false))
        .runWith(Sink.seq)
        .map(
          _ shouldBe Vector(
            ServerSentEvent("event 1\nevent 1 line 2"),
            ServerSentEvent("event 2", Some("my-event"), Some("42"))))
    }
  }
}
