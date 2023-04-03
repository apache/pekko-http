/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.settings

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.annotation.DoNotInherit
import pekko.http.impl.settings.HttpsProxySettingsImpl
import com.typesafe.config.Config

/**
 * Public API but not intended for subclassing
 */
@DoNotInherit
abstract class HttpsProxySettings private[pekko] () { self: HttpsProxySettingsImpl =>
  def getHost: String = host
  def getPort: Int = port
}

object HttpsProxySettings extends SettingsCompanion[HttpsProxySettings] {
  override def create(config: Config): HttpsProxySettings = HttpsProxySettingsImpl(config)
  override def create(configOverrides: String): HttpsProxySettings = HttpsProxySettingsImpl(configOverrides)
  override def create(system: ActorSystem): HttpsProxySettings = create(system.settings.config)
}
