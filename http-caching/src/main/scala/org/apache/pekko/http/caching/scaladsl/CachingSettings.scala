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

import org.apache.pekko
import pekko.annotation.DoNotInherit
import pekko.http.caching.impl.settings.CachingSettingsImpl
import pekko.http.caching.javadsl
import pekko.http.scaladsl.settings.SettingsCompanion

import com.typesafe.config.Config

/**
 * Public API but not intended for subclassing
 */
@DoNotInherit
abstract class CachingSettings private[http] () extends javadsl.CachingSettings { self: CachingSettingsImpl =>
  def lfuCacheSettings: LfuCacheSettings

  // overloads for idiomatic Scala use
  def withLfuCacheSettings(newSettings: LfuCacheSettings): CachingSettings =
    self.copy(lfuCacheSettings = newSettings)
}

object CachingSettings extends SettingsCompanion[CachingSettings] {
  def apply(config: Config): CachingSettings = CachingSettingsImpl(config)
  def apply(configOverrides: String): CachingSettings = CachingSettingsImpl(configOverrides)
}
