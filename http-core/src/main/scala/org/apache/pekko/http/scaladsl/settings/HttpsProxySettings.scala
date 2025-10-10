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
