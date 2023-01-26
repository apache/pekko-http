/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.scaladsl.server

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.http.scaladsl.model.{ HttpRequest, HttpResponse, StatusCodes }
import pekko.http.scaladsl.server.Directives.complete
import pekko.stream.{ Materializer, SystemMaterializer }
import pekko.stream.scaladsl.Flow
import pekko.testkit.TestKit
import scala.annotation.nowarn
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

@nowarn("msg=never used")
class RouteResultSpec extends AnyWordSpec with Matchers {
  "RouteResult" should {
    val route: Route = complete(StatusCodes.OK)
    "provide a conversion from Route to Flow when an ActorSystem is available" in {
      implicit val system = ActorSystem("RouteResultSpec1")

      val flow: Flow[HttpRequest, HttpResponse, Any] = route

      TestKit.shutdownActorSystem(system, verifySystemShutdown = true)
    }

    "provide a conversion from Route to Flow when a Materializer is implicitly available" in {
      val system = ActorSystem("RouteResultSpec1")
      implicit val materializer: Materializer = SystemMaterializer(system).materializer

      @nowarn("msg=deprecated")
      val flow: Flow[HttpRequest, HttpResponse, Any] = route

      TestKit.shutdownActorSystem(system, verifySystemShutdown = true)
    }

    "provide a conversion from Route to Flow when both a Materializer and a system are implicitly  available" in {
      implicit val system = ActorSystem("RouteResultSpec1")
      // implemented with ??? so it produces an error when the materializer is selected over the system
      implicit def materializer: Materializer = ???

      val flow: Flow[HttpRequest, HttpResponse, Any] = route

      TestKit.shutdownActorSystem(system, verifySystemShutdown = true)
    }
  }

}
