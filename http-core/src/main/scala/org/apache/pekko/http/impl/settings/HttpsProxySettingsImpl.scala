/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.impl.settings

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.http.impl.util.SettingsCompanionImpl
import com.typesafe.config.Config

/** INTERNAL API */
@InternalApi
private[http] final case class HttpsProxySettingsImpl(
    host: String,
    port: Int) extends pekko.http.scaladsl.settings.HttpsProxySettings {
  require(host != "", "host must not be left empty")
  require(port > 0, "port must be greater than 0")

  override def productPrefix = "HttpsProxySettings"
}

object HttpsProxySettingsImpl extends SettingsCompanionImpl[HttpsProxySettingsImpl]("pekko.http.client.proxy.https") {
  override def fromSubConfig(root: Config, c: Config): HttpsProxySettingsImpl = {
    new HttpsProxySettingsImpl(
      c.getString("host"),
      c.getInt("port"))
  }
}
