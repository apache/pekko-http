/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.settings

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.annotation.{ ApiMayChange, DoNotInherit }
import pekko.http.impl.settings.ServerSentEventSettingsImpl
import pekko.http.scaladsl.settings.{ OversizedSseStrategy => ScalaOversizedSseStrategy }

import com.typesafe.config.Config

/**
 * Public API but not intended for subclassing
 *
 * Options that are in "preview" or "early access" mode.
 * These options may change and/or be removed within patch releases
 * without early notice (e.g. by moving them into a stable supported place).
 */
@ApiMayChange @DoNotInherit
abstract class ServerSentEventSettings private[pekko] () { self: ServerSentEventSettingsImpl =>

  /**
   * The maximum size for parsing server-sent events
   */
  def maxEventSize: Int

  /**
   * The maximum size for parsing lines of a server-sent event
   */
  def maxLineSize: Int

  /**
   * Should events with empty data field be passed to the application.
   */
  def emitEmptyEvents: Boolean

  /**
   * How to handle lines that exceed max-line-size limit.
   * Valid options: "fail-stream", "log-and-skip", "truncate", "dead-letter"
   * @since 1.3.0
   */
  def getOversizedLineStrategy: String = self.oversizedLineStrategyAsString

  /**
   * How to handle lines that exceed max-line-size limit.
   * Returns the strategy as a Java enum.
   * @since 1.3.0
   */
  def getOversizedLineStrategyEnum: org.apache.pekko.http.javadsl.settings.OversizedSseStrategy =
    org.apache.pekko.http.javadsl.settings.OversizedSseStrategy.fromScala(self.oversizedLineStrategy)

  /**
   * How to handle events that exceed max-event-size limit.
   * Valid options: "fail-stream", "log-and-skip", "truncate", "dead-letter"
   * @since 1.3.0
   */
  def getOversizedEventStrategy: String = self.oversizedEventStrategyAsString

  /**
   * How to handle events that exceed max-event-size limit.
   * Returns the strategy as a Java enum.
   * @since 1.3.0
   */
  def getOversizedEventStrategyEnum: org.apache.pekko.http.javadsl.settings.OversizedSseStrategy =
    org.apache.pekko.http.javadsl.settings.OversizedSseStrategy.fromScala(self.oversizedEventStrategy)

  def withMaxEventSize(newValue: Int): ServerSentEventSettings = self.copy(maxEventSize = newValue)
  def withLineLength(newValue: Int): ServerSentEventSettings = self.copy(maxLineSize = newValue)
  def withEmitEmptyEvents(newValue: Boolean): ServerSentEventSettings = self.copy(emitEmptyEvents = newValue)

  /**
   * @since 1.3.0
   */
  def withOversizedLineStrategy(newValue: String): ServerSentEventSettings =
    self.copy(oversizedLineStrategy = ScalaOversizedSseStrategy.fromString(newValue))

  /**
   * @since 1.3.0
   */
  def withOversizedLineStrategy(
      newValue: org.apache.pekko.http.javadsl.settings.OversizedSseStrategy): ServerSentEventSettings =
    self.copy(oversizedLineStrategy = newValue.asScala)

  /**
   * @since 1.3.0
   */
  def withOversizedEventStrategy(newValue: String): ServerSentEventSettings =
    self.copy(oversizedEventStrategy = ScalaOversizedSseStrategy.fromString(newValue))

  /**
   * @since 1.3.0
   */
  def withOversizedEventStrategy(
      newValue: org.apache.pekko.http.javadsl.settings.OversizedSseStrategy): ServerSentEventSettings =
    self.copy(oversizedEventStrategy = newValue.asScala)
}

object ServerSentEventSettings extends SettingsCompanion[ServerSentEventSettings] {

  /**
   * Creates an instance of ServerSentEventSettings using the configuration provided by the given ActorSystem.
   * Java API
   */
  override def create(system: ActorSystem): ServerSentEventSettings = ServerSentEventSettingsImpl(system)

  /**
   * Creates an instance of ServerSentEventSettings using the given Config.
   * Java API
   */
  override def create(config: Config): ServerSentEventSettings = ServerSentEventSettingsImpl(config)

  /**
   * Create an instance of ServerSentEventSettings using the given String of config overrides to override
   * settings set in the class loader of this class.
   * Java API
   */
  override def create(configOverrides: String): ServerSentEventSettings = ServerSentEventSettingsImpl(configOverrides)
}
