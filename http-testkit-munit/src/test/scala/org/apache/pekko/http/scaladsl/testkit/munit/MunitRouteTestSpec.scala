/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.http.scaladsl.testkit.munit

import scala.concurrent.Await
import scala.concurrent.duration._

import Directives._
import HttpMethods._
import StatusCodes._
import munit.FailException

import org.apache.pekko
import pekko.actor.ActorRef
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.model.headers.{ `X-Forwarded-Proto`, RawHeader }
import pekko.http.scaladsl.server._
import pekko.pattern.ask
import pekko.stream.scaladsl.Source
import pekko.testkit._
import pekko.util.{ ByteString, Timeout }

class MunitRouteTestSpec extends MunitRouteTest {
  override def testConfigSource: String = "pekko.http.server.transparent-head-requests = on" // see test below

  test("The MunitRouteTest should support the most simple and direct route test") {
    Get() ~> complete(HttpResponse()) ~> { rr =>
      rr.awaitResult
      assertEquals(rr.response, HttpResponse())
    }
  }

  test("The MunitRouteTest should support a test using a directive and some checks") {
    val pinkHeader = RawHeader("Fancy", "pink")
    Get() ~> addHeader(pinkHeader) ~> {
      respondWithHeader(pinkHeader) {
        complete("abc")
      }
    } ~> check {
      assertEquals(status, OK)
      assertEquals(responseEntity, HttpEntity(ContentTypes.`text/plain(UTF-8)`, "abc"))
      assertEquals(header("Fancy"), Some(pinkHeader))
    }
  }

  test("The MunitRouteTest should support a test using ~!> and some checks") {
    // raw here, should have been parsed into modelled header when going through an actual server when using `~!>`
    val extraHeader = RawHeader("X-Forwarded-Proto", "abc")
    Get() ~!> {
      respondWithHeader(extraHeader) {
        complete("abc")
      }
    } ~> check {
      assertEquals(status, OK)
      assertEquals(responseEntity, HttpEntity(ContentTypes.`text/plain(UTF-8)`, "abc"))
      assertEquals(header[`X-Forwarded-Proto`].get, `X-Forwarded-Proto`("abc"))
    }
  }

  test("The MunitRouteTest should support test checking a route that returns infinite chunks") {
    Get() ~> {
      val infiniteSource =
        Source.unfold(0L)(acc => Some((acc + 1, acc)))
          .throttle(1, 20.millis)
          .map(i => ByteString(i.toString))
      complete(HttpEntity(ContentTypes.`application/octet-stream`, infiniteSource))
    } ~> check {
      assertEquals(status, OK)
      assertEquals(contentType, ContentTypes.`application/octet-stream`)
      val future = chunksStream.take(5).runFold(Vector.empty[Int])(_ :+ _.data.utf8String.toInt)
      assertEquals(Await.result(future, 5.seconds), (0 until 5).toVector)
    }
  }

  test("The MunitRouteTest should support proper rejection collection") {
    Post("/abc", "content") ~> {
      (get | put) {
        complete("naah")
      }
    } ~> check {
      assertEquals(rejections, List(MethodRejection(GET), MethodRejection(PUT)))
    }
  }

  test("The MunitRouteTest should support separation of route execution from checking") {
    val pinkHeader = RawHeader("Fancy", "pink")

    case object Command
    val service = TestProbe()
    val handler = TestProbe()
    implicit def serviceRef: ActorRef = service.ref
    implicit val askTimeout: Timeout = 1.second.dilated

    val result =
      Get() ~> pinkHeader ~> {
        respondWithHeader(pinkHeader) {
          complete(handler.ref.ask(Command).mapTo[String])
        }
      } ~> runRoute

    handler.expectMsg(Command)
    handler.reply("abc")

    check {
      assertEquals(status, OK)
      assertEquals(responseEntity, HttpEntity(ContentTypes.`text/plain(UTF-8)`, "abc"))
      assertEquals(header("Fancy"), Some(pinkHeader))
    }(result)
  }

  test("The MunitRouteTest should support failing the test inside the route") {
    val route = get {
      fail("BOOM")
    }

    intercept[FailException] {
      Get() ~> route
    }
  }

  test("The MunitRouteTest should support throwing an AssertionError inside the route") {
    val route = get {
      throw new AssertionError("test")
    }

    intercept[AssertionError] {
      Get() ~> route
    }
  }

  test("The MunitRouteTest should support internal server error") {
    val route = get {
      throw new RuntimeException("BOOM")
    }

    Get() ~> route ~> check {
      assertEquals(status, InternalServerError)
    }
  }

  test("The MunitRouteTest should fail if testing a HEAD request with ~> and `transparent-head-request = on`") {
    def runTest(): Unit = Head() ~> complete("Ok") ~> check {}

    interceptMessage[AssertionError](
      "`pekko.http.server.transparent-head-requests = on` not supported in RouteTest using `~>`. " +
      "Use `~!>` instead for a full-stack test, e.g. `req ~!> route ~> check {...}`") {
      runTest()
    }
  }
}
