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

import org.apache.pekko.http.scaladsl.server.util.Tuple

/**
 * A Route that can be implicitly converted into a Directive (fitting any signature).
 */
abstract class StandardRoute extends Route {
  def toDirective[L: Tuple]: Directive[L] = StandardRoute.toDirective(this)
}

object StandardRoute {
  def apply(route: Route): StandardRoute = route match {
    case x: StandardRoute => x
    case x                => new StandardRoute { def apply(ctx: RequestContext) = x(ctx) }
  }

  /**
   * Converts the StandardRoute into a directive that never passes the request to its inner route
   * (and always returns its underlying route).
   */
  implicit def toDirective[L: Tuple](route: StandardRoute): Directive[L] =
    Directive[L] { _ => route }
}
