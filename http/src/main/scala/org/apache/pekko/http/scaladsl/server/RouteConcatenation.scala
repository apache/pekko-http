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

package org.apache.pekko.http.scaladsl.server

import org.apache.pekko
import pekko.http.scaladsl.server.Directives.reject
import pekko.http.scaladsl.util.FastFuture
import pekko.http.scaladsl.util.FastFuture._

/**
 * @groupname concat Route concatenation
 * @groupprio concat 300
 */
trait RouteConcatenation {

  /**
   * @group concat
   */
  implicit def _enhanceRouteWithConcatenation(route: Route): RouteConcatenation.RouteWithConcatenation =
    new RouteConcatenation.RouteWithConcatenation(route: Route)

  /**
   * Tries the supplied routes in sequence, returning the result of the first route that doesn't reject the request.
   * This is an alternative to direct usage of the infix ~ operator. The ~ can be prone to programmer error, because if
   * it is omitted, the program will still be syntactically correct, but will not actually attempt to match multiple
   * routes, as intended.
   *
   * @param routes subroutes to concatenate
   * @return the concatenated route
   */
  def concat(routes: Route*): Route = routes.foldLeft[Route](reject)(_ ~ _)
}

object RouteConcatenation extends RouteConcatenation {

  class RouteWithConcatenation(route: Route) {

    /**
     * Returns a Route that chains two Routes. If the first Route rejects the request the second route is given a
     * chance to act upon the request.
     */
    def ~(other: Route): Route = { ctx =>
      import ctx.executionContext
      route(ctx).fast.flatMap {
        case x: RouteResult.Complete => FastFuture.successful(x)
        case RouteResult.Rejected(outerRejections) =>
          other(ctx).fast.map {
            case x: RouteResult.Complete               => x
            case RouteResult.Rejected(innerRejections) => RouteResult.Rejected(outerRejections ++ innerRejections)
          }
      }
    }
  }
}
