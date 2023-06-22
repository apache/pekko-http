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

package org.apache.pekko.http.impl.settings

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.http.impl.util.SettingsCompanionImpl
import com.typesafe.config.Config

@InternalApi
private[http] final case class ServerSentEventSettingsImpl(
    maxEventSize: Int,
    maxLineSize: Int,
    emitEmptyEvents: Boolean) extends pekko.http.scaladsl.settings.ServerSentEventSettings {
  require(maxLineSize > 0, "max-line-size must be greater than 0")
  require(maxEventSize > maxLineSize, "max-event-size must be greater than max-line-size")

  override def productPrefix: String = "ServerSentEventSettings"

}

object ServerSentEventSettingsImpl extends SettingsCompanionImpl[ServerSentEventSettingsImpl]("pekko.http.sse") {
  def fromSubConfig(root: Config, c: Config) = ServerSentEventSettingsImpl(
    c.getInt("max-event-size"),
    c.getInt("max-line-size"),
    c.getBoolean("emit-empty-events"))
}
