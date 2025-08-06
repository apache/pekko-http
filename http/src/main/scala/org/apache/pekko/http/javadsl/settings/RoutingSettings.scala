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
import pekko.annotation.DoNotInherit
import pekko.http.impl.settings.RoutingSettingsImpl
import com.typesafe.config.Config

/**
 * Public API but not intended for subclassing
 */
@DoNotInherit
abstract class RoutingSettings private[pekko] () { self: RoutingSettingsImpl =>
  def getVerboseErrorMessages: Boolean
  def getFileGetConditional: Boolean
  def getRenderVanityFooter: Boolean
  def getRangeCountLimit: Int
  def getRangeCoalescingThreshold: Long
  def getDecodeMaxBytesPerChunk: Int
  @deprecated(
    "binary compatibility method. Use `pekko.stream.materializer.blocking-io-dispatcher` to configure the dispatcher",
    since = "Akka HTTP 10.1.6")
  def getFileIODispatcher: String

  def withVerboseErrorMessages(verboseErrorMessages: Boolean): RoutingSettings =
    self.copy(verboseErrorMessages = verboseErrorMessages)
  def withFileGetConditional(fileGetConditional: Boolean): RoutingSettings =
    self.copy(fileGetConditional = fileGetConditional)
  def withRenderVanityFooter(renderVanityFooter: Boolean): RoutingSettings =
    self.copy(renderVanityFooter = renderVanityFooter)
  def withRangeCountLimit(rangeCountLimit: Int): RoutingSettings = self.copy(rangeCountLimit = rangeCountLimit)
  def withRangeCoalescingThreshold(rangeCoalescingThreshold: Long): RoutingSettings =
    self.copy(rangeCoalescingThreshold = rangeCoalescingThreshold)
  def withDecodeMaxBytesPerChunk(decodeMaxBytesPerChunk: Int): RoutingSettings =
    self.copy(decodeMaxBytesPerChunk = decodeMaxBytesPerChunk)
  def withDecodeMaxSize(decodeMaxSize: Long): RoutingSettings = self.copy(decodeMaxSize = decodeMaxSize)
  @deprecated(
    "binary compatibility method. Use `pekko.stream.materializer.blocking-io-dispatcher` to configure the dispatcher",
    since = "Akka HTTP 10.1.6")
  def withFileIODispatcher(fileIODispatcher: String): RoutingSettings = self
}

object RoutingSettings extends SettingsCompanion[RoutingSettings] {
  override def create(config: Config): RoutingSettings = RoutingSettingsImpl(config)
  override def create(configOverrides: String): RoutingSettings = RoutingSettingsImpl(configOverrides)
  override def create(system: ActorSystem): RoutingSettings = create(system.settings.config)
}
