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
private[http] object CachingSettingsImpl extends SettingsCompanionImpl[CachingSettingsImpl]("pekko.http.caching") {
  def fromSubConfig(root: Config, c: Config): CachingSettingsImpl = {
    new CachingSettingsImpl(
      LfuCachingSettingsImpl.fromSubConfig(root, c.getConfig("lfu-cache")))
  }
}
