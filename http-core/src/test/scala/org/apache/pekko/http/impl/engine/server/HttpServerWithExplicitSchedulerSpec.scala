/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.impl.engine.server

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.http.impl.util._
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.model.headers.{ `Timeout-Access`, Connection }
import pekko.stream.Materializer
import pekko.stream.testkit.Utils.assertAllStagesStopped
import pekko.testkit.ExplicitlyTriggeredScheduler
import org.scalatest.Inside

import scala.concurrent.duration._

/** Tests similar to HttpServerSpec that need ExplicitlyTriggeredScheduler */
class HttpServerWithExplicitSchedulerSpec extends PekkoSpecWithMaterializer(
      """
     pekko.http.server.log-unencrypted-network-bytes = 100
     pekko.http.server.request-timeout = infinite
     pekko.scheduler.implementation = "org.apache.pekko.testkit.ExplicitlyTriggeredScheduler"
  """) with Inside { spec =>
  "The server implementation" should {
    "support request timeouts".which {

      "are defined via the config" in assertAllStagesStopped(new RequestTimeoutTestSetup(10.millis) {
        send("GET / HTTP/1.1\r\nHost: example.com\r\n\r\n")
        expectRequest().header[`Timeout-Access`] shouldBe defined

        scheduler.timePasses(20.millis)
        expectResponseWithWipedDate(
          """HTTP/1.1 503 Service Unavailable
            |Server: pekko-http/test
            |Date: XXXX
            |Content-Type: text/plain; charset=UTF-8
            |Content-Length: 105
            |
            |The server was not able to produce a timely response to your request.
            |Please try again in a short while!""")

        // FIXME: it seems the request side of the user handler is just completed
        // and the response side is still working on a response.
        // It would be better if the request side would be failed so that error could be propagated
        // see #3072
        requests.expectComplete()
        responses.sendError(new RuntimeException)

        netOut.expectComplete()
        netIn.sendComplete()
      })

      "are programmatically increased (not expiring)" in assertAllStagesStopped(new RequestTimeoutTestSetup(50.millis) {
        send("GET / HTTP/1.1\r\nHost: example.com\r\n\r\n")
        expectRequest().header[`Timeout-Access`].foreach(_.timeoutAccess.updateTimeout(250.millis))
        netOut.expectNoBytes()
        responses.sendNext(HttpResponse(headers = Connection("close") :: Nil))
        expectResponseWithWipedDate(
          """HTTP/1.1 200 OK
            |Server: pekko-http/test
            |Date: XXXX
            |Connection: close
            |Content-Length: 0
            |
            |""")

        // FIXME: why is the network handler only completed after the network?
        // requests.expectComplete()

        netOut.expectComplete()
        netIn.sendComplete()

        requests.expectComplete()
        responses.sendComplete()
      })

      "are programmatically increased (expiring)" in assertAllStagesStopped(new RequestTimeoutTestSetup(50.millis) {
        send("GET / HTTP/1.1\r\nHost: example.com\r\n\r\n")

        scheduler.timePasses(25.millis)
        expectRequest().header[`Timeout-Access`].foreach(_.timeoutAccess.updateTimeout(250.millis))

        scheduler.timePasses(150.millis)
        netOut.expectNoBytes(Duration.Zero)

        scheduler.timePasses(100.millis)
        expectResponseWithWipedDate(
          """HTTP/1.1 503 Service Unavailable
            |Server: pekko-http/test
            |Date: XXXX
            |Content-Type: text/plain; charset=UTF-8
            |Content-Length: 105
            |
            |The server was not able to produce a timely response to your request.
            |Please try again in a short while!""")

        // FIXME: it seems the request side of the user handler is just completed
        // and the response side is still working on a response.
        // It would be better if the request side would be failed so that error could be propagated
        // see #3072
        requests.expectComplete()
        responses.sendError(new RuntimeException)

        netOut.expectComplete()
        netIn.sendComplete()
      })

      "are programmatically decreased" in assertAllStagesStopped(new RequestTimeoutTestSetup(250.millis) {
        send("GET / HTTP/1.1\r\nHost: example.com\r\n\r\n")
        expectRequest().header[`Timeout-Access`].foreach(_.timeoutAccess.updateTimeout(50.millis))

        scheduler.timePasses(40.millis)
        netOut.expectNoBytes(Duration.Zero)

        scheduler.timePasses(10.millis)
        expectResponseWithWipedDate(
          """HTTP/1.1 503 Service Unavailable
            |Server: pekko-http/test
            |Date: XXXX
            |Content-Type: text/plain; charset=UTF-8
            |Content-Length: 105
            |
            |The server was not able to produce a timely response to your request.
            |Please try again in a short while!""")

        // FIXME: it seems the request side of the user handler is just completed
        // and the response side is still working on a response.
        // It would be better if the request side would be failed so that error could be propagated
        // see #3072
        requests.expectComplete()
        responses.sendError(new RuntimeException)

        netOut.expectComplete()
        netIn.sendComplete()
      })

      "have a programmatically set timeout handler" in assertAllStagesStopped(new RequestTimeoutTestSetup(400.millis) {
        send("GET / HTTP/1.1\r\nHost: example.com\r\n\r\n")
        val timeoutResponse = HttpResponse(StatusCodes.InternalServerError, entity = "OOPS!")
        expectRequest().header[`Timeout-Access`].foreach(
          _.timeoutAccess.updateHandler((_: HttpRequest) => timeoutResponse))

        scheduler.timePasses(500.millis)
        expectResponseWithWipedDate(
          """HTTP/1.1 500 Internal Server Error
            |Server: pekko-http/test
            |Date: XXXX
            |Content-Type: text/plain; charset=UTF-8
            |Content-Length: 5
            |
            |OOPS!""")

        // FIXME: it seems the request side of the user handler is just completed
        // and the response side is still working on a response.
        // It would be better if the request side would be failed so that error could be propagated
        // see #3072
        requests.expectComplete()
        responses.sendError(new RuntimeException)

        netOut.expectComplete()
        netIn.sendComplete()
      })
    }
  }

  class TestSetup(maxContentLength: Int = -1) extends HttpServerTestSetupBase {
    implicit def system: ActorSystem = spec.system
    implicit def materializer: Materializer = spec.materializer
    lazy val scheduler = spec.system.scheduler.asInstanceOf[ExplicitlyTriggeredScheduler]

    override def settings = {
      val s = super.settings
      if (maxContentLength < 0) s
      else s.withParserSettings(s.parserSettings.withMaxContentLength(maxContentLength))
    }
  }
  class RequestTimeoutTestSetup(requestTimeout: FiniteDuration) extends TestSetup {
    override def settings = {
      val s = super.settings
      s.withTimeouts(s.timeouts.withRequestTimeout(requestTimeout))
    }
  }
}
