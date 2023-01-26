/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.caching.impl.settings

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.http.caching.scaladsl.LfuCacheSettings
import pekko.http.impl.util.SettingsCompanionImpl
import pekko.http.impl.util._
import com.typesafe.config.Config

import scala.concurrent.duration.Duration

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
    extends SettingsCompanionImpl[LfuCachingSettingsImpl]("akka.http.caching.lfu-cache") {
  def fromSubConfig(root: Config, inner: Config): LfuCachingSettingsImpl = {
    val c = inner.withFallback(root.getConfig(prefix))
    new LfuCachingSettingsImpl(
      c.getInt("max-capacity"),
      c.getInt("initial-capacity"),
      c.getPotentiallyInfiniteDuration("time-to-live"),
      c.getPotentiallyInfiniteDuration("time-to-idle"))
  }
}
