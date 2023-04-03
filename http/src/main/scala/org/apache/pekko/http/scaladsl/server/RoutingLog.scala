/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.scaladsl.server

import org.apache.pekko
import pekko.event.LoggingAdapter
import pekko.actor.{ ActorContext, ActorSystem }
import pekko.http.scaladsl.model.HttpRequest

trait RoutingLog {
  def log: LoggingAdapter
  def requestLog(request: HttpRequest): LoggingAdapter
}

object RoutingLog extends LowerPriorityRoutingLogImplicits {
  def apply(defaultLog: LoggingAdapter): RoutingLog =
    new RoutingLog {
      def log = defaultLog
      def requestLog(request: HttpRequest) = defaultLog
    }

  implicit def fromActorContext(implicit ac: ActorContext): RoutingLog = RoutingLog(ac.system.log)
}
sealed abstract class LowerPriorityRoutingLogImplicits {
  implicit def fromActorSystem(implicit system: ActorSystem): RoutingLog = RoutingLog(system.log)
}
