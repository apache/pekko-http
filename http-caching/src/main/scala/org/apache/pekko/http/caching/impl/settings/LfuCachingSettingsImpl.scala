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

package org.apache.pekko.http.caching.impl.settings

import scala.concurrent.duration.Duration

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.http.caching.scaladsl.LfuCacheSettings
import pekko.http.impl.util._
import pekko.http.impl.util.SettingsCompanionImpl

import com.typesafe.config.Config

/** INTERNAL API */
@InternalApi
private[http] final case class LfuCachingSettingsImpl(
    maxCapacity: Int,
    initialCapacity: Int,
    timeToLive: Duration,
    timeToIdle: Duration)
    extends LfuCacheSettings {
  override def productPrefix = "LfuCacheSettings"
}

/** INTERNAL API */
@InternalApi
private[http] object LfuCachingSettingsImpl
    extends SettingsCompanionImpl[LfuCachingSettingsImpl]("pekko.http.caching.lfu-cache") {
  def fromSubConfig(root: Config, inner: Config): LfuCachingSettingsImpl = {
    val c = inner.withFallback(root.getConfig(prefix))
    new LfuCachingSettingsImpl(
      c.getInt("max-capacity"),
      c.getInt("initial-capacity"),
      c.getPotentiallyInfiniteDuration("time-to-live"),
      c.getPotentiallyInfiniteDuration("time-to-idle"))
  }
}
