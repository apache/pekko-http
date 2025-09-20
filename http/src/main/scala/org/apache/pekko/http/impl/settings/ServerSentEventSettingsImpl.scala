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
    override val oversizedLineStrategy: OversizedSseStrategy,
    override val oversizedEventStrategy: OversizedSseStrategy)
    extends pekko.http.scaladsl.settings.ServerSentEventSettings {
  require(maxLineSize >= 0, "max-line-size must be >= 0 (0 means unlimited)")
  require(maxEventSize >= 0, "max-event-size must be >= 0 (0 means unlimited)")
  require(
    maxLineSize == 0 || maxEventSize == 0 || maxEventSize > maxLineSize,
    "max-event-size must be greater than max-line-size, unless either is 0 (unlimited)")

  override def productPrefix: String = "ServerSentEventSettings"

  override def withOversizedLineStrategy(newValue: String): ServerSentEventSettingsImpl =
    copy(oversizedLineStrategy = OversizedSseStrategy.fromString(newValue))

  override def withOversizedLineStrategy(newValue: OversizedSseStrategy): ServerSentEventSettingsImpl =
    copy(oversizedLineStrategy = newValue)

  override def withOversizedEventStrategy(newValue: String): ServerSentEventSettingsImpl =
    copy(oversizedEventStrategy = OversizedSseStrategy.fromString(newValue))

  override def withOversizedEventStrategy(newValue: OversizedSseStrategy): ServerSentEventSettingsImpl =
    copy(oversizedEventStrategy = newValue)

  // For Java API compatibility
  def oversizedLineStrategyAsString: String = OversizedSseStrategy.toString(oversizedLineStrategy)
  def oversizedEventStrategyAsString: String = OversizedSseStrategy.toString(oversizedEventStrategy)

}

object ServerSentEventSettingsImpl extends SettingsCompanionImpl[ServerSentEventSettingsImpl]("pekko.http.sse") {

  // Binary compatibility: provide original 3-parameter constructor
  def apply(maxEventSize: Int, maxLineSize: Int, emitEmptyEvents: Boolean): ServerSentEventSettingsImpl =
    ServerSentEventSettingsImpl(maxEventSize, maxLineSize, emitEmptyEvents, OversizedSseStrategy.FailStream,
      OversizedSseStrategy.FailStream)

  def fromSubConfig(root: Config, c: Config) = ServerSentEventSettingsImpl(
    c.getInt("max-event-size"),
    c.getInt("max-line-size"),
    c.getBoolean("emit-empty-events"),
    OversizedSseStrategy.fromString(c.getString("oversized-line-handling")),
    OversizedSseStrategy.fromString(c.getString("oversized-event-handling")))
}
