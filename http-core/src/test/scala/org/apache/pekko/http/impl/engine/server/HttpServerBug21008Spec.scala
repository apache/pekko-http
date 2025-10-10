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

package org.apache.pekko.http.impl.engine.server

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.http.impl.util.PekkoSpecWithMaterializer
import pekko.http.scaladsl.model.HttpEntity.Chunked
import pekko.http.scaladsl.model.HttpMethods._
import pekko.http.scaladsl.model.{ ContentType, HttpRequest, HttpResponse }
import pekko.http.scaladsl.model.MediaTypes._
import pekko.stream.Materializer
import pekko.stream.testkit.Utils.{ TE, _ }
import pekko.testkit._
import org.scalatest.Inside

import scala.concurrent.Await
import scala.concurrent.duration._

class HttpServerBug21008Spec extends PekkoSpecWithMaterializer(
      """
   pekko.http.server.request-timeout = infinite
   pekko.test.filter-leeway=1s""") with Inside { spec =>
  "The HttpServer" should {

    "not cause internal graph failures when consuming a `100 Continue` entity triggers a failure" in assertAllStagesStopped(
      new HttpServerTestSetupBase {
        override implicit def system: ActorSystem = HttpServerBug21008Spec.this.system
        override implicit def materializer: Materializer = HttpServerBug21008Spec.this.materializer

        send("""POST / HTTP/1.1
             |Host: example.com
             |Expect: 100-continue
             |Transfer-Encoding: chunked
             |
             |""")
        inside(expectRequest()) {
          case HttpRequest(POST, _, _, Chunked(ContentType(`application/octet-stream`, None), data), _) =>
            val done = data.runForeach(_ => throw TE("failed on first chunk"))

            expectResponseWithWipedDate(
              """HTTP/1.1 100 Continue
              |Server: pekko-http/test
              |Date: XXXX
              |
              |""")
            send("""10
                 |0123456789ABCDEF
                 |0
                 |
                 |""")

            // Bug #21008 does not actually ever make the request fail instead it logs the exception and behaves
            // nicely so we need to check that the exception didn't get logged
            var sawException = false
            try {
              EventFilter[IllegalArgumentException](occurrences = 1).intercept {
                // make sure the failure has happened
                Await.ready(done, 10.seconds.dilated)
                // and then when the failure has happened/future completes, we push a reply
                responses.sendNext(HttpResponse(entity = "Yeah"))
              }

              // got such an error, that is bad,
              sawException = true
            } catch {
              case _: AssertionError => sawException = false
            }
            if (sawException) fail(
              "HttpServerBluePrint.ControllerStage: requirement failed: Cannot pull closed port (requestParsingIn)")

            // and the client should still get that ok
            expectResponseWithWipedDate(
              """HTTP/1.1 200 OK
              |Server: pekko-http/test
              |Date: XXXX
              |Connection: close
              |Content-Type: text/plain; charset=UTF-8
              |Content-Length: 4
              |
              |Yeah""")

        }

        netIn.sendComplete()
        netOut.expectComplete()

      })

  }
}
