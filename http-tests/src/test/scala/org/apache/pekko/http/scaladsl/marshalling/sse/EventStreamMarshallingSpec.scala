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
package marshalling
package sse

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

import org.apache.pekko
import pekko.http.scaladsl.model.MediaTypes.`text/event-stream`
import pekko.http.scaladsl.model.sse.ServerSentEvent
import pekko.http.scaladsl.server.Directives
import pekko.http.scaladsl.testkit.RouteTest
import pekko.http.scaladsl.testkit.TestFrameworkInterface.Scalatest
import pekko.stream.scaladsl.{ Sink, Source }

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class EventStreamMarshallingSpec extends AnyWordSpec with Matchers with RouteTest with Scalatest {
  import Directives._

  import pekko.http.scaladsl.marshalling.sse.EventStreamMarshalling._

  "A source of ServerSentEvents" should {
    "be marshallable to a HTTP response" in {
      val events = 1.to(666).map(n => ServerSentEvent(n.toString))
      val route = complete(Source(events))
      Get() ~> route ~> check {
        mediaType shouldBe `text/event-stream`
        Await.result(responseEntity.dataBytes.runWith(Sink.seq), 3.seconds) shouldBe events.map(_.encode)
      }
    }
  }
}
