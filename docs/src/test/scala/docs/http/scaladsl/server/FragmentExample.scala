/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.http.scaladsl.server

//#source-quote
import org.apache.pekko
import pekko.http.scaladsl.server.Directives._
import pekko.http.scaladsl.server.Route

object RouteFragment {
  val route: Route = pathEnd {
    get {
      complete("example")
    }
  }
}

object API {
  pathPrefix("version") {
    RouteFragment.route
  }
}
//#source-quote
