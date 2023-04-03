/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.scaladsl.testkit

import scala.concurrent.duration._
import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.testkit._

case class RouteTestTimeout(duration: FiniteDuration)

object RouteTestTimeout {
  implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(1.second.dilated)
}
