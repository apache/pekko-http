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
abstract class PreviewServerSettings private[pekko] ()
    extends org.apache.pekko.http.javadsl.settings.PreviewServerSettings {
  self: PreviewServerSettingsImpl =>

  override def enableHttp2: Boolean

  // --

  // override for more specific return type
  override def withEnableHttp2(newValue: Boolean): PreviewServerSettings = self.copy(enableHttp2 = newValue)

}

object PreviewServerSettings extends SettingsCompanion[PreviewServerSettings] {
  def fromSubConfig(root: Config, c: Config) =
    PreviewServerSettingsImpl.fromSubConfig(root, c)
  override def apply(config: Config): PreviewServerSettings = PreviewServerSettingsImpl(config)
  override def apply(configOverrides: String): PreviewServerSettings = PreviewServerSettingsImpl(configOverrides)
}
