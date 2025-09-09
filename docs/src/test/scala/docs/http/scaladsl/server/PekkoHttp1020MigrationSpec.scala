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

package docs.http.scaladsl.server

import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.server.Route

class PekkoHttp1020MigrationSpec {
  import org.apache.pekko.http.scaladsl.server.Directives._

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
