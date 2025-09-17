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

package org.apache.pekko.http.caching.javadsl

import org.apache.pekko
import pekko.annotation.DoNotInherit
import pekko.http.caching.impl.settings.LfuCachingSettingsImpl
import pekko.http.javadsl.settings.SettingsCompanion
import pekko.util.JavaDurationConverters._
import com.typesafe.config.Config

import scala.concurrent.duration.Duration

/**
 * Public API but not intended for subclassing
 */
@DoNotInherit
abstract class LfuCacheSettings private[http] () { self: LfuCachingSettingsImpl =>

  /* JAVA APIs */
  def getMaxCapacity: Int
  def getInitialCapacity: Int

  /**
   * Java API
   * <p>
   * In 2.0.0, the return type of this method changed from `scala.concurrent.duration.Duration`
   * to `java.time.Duration`.
   * </p>
   */
  def getTimeToLive: java.time.Duration

  /**
   * Java API
   * <p>
   * In 2.0.0, the return type of this method changed from `scala.concurrent.duration.Duration`
   * to `java.time.Duration`.
   * </p>
   */
  def getTimeToIdle: java.time.Duration

  def withMaxCapacity(newMaxCapacity: Int): LfuCacheSettings = self.copy(maxCapacity = newMaxCapacity)
  def withInitialCapacity(newInitialCapacity: Int): LfuCacheSettings = self.copy(initialCapacity = newInitialCapacity)
  def withTimeToLive(newTimeToLive: Duration): LfuCacheSettings = self.copy(timeToLive = newTimeToLive)

  /**
   * Java API
   * @since 1.3.0
   */
  def withTimeToLive(newTimeToLive: java.time.Duration): LfuCacheSettings =
    self.copy(timeToLive = newTimeToLive.asScala)
  def withTimeToIdle(newTimeToIdle: Duration): LfuCacheSettings = self.copy(timeToIdle = newTimeToIdle)

  /**
   * Java API
   * @since 1.3.0
   */
  def withTimeToIdle(newTimeToIdle: java.time.Duration): LfuCacheSettings =
    self.copy(timeToIdle = newTimeToIdle.asScala)
}

object LfuCacheSettings extends SettingsCompanion[LfuCacheSettings] {
  def create(config: Config): LfuCacheSettings = LfuCachingSettingsImpl(config)
  def create(configOverrides: String): LfuCacheSettings = LfuCachingSettingsImpl(configOverrides)
}
