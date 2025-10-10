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
import pekko.annotation.{ ApiMayChange, DoNotInherit }
import pekko.http.impl.settings.PreviewServerSettingsImpl
import com.typesafe.config.Config

/**
 * Public API but not intended for subclassing
 *
 * Options that are in "preview" or "early access" mode.
 * These options may change and/or be removed within patch releases
 * without early notice (e.g. by moving them into a stable supported place).
 */
@ApiMayChange @DoNotInherit
abstract class PreviewServerSettings private[pekko] () { self: PreviewServerSettingsImpl =>

  /**
   * Configures the Http extension to bind using HTTP/2 if given an
   * [[pekko.http.scaladsl.HttpsConnectionContext]]. Otherwise binds as plain HTTP.
   */
  def enableHttp2: Boolean

  // ---

  def withEnableHttp2(newValue: Boolean): PreviewServerSettings = self.copy(enableHttp2 = newValue)
}

object PreviewServerSettings extends SettingsCompanion[PreviewServerSettings] {
  override def create(config: Config): PreviewServerSettings = PreviewServerSettingsImpl(config)
  override def create(configOverrides: String): PreviewServerSettings = PreviewServerSettingsImpl(configOverrides)
  override def create(system: ActorSystem): PreviewServerSettings = create(system.settings.config)
}
