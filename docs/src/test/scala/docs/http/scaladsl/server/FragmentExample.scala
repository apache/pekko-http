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
