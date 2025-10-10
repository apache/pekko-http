/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

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

object PreviewServerSettingsImpl extends SettingsCompanionImpl[PreviewServerSettingsImpl]("pekko.http.server.preview") {
  def fromSubConfig(root: Config, c: Config) = PreviewServerSettingsImpl(
    c.getBoolean("enable-http2"))
}
