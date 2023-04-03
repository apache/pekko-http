/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.scaladsl.settings

import org.apache.pekko
import pekko.annotation.DoNotInherit
import pekko.http.impl.settings.HttpsProxySettingsImpl
import com.typesafe.config.Config

/**
 * Public API but not intended for subclassing
 */
@DoNotInherit
abstract class HttpsProxySettings private[pekko] extends pekko.http.javadsl.settings.HttpsProxySettings() {
  self: HttpsProxySettingsImpl =>
  def host: String
  def port: Int
}

object HttpsProxySettings extends SettingsCompanion[HttpsProxySettings] {
  override def apply(config: Config): HttpsProxySettings = HttpsProxySettingsImpl(config)
  override def apply(configOverrides: String): HttpsProxySettings = HttpsProxySettingsImpl(configOverrides)
}
