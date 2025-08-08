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

package org.apache.pekko.http.scaladsl.settings

import org.apache.pekko
import pekko.annotation.DoNotInherit
import pekko.http.impl.settings.RoutingSettingsImpl
import com.typesafe.config.Config

/**
 * Public API but not intended for subclassing
 */
@DoNotInherit
abstract class RoutingSettings private[pekko] () extends pekko.http.javadsl.settings.RoutingSettings {
  self: RoutingSettingsImpl =>
  def verboseErrorMessages: Boolean
  def fileGetConditional: Boolean
  def renderVanityFooter: Boolean
  def rangeCountLimit: Int
  def rangeCoalescingThreshold: Long
  def decodeMaxBytesPerChunk: Int
  def decodeMaxSize: Long
  @deprecated(
    "binary compatibility method. Use `pekko.stream.materializer.blocking-io-dispatcher` to configure the dispatcher",
    since = "Akka HTTP 10.1.6")
  def fileIODispatcher: String

  /* Java APIs */
  def getVerboseErrorMessages: Boolean = this.verboseErrorMessages
  def getFileGetConditional: Boolean = this.fileGetConditional
  def getRenderVanityFooter: Boolean = this.renderVanityFooter
  def getRangeCountLimit: Int = this.rangeCountLimit
  def getRangeCoalescingThreshold: Long = this.rangeCoalescingThreshold
  def getDecodeMaxBytesPerChunk: Int = this.decodeMaxBytesPerChunk
  def getDecodeMaxSize: Long = this.decodeMaxSize
  @deprecated(
    "binary compatibility method. Use `pekko.stream.materializer.blocking-io-dispatcher` to configure the dispatcher",
    since = "Akka HTTP 10.1.6")
  def getFileIODispatcher: String = this.fileIODispatcher

  override def withVerboseErrorMessages(verboseErrorMessages: Boolean): RoutingSettings =
    self.copy(verboseErrorMessages = verboseErrorMessages)
  override def withFileGetConditional(fileGetConditional: Boolean): RoutingSettings =
    self.copy(fileGetConditional = fileGetConditional)
  override def withRenderVanityFooter(renderVanityFooter: Boolean): RoutingSettings =
    self.copy(renderVanityFooter = renderVanityFooter)
  override def withRangeCountLimit(rangeCountLimit: Int): RoutingSettings = self.copy(rangeCountLimit = rangeCountLimit)
  override def withRangeCoalescingThreshold(rangeCoalescingThreshold: Long): RoutingSettings =
    self.copy(rangeCoalescingThreshold = rangeCoalescingThreshold)
  override def withDecodeMaxBytesPerChunk(decodeMaxBytesPerChunk: Int): RoutingSettings =
    self.copy(decodeMaxBytesPerChunk = decodeMaxBytesPerChunk)
  override def withDecodeMaxSize(decodeMaxSize: Long): RoutingSettings = self.copy(decodeMaxSize = decodeMaxSize)
  @deprecated(
    "binary compatibility method. Use `pekko.stream.materializer.blocking-io-dispatcher` to configure the dispatcher",
    since = "Akka HTTP 10.1.6")
  override def withFileIODispatcher(fileIODispatcher: String): RoutingSettings = self
}

object RoutingSettings extends SettingsCompanion[RoutingSettings] {
  override def apply(config: Config): RoutingSettings = RoutingSettingsImpl(config)
  override def apply(configOverrides: String): RoutingSettings = RoutingSettingsImpl(configOverrides)
}
