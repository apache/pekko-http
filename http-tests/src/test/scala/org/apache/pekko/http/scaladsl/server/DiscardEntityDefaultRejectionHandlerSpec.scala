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

package org.apache.pekko.http.scaladsl.server

import org.apache.pekko
import pekko.http.scaladsl.model.ContentTypes.`text/plain(UTF-8)`
import pekko.http.scaladsl.model.HttpEntity
import pekko.http.scaladsl.model.StatusCodes.NotFound
import pekko.stream.scaladsl.Source
import pekko.util.ByteString

import org.scalatest.concurrent.Eventually._
import org.scalatest.concurrent.ScalaFutures

class DiscardEntityDefaultRejectionHandlerSpec extends RoutingSpec with ScalaFutures {

  private val route = path("foo") {
    complete("bar")
  }

  private val numElems = 1000
  @volatile
  private var elementsEmitted = 0
  private def gimmeElement(): ByteString = {
    elementsEmitted = elementsEmitted + 1
    ByteString("Foo")
  }

  private val ThousandElements: Stream[ByteString] = Stream.continually(gimmeElement()).take(numElems)
  private val RequestToNotHandled = Get("/bar", HttpEntity(`text/plain(UTF-8)`, Source[ByteString](ThousandElements)))

  "Default RejectionHandler" should {
    "rejectEntity by default" in {
      RequestToNotHandled ~> Route.seal(route) ~> check {
        status shouldBe NotFound
        eventually {
          elementsEmitted shouldBe numElems
        }
      }
    }
  }

}
