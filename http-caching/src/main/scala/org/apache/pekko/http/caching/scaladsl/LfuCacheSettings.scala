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

package org.apache.pekko.http.caching.scaladsl

import scala.concurrent.duration.Duration

import org.apache.pekko
import pekko.annotation.DoNotInherit
import pekko.http.caching.impl.settings.LfuCachingSettingsImpl
import pekko.http.caching.javadsl
import pekko.http.impl.util.JavaDurationConverter
import pekko.http.scaladsl.settings.SettingsCompanion

import com.typesafe.config.Config

/**
 * Public API but not intended for subclassing
 */
@DoNotInherit
abstract class LfuCacheSettings private[http] () extends javadsl.LfuCacheSettings { self: LfuCachingSettingsImpl =>
  def maxCapacity: Int
  def initialCapacity: Int
  def timeToLive: Duration
  def timeToIdle: Duration

  final def getMaxCapacity: Int = self.maxCapacity
  final def getInitialCapacity: Int = self.initialCapacity
  final def getTimeToLive: java.time.Duration = JavaDurationConverter.toJava(self.timeToLive)
  final def getTimeToIdle: java.time.Duration = JavaDurationConverter.toJava(self.timeToIdle)

  override def withMaxCapacity(newMaxCapacity: Int): LfuCacheSettings = self.copy(maxCapacity = newMaxCapacity)
  override def withInitialCapacity(newInitialCapacity: Int): LfuCacheSettings =
    self.copy(initialCapacity = newInitialCapacity)
  override def withTimeToLive(newTimeToLive: Duration): LfuCacheSettings = self.copy(timeToLive = newTimeToLive)
  override def withTimeToIdle(newTimeToIdle: Duration): LfuCacheSettings = self.copy(timeToIdle = newTimeToIdle)
}

object LfuCacheSettings extends SettingsCompanion[LfuCacheSettings] {
  def apply(config: Config): LfuCacheSettings = LfuCachingSettingsImpl(config)
  def apply(configOverrides: String): LfuCacheSettings = LfuCachingSettingsImpl(configOverrides)
}
