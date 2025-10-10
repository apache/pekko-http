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

package org.apache.pekko.http.scaladsl.testkit

import scala.concurrent.duration._

import org.specs2.mutable.Specification

import org.apache.pekko
import pekko.actor.ActorRef
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.model.HttpMethods._
import pekko.http.scaladsl.model.StatusCodes._
import pekko.http.scaladsl.model.headers.RawHeader
import pekko.http.scaladsl.server._
import pekko.http.scaladsl.server.Directives._
import pekko.pattern.ask
import pekko.testkit.TestProbe
import pekko.util.Timeout

class Specs2RouteTestSpec extends Specification with Specs2RouteTest {

  "The ScalatestRouteTest should support" should {

    "the most simple and direct route test" in {
      Get() ~> complete(HttpResponse()) ~> { rr => rr.awaitResult; rr.response } shouldEqual HttpResponse()
    }

    "a test using a directive and some checks" in {
      val pinkHeader = RawHeader("Fancy", "pink")
      Get() ~> addHeader(pinkHeader) ~> {
        respondWithHeader(pinkHeader) {
          complete("abc")
        }
      } ~> check {
        status shouldEqual OK
        responseEntity shouldEqual HttpEntity(ContentTypes.`text/plain(UTF-8)`, "abc")
        header("Fancy") shouldEqual Some(pinkHeader)
      }
    }

    "proper rejection collection" in {
      Post("/abc", "content") ~> {
        (get | put) {
          complete("naah")
        }
      } ~> check {
        rejections shouldEqual List(MethodRejection(GET), MethodRejection(PUT))
      }
    }

    "separation of route execution from checking" in {
      val pinkHeader = RawHeader("Fancy", "pink")

      case object Command
      val service = TestProbe()
      val handler = TestProbe()
      implicit def serviceRef: ActorRef = service.ref
      implicit val askTimeout: Timeout = 1.second

      val result =
        Get() ~> pinkHeader ~> {
          respondWithHeader(pinkHeader) {
            complete(handler.ref.ask(Command).mapTo[String])
          }
        } ~> runRoute

      handler.expectMsg(Command)
      handler.reply("abc")

      check {
        status shouldEqual OK
        responseEntity shouldEqual HttpEntity(ContentTypes.`text/plain(UTF-8)`, "abc")
        header("Fancy") shouldEqual Some(pinkHeader)
      }(result)
    }

    "failing the test inside the route" in {

      val route = get {
        failure("BOOM")
        complete(HttpResponse())
      }

      {
        Get() ~> route
      } must throwA[org.specs2.execute.FailureException]
    }

    "failing an assertion inside the route" in {
      val route = get {
        throw new AssertionError("test")
      }

      {
        Get() ~> route
      } must throwA[java.lang.AssertionError]
    }

    "internal server error" in {

      val route = get {
        throw new RuntimeException("BOOM")
      }

      Get().~>(route).~>(check {
        status shouldEqual InternalServerError
      })
    }
  }
}
