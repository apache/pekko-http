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
import pekko.actor.{ ActorContext, ActorSystem }
import pekko.event.LoggingAdapter
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
