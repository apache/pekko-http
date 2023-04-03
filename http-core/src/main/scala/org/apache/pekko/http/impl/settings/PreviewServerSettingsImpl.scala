/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.impl.settings

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.http.impl.util.SettingsCompanionImpl
import com.typesafe.config.Config

@InternalApi
private[http] final case class PreviewServerSettingsImpl(
    enableHttp2: Boolean) extends pekko.http.scaladsl.settings.PreviewServerSettings {

  override def productPrefix: String = "PreviewServerSettings"
}

object PreviewServerSettingsImpl extends SettingsCompanionImpl[PreviewServerSettingsImpl]("akka.http.server.preview") {
  def fromSubConfig(root: Config, c: Config) = PreviewServerSettingsImpl(
    c.getBoolean("enable-http2"))
}
