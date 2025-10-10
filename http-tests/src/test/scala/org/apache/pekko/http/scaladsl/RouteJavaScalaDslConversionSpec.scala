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

package org.apache.pekko.http.scaladsl

import java.util.function.Supplier

import org.apache.pekko
import pekko.http.javadsl.server.Route

import org.scalatest.wordspec.AnyWordSpec

class RouteJavaScalaDslConversionSpec extends AnyWordSpec {

  "Routes" must {

    "convert JavaDSL to ScalaDSL" in {
      // #java-to-scala
      import org.apache.pekko

      val javaRoute =
        pekko.http.javadsl.server.Directives.get(new Supplier[pekko.http.javadsl.server.Route] {
          override def get(): Route = pekko.http.javadsl.server.Directives.complete("ok")
        })

      // Remember that Route in Scala is just a type alias:
      //   type Route = RequestContext => Future[RouteResult]
      val scalaRoute: pekko.http.scaladsl.server.Route = javaRoute.asScala
      // #java-to-scala
    }

    "convert ScalaDSL to JavaDSL" in {
      // #scala-to-java
      import org.apache.pekko

      val scalaRoute: pekko.http.scaladsl.server.Route =
        pekko.http.scaladsl.server.Directives.get {
          pekko.http.scaladsl.server.Directives.complete("OK")
        }

      val javaRoute: pekko.http.javadsl.server.Route =
        pekko.http.javadsl.server.directives.RouteAdapter.asJava(scalaRoute)
      // #scala-to-java
    }
  }
}
