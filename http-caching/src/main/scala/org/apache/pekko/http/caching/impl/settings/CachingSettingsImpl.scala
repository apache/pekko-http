/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.caching.impl.settings

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.http.caching.scaladsl.{ CachingSettings, LfuCacheSettings }
import pekko.http.impl.util.SettingsCompanionImpl
import com.typesafe.config.Config

/** INTERNAL API */
@InternalApi
private[http] final case class CachingSettingsImpl(lfuCacheSettings: LfuCacheSettings)
    extends CachingSettings {
  override def productPrefix = "CachingSettings"
}

/** INTERNAL API */
@InternalApi
private[http] object CachingSettingsImpl extends SettingsCompanionImpl[CachingSettingsImpl]("akka.http.caching") {
  def fromSubConfig(root: Config, c: Config): CachingSettingsImpl = {
    new CachingSettingsImpl(
      LfuCachingSettingsImpl.fromSubConfig(root, c.getConfig("lfu-cache")))
  }
}
