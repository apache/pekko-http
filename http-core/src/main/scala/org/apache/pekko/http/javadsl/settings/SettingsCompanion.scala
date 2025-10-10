/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.settings

import org.apache.pekko.actor.ActorSystem
import com.typesafe.config.Config

trait SettingsCompanion[T] {

  /**
   * WARNING: This MUST overridden in sub-classes as otherwise won't be usable (return type) from Java.
   * Creates an instance of settings using the configuration provided by the given ActorSystem.
   *
   * Java API
   */
  def create(system: ActorSystem): T = create(system.settings.config)

  /**
   * Creates an instance of settings using the given Config.
   *
   * Java API
   */
  def create(config: Config): T

  /**
   * Create an instance of settings using the given String of config overrides to override
   * settings set in the class loader of this class (i.e. by application.conf or reference.conf files in
   * the class loader of this class).
   *
   * Java API
   */
  def create(configOverrides: String): T
}
