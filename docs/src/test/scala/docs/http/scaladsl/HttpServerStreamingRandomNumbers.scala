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
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.scaladsl.Behaviors
import pekko.stream.scaladsl._
import pekko.util.ByteString
import pekko.http.scaladsl.Http
import pekko.http.scaladsl.model.{ ContentTypes, HttpEntity }
import pekko.http.scaladsl.server.Directives._
import scala.util.Random
import scala.io.StdIn

object HttpServerStreamingRandomNumbers {

  def main(args: Array[String]): Unit = {

    implicit val system = ActorSystem(Behaviors.empty, "RandomNumbers")
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext = system.executionContext

    // streams are re-usable so we can define it here
    // and use it for every request
    val numbers = Source.fromIterator(() =>
      Iterator.continually(Random.nextInt()))

    val route =
      path("random") {
        get {
          complete(
            HttpEntity(
              ContentTypes.`text/plain(UTF-8)`,
              // transform each number to a chunk of bytes
              numbers.map(n => ByteString(s"$n\n"))))
        }
      }

    val bindingFuture = Http().newServerAt("localhost", 8080).bind(route)
    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}
