/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.http.scaladsl.server

import org.apache.pekko
import pekko.actor.CoordinatedShutdown
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.scaladsl.Behaviors
import pekko.http.scaladsl.Http
import docs.CompileOnlySpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent._
import scala.concurrent.duration._

class ServerShutdownExampleSpec extends AnyWordSpec with Matchers
    with CompileOnlySpec {

  "mount coordinated shutdown" in compileOnlySpec {
    import org.apache.pekko.http.scaladsl.server.Route

    implicit val system = ActorSystem(Behaviors.empty, "http-server")
    implicit val ec: ExecutionContext = system.executionContext

    val routes: Route = ???

    // #suggested
    val bindingFuture = Http().newServerAt("localhost", 8080).bind(routes)
      .map(_.addToCoordinatedShutdown(hardTerminationDeadline = 10.seconds))
    // #suggested

    // #shutdown
    // shut down with `ActorSystemTerminateReason`
    system.terminate()

    // or define a specific reason
    case object UserInitiatedShutdown extends CoordinatedShutdown.Reason

    CoordinatedShutdown(system).run(UserInitiatedShutdown)
    // #shutdown
  }

}
