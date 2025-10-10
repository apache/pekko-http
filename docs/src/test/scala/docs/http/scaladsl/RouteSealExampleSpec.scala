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

package docs.http.scaladsl

import org.apache.pekko
import pekko.http.scaladsl.model.headers.RawHeader
import pekko.http.scaladsl.server.{ Directives, Route, RoutingSpec }
import docs.CompileOnlySpec

class RouteSealExampleSpec extends RoutingSpec with Directives with CompileOnlySpec {

  "seal route example" in compileOnlySpec {
    // #route-seal-example
    val route = respondWithHeader(RawHeader("special-header", "you always have this even in 404")) {
      Route.seal(
        get {
          pathSingleSlash {
            complete {
              "Captain on the bridge!"
            }
          }
        })
    }
    // #route-seal-example
  }

}
