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
import pekko.http.scaladsl.settings.OversizedSseStrategy
import com.typesafe.config.Config

@InternalApi
private[http] final case class ServerSentEventSettingsImpl(
    maxEventSize: Int,
    maxLineSize: Int,
    emitEmptyEvents: Boolean,
    override val oversizedStrategy: OversizedSseStrategy) extends pekko.http.scaladsl.settings.ServerSentEventSettings {
  require(maxLineSize >= 0, "max-line-size must be >= 0 (0 means unlimited)")
  require(maxEventSize >= 0, "max-event-size must be >= 0 (0 means unlimited)")
  require(
    maxLineSize == 0 || maxEventSize == 0 || maxEventSize > maxLineSize,
    "max-event-size must be greater than max-line-size, unless either is 0 (unlimited)")

  override def productPrefix: String = "ServerSentEventSettings"

  // Override methods to resolve conflict between Java and Scala return types
  override def withOversizedStrategy(newValue: String): ServerSentEventSettingsImpl =
    copy(oversizedStrategy = OversizedSseStrategy.fromString(newValue))

  override def withOversizedStrategy(newValue: OversizedSseStrategy): ServerSentEventSettingsImpl =
    copy(oversizedStrategy = newValue)

  // For Java API compatibility
  def oversizedStrategyAsString: String = OversizedSseStrategy.toString(oversizedStrategy)

}

object ServerSentEventSettingsImpl extends SettingsCompanionImpl[ServerSentEventSettingsImpl]("pekko.http.sse") {
  def fromSubConfig(root: Config, c: Config) = ServerSentEventSettingsImpl(
    c.getInt("max-event-size"),
    c.getInt("max-line-size"),
    c.getBoolean("emit-empty-events"),
    OversizedSseStrategy.fromString(c.getString("oversized-message-handling")))
}
