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
import pekko.NotUsed
import pekko.http.scaladsl.model.HttpEntity
import pekko.http.scaladsl.model.MediaTypes.`text/event-stream`
import pekko.http.scaladsl.model.sse.ServerSentEvent
import pekko.http.scaladsl.settings.{ OversizedSseStrategy, ServerSentEventSettings }
import pekko.stream.scaladsl.{ Sink, Source }
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import scala.concurrent.ExecutionContext

final class EventStreamUnmarshallingSimpleSpec extends AsyncWordSpec with Matchers with BaseUnmarshallingSpec {
  implicit val ec: ExecutionContext = system.dispatcher
  implicit val mat: pekko.stream.Materializer = pekko.stream.SystemMaterializer(system).materializer

  "EventStreamUnmarshalling with oversized message handling" should {

    "fail the stream with FailStream strategy" in {
      val oversizedSseData = 
        s"""data: before
           |
           |data: ${"x" * 200}
           |
           |data: after
           |
           |""".stripMargin

      val entity = HttpEntity(`text/event-stream`, oversizedSseData)
      
      val settings = ServerSentEventSettings(system)
        .withLineLength(50)
        .withOversizedStrategy(OversizedSseStrategy.FailStream)

      val unmarshaller = EventStreamUnmarshalling.fromEventsStream(settings)

      recoverToExceptionIf[IllegalStateException] {
        Unmarshal(entity)
          .to[Source[ServerSentEvent, NotUsed]](unmarshaller, ec, mat)
          .flatMap(_.runWith(Sink.seq))
      }.map { exception =>
        exception.getMessage should include("SSE line size")
        exception.getMessage should include("exceeds max-line-size: 50")
      }
    }

    "skip oversized content with LogAndSkip strategy" in {
      val oversizedSseData = 
        s"""data: before
           |
           |data: ${"x" * 200}
           |
           |data: after
           |
           |""".stripMargin

      val entity = HttpEntity(`text/event-stream`, oversizedSseData)
      
      val settings = ServerSentEventSettings(system)
        .withLineLength(50)
        .withOversizedStrategy(OversizedSseStrategy.LogAndSkip)

      val unmarshaller = EventStreamUnmarshalling.fromEventsStream(settings)

      Unmarshal(entity)
        .to[Source[ServerSentEvent, NotUsed]](unmarshaller, ec, mat)
        .flatMap(_.runWith(Sink.seq))
        .map { result =>
          result shouldBe Vector(
            ServerSentEvent("before"),
            ServerSentEvent("after")
          )
        }
    }

    "truncate oversized content with Truncate strategy" in {
      val oversizedSseData = 
        s"""data: before
           |
           |data: ${"x" * 200}
           |
           |data: after
           |
           |""".stripMargin

      val entity = HttpEntity(`text/event-stream`, oversizedSseData)
      
      val settings = ServerSentEventSettings(system)
        .withLineLength(50)
        .withOversizedStrategy(OversizedSseStrategy.Truncate)

      val unmarshaller = EventStreamUnmarshalling.fromEventsStream(settings)

      Unmarshal(entity)
        .to[Source[ServerSentEvent, NotUsed]](unmarshaller, ec, mat)
        .flatMap(_.runWith(Sink.seq))
        .map { result =>
          result should have size 3
          result(0) shouldBe ServerSentEvent("before")
          result(1).data shouldBe "x" * 44 // truncated (50 - "data: ".length)
          result(2) shouldBe ServerSentEvent("after")
        }
    }

    "send oversized content to dead letters with DeadLetter strategy" in {
      val oversizedSseData = 
        s"""data: before
           |
           |data: ${"x" * 200}
           |
           |data: after
           |
           |""".stripMargin

      val entity = HttpEntity(`text/event-stream`, oversizedSseData)
      
      val settings = ServerSentEventSettings(system)
        .withLineLength(50)
        .withOversizedStrategy(OversizedSseStrategy.DeadLetter)

      val unmarshaller = EventStreamUnmarshalling.fromEventsStream(settings)

      Unmarshal(entity)
        .to[Source[ServerSentEvent, NotUsed]](unmarshaller, ec, mat)
        .flatMap(_.runWith(Sink.seq))
        .map { result =>
          result shouldBe Vector(
            ServerSentEvent("before"),
            ServerSentEvent("after")
          )
        }
    }
  }
}