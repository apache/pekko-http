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
import pekko.actor.ActorSystem
import pekko.annotation.DoNotInherit
import pekko.http.caching.impl.settings.CachingSettingsImpl
import pekko.http.javadsl.settings.SettingsCompanion

import com.typesafe.config.Config

/**
 * Public API but not intended for subclassing
 */
@DoNotInherit
abstract class CachingSettings private[http] () { self: CachingSettingsImpl =>
  def lfuCacheSettings: LfuCacheSettings

  // overloads for idiomatic Scala use
  def withLfuCacheSettings(newSettings: LfuCacheSettings): CachingSettings = {
    import pekko.http.caching.CacheJavaMapping.Implicits._
    import pekko.http.impl.util.JavaMapping.Implicits._

    self.copy(lfuCacheSettings = newSettings.asScala)
  }
}

object CachingSettings extends SettingsCompanion[CachingSettings] {
  def create(config: Config): CachingSettings = CachingSettingsImpl(config)
  def create(configOverrides: String): CachingSettings = CachingSettingsImpl(configOverrides)
  override def create(system: ActorSystem): CachingSettings = create(system.settings.config)
}
