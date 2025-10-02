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

import scala.concurrent.Future

import org.apache.pekko
import pekko.http.impl.util.WithLogCapturing
import pekko.http.scaladsl.model.ContentTypes.`text/plain(UTF-8)`
import pekko.http.scaladsl.model.HttpEntity
import pekko.http.scaladsl.model.StatusCodes.InternalServerError
import pekko.stream.scaladsl.Source
import pekko.util.ByteString

import org.scalatest.concurrent.Eventually._
import org.scalatest.concurrent.ScalaFutures

class DiscardEntityDefaultExceptionHandlerSpec extends RoutingSpec with ScalaFutures with WithLogCapturing {

  private val route = concat(
    path("crash") {
      throw new RuntimeException("BOOM!")
    },
    path("crashAfterConsuming") {
      extractRequestEntity { entity =>
        val future: Future[String] =
          entity.dataBytes.runFold(ByteString.empty)(_ ++ _).map(_ => throw new RuntimeException("KABOOM!"))
        complete(future)
      }
    })

  trait Fixture {
    @volatile
    var streamConsumed = false
    val thousandElements: LazyList[ByteString] = LazyList.continually(ByteString("foo")).take(999).lazyAppendedAll {
      streamConsumed = true
      Seq(ByteString("end"))
    }

  }

  "Default ExceptionHandler" should {
    "rejectEntity by default" in new Fixture {
      streamConsumed shouldBe false
      Get("/crash", HttpEntity(`text/plain(UTF-8)`, Source[ByteString](thousandElements))) ~> Route.seal(
        route) ~> check {
        status shouldBe InternalServerError
        eventually { // Stream will be eventually consumed, once all the stream bytes are successfully discarded
          streamConsumed shouldBe true
        }
      }
    }

    "rejectEntity by default even if consumed already" in new Fixture {
      streamConsumed shouldBe false
      Get("/crashAfterConsuming", HttpEntity(`text/plain(UTF-8)`, Source[ByteString](thousandElements))) ~> Route.seal(
        route) ~> check {
        // Stream should be consumed immediately after the request finishes
        streamConsumed shouldBe true
        status shouldBe InternalServerError
      }
    }
  }

}
