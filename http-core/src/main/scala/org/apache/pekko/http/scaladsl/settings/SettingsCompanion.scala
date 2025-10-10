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

package org.apache.pekko.http.scaladsl.settings

import org.apache.pekko
import pekko.actor.{ ActorRefFactory, ActorSystem, ClassicActorSystemProvider }
import pekko.annotation.InternalApi
import com.typesafe.config.Config
import pekko.http.impl.util._

/** INTERNAL API */
@InternalApi
private[pekko] trait SettingsCompanion[T] {

  /**
   * Creates an instance of settings using the configuration provided by the given ActorSystem.
   */
  final def apply(system: ActorSystem): T = apply(system.settings.config)
  final def apply(system: ClassicActorSystemProvider): T = apply(system.classicSystem.settings.config)
  implicit def default(implicit system: ClassicActorSystemProvider): T = apply(system.classicSystem)
  def default(actorRefFactory: ActorRefFactory): T = apply(actorSystem(actorRefFactory))

  /**
   * Creates an instance of settings using the given Config.
   */
  def apply(config: Config): T

  /**
   * Create an instance of settings using the given String of config overrides to override
   * settings set in the class loader of this class (i.e. by application.conf or reference.conf files in
   * the class loader of this class).
   */
  def apply(configOverrides: String): T
}
