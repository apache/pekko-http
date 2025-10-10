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

package docs.http.scaladsl.server

import org.apache.pekko
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.server.{ Directives, Route }
import docs.CompileOnlySpec

import scala.concurrent.Future
import org.scalatest.wordspec.AnyWordSpec

class BlockingInHttpExamplesSpec extends AnyWordSpec with CompileOnlySpec
    with Directives {

  compileOnlySpec {
    val system: ActorSystem = ActorSystem()

    // #blocking-example-in-default-dispatcher
    // BAD (due to blocking in Future, on default dispatcher)
    implicit val defaultDispatcher = system.dispatcher

    val routes: Route = post {
      complete {
        Future { // uses defaultDispatcher
          Thread.sleep(5000) // will block on default dispatcher,
          System.currentTimeMillis().toString // Starving the routing infrastructure
        }
      }
    }
    // #blocking-example-in-default-dispatcher
  }

  compileOnlySpec {
    val system: ActorSystem = ActorSystem()

    // #blocking-example-in-dedicated-dispatcher
    // GOOD (the blocking is now isolated onto a dedicated dispatcher):
    implicit val blockingDispatcher = system.dispatchers.lookup("my-blocking-dispatcher")

    val routes: Route = post {
      complete {
        Future { // uses the good "blocking dispatcher" that we configured,
          // instead of the default dispatcher to isolate the blocking.
          Thread.sleep(5000)
          System.currentTimeMillis().toString
        }
      }
    }
    // #blocking-example-in-dedicated-dispatcher
  }

}
