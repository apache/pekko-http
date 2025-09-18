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

package org.apache.pekko.http.scaladsl.settings

import org.apache.pekko
import pekko.annotation.{ ApiMayChange, DoNotInherit }
import pekko.http.impl.settings.ServerSentEventSettingsImpl
import com.typesafe.config.Config

/**
 * Public API but not intended for subclassing
 *
 * Options that are in "preview" or "early access" mode.
 * These options may change and/or be removed within patch releases
 * without early notice (e.g. by moving them into a stable supported place).
 */
@ApiMayChange @DoNotInherit
abstract class ServerSentEventSettings private[pekko] () extends pekko.http.javadsl.settings.ServerSentEventSettings {
  self: ServerSentEventSettingsImpl =>

  override def maxEventSize: Int
  override def maxLineSize: Int

  /**
   * @since 1.3.0
   */
  override def oversizedLineStrategy: OversizedSseStrategy = OversizedSseStrategy.FailStream

  /**
   * @since 1.3.0
   */
  override def oversizedEventStrategy: OversizedSseStrategy = OversizedSseStrategy.FailStream

  override def withMaxEventSize(newValue: Int): ServerSentEventSettings = self.copy(maxEventSize = newValue)
  override def withLineLength(newValue: Int): ServerSentEventSettings = self.copy(maxLineSize = newValue)
  override def withEmitEmptyEvents(newValue: Boolean): ServerSentEventSettings = self.copy(emitEmptyEvents = newValue)

  /**
   * @since 1.3.0
   */
  override def withOversizedLineStrategy(newValue: String): ServerSentEventSettings =
    self.copy(oversizedLineStrategy = OversizedSseStrategy.fromString(newValue))

  /**
   * @since 1.3.0
   */
  def withOversizedLineStrategy(newValue: OversizedSseStrategy): ServerSentEventSettings =
    self.copy(oversizedLineStrategy = newValue)

  /**
   * @since 1.3.0
   */
  override def withOversizedEventStrategy(newValue: String): ServerSentEventSettings =
    self.copy(oversizedEventStrategy = OversizedSseStrategy.fromString(newValue))

  /**
   * @since 1.3.0
   */
  def withOversizedEventStrategy(newValue: OversizedSseStrategy): ServerSentEventSettings =
    self.copy(oversizedEventStrategy = newValue)
}

object ServerSentEventSettings extends SettingsCompanion[ServerSentEventSettings] {
  def fromSubConfig(root: Config, c: Config): ServerSentEventSettings =
    ServerSentEventSettingsImpl.fromSubConfig(root, c)
  override def apply(config: Config): ServerSentEventSettings = ServerSentEventSettingsImpl(config)
  override def apply(configOverrides: String): ServerSentEventSettings = ServerSentEventSettingsImpl(configOverrides)
}
