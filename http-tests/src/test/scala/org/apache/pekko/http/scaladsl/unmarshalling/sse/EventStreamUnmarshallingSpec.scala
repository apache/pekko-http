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
import pekko.NotUsed
import pekko.http.scaladsl.model.HttpEntity
import pekko.http.scaladsl.model.MediaTypes.`text/event-stream`
import pekko.http.scaladsl.model.sse.ServerSentEvent
import pekko.http.scaladsl.settings.ServerSentEventSettings
import pekko.stream.scaladsl.{ Sink, Source }

import java.util.{ List => JList }
import scala.collection.immutable.Seq
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

object EventStreamUnmarshallingSpec {

  val events: Seq[ServerSentEvent] =
    1.to(666).map(n => ServerSentEvent(n.toString))

  // Also used by EventStreamUnmarshallingTest.java
  val eventsAsJava: JList[javadsl.model.sse.ServerSentEvent] = {
    import scala.jdk.CollectionConverters._
    events.map(_.asInstanceOf[javadsl.model.sse.ServerSentEvent]).asJava
  }

  // Also used by EventStreamUnmarshallingTest.java
  val entity: HttpEntity = streamEntity(events)

  def streamEntity(events: Seq[ServerSentEvent]): HttpEntity =
    HttpEntity(`text/event-stream`, Source(events).map(_.encode))
}

final class EventStreamUnmarshallingSpec extends AsyncWordSpec with Matchers with BaseUnmarshallingSpec {
  import EventStreamUnmarshallingSpec._
  import pekko.http.scaladsl.unmarshalling.sse.EventStreamUnmarshalling._

  "A HTTP entity with media-type text/event-stream" should {
    "be unmarshallable to an EventStream" in {
      Unmarshal(entity)
        .to[Source[ServerSentEvent, NotUsed]]
        .flatMap(_.runWith(Sink.seq))
        .map(_ shouldBe events)
    }
    "not receive any empty events by default" in {
      Unmarshal(streamEntity(ServerSentEvent.heartbeat +: events :+ ServerSentEvent.heartbeat))
        .to[Source[ServerSentEvent, NotUsed]]
        .flatMap(_.runWith(Sink.seq))
        .map(_ shouldBe events)
    }
    "receive empty events when enabled" in {
      val allEvents = ServerSentEvent.heartbeat +: events :+ ServerSentEvent.heartbeat
      implicit val fromEventsStream =
        EventStreamUnmarshalling.fromEventsStream(ServerSentEventSettings(system).withEmitEmptyEvents(true))
      Unmarshal(streamEntity(allEvents))
        .to[Source[ServerSentEvent, NotUsed]]
        .flatMap(_.runWith(Sink.seq))
        .map(_ shouldBe allEvents)
    }
  }
}
