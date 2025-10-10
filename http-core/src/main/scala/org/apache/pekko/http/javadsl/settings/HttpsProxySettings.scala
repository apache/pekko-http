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
