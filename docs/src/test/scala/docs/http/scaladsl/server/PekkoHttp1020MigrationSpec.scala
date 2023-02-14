/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.http.scaladsl.server

import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.stream.{ ActorMaterializer, Materializer }
import scala.annotation.nowarn

@nowarn("msg=is deprecated")
class PekkoHttp1020MigrationSpec {
  import org.apache.pekko.http.scaladsl.server.Directives._

  {
    // #old-binding
    // only worked with classic actor system
    implicit val system = org.apache.pekko.actor.ActorSystem("TheSystem")
    implicit val mat: Materializer = ActorMaterializer()
    val route: Route =
      get {
        complete("Hello world")
      }
    Http().bindAndHandle(route, "localhost", 8080)
    // #old-binding
  }

  {
    // #new-binding
    // works with both classic and typed ActorSystem
    implicit val system = org.apache.pekko.actor.typed.ActorSystem(Behaviors.empty, "TheSystem")
    // or
    // implicit val system = org.apache.pekko.actor.ActorSystem("TheSystem")

    // materializer not needed any more

    val route: Route =
      get {
        complete("Hello world")
      }
    Http().newServerAt("localhost", 8080).bind(route)
    // #new-binding
  }
}
