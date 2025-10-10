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

package docs.http.scaladsl

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.http.scaladsl.Http
import pekko.http.scaladsl.model.headers.`Set-Cookie`
import pekko.http.scaladsl.model._

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future

class HttpClientCollectingHeaders {
  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val executionContext: ExecutionContextExecutor = system.dispatcher

    val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = "http://pekko.apache.org"))

    responseFuture.map {
      case response @ HttpResponse(StatusCodes.OK, _, _, _) =>
        val setCookies = response.headers[`Set-Cookie`]
        println(s"Cookies set by a server: $setCookies")
        response.discardEntityBytes()
      case _ => sys.error("something wrong")
    }
  }
}
