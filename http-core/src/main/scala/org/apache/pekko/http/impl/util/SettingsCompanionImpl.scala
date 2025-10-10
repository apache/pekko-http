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

package org.apache.pekko.http.impl.util

import java.net.{ InetAddress, InetSocketAddress }

import com.typesafe.config.{ Config, ConfigFactory }
import com.typesafe.config.ConfigFactory._

import scala.util.control.NonFatal
import scala.collection.immutable.ListMap
import scala.jdk.CollectionConverters._
import org.apache.pekko
import pekko.actor.{ ActorRefFactory, ActorSystem }
import pekko.annotation.InternalApi

/**
 * INTERNAL API
 */
@InternalApi
private[http] abstract class SettingsCompanionImpl[T](protected val prefix: String) {
  private final val MaxCached = 8
  private[this] var cache = ListMap.empty[ActorSystem, T]

  implicit def default(implicit refFactory: ActorRefFactory): T =
    apply(actorSystem)

  def apply(system: ActorSystem): T =
    // we use and update the cache without any synchronization,
    // there are two possible "problems" resulting from this:
    // - cache misses of things another thread has already put into the cache,
    //   in these cases we do double work, but simply accept it
    // - cache hits of things another thread has already dropped from the cache,
    //   in these cases we avoid double work, which is nice
    cache.getOrElse(system, {
        val settings = apply(system.settings.config)
        val c =
          if (cache.size < MaxCached) cache
          else cache.tail // drop the first (and oldest) cache entry
        cache = c.updated(system, settings)
        settings
      })

  def apply(configOverrides: String): T =
    apply(parseString(configOverrides)
      .withFallback(SettingsCompanionImpl.configAdditions)
      .withFallback(defaultReference(getClass.getClassLoader)))

  def apply(config: Config): T =
    fromSubConfig(config, config.getConfig(prefix))

  def fromSubConfig(root: Config, c: Config): T
}

private[http] object SettingsCompanionImpl {
  lazy val configAdditions: Config = {
    val localHostName =
      try new InetSocketAddress(InetAddress.getLocalHost, 80).getHostString
      catch { case NonFatal(_) => "" }
    ConfigFactory.parseMap(Map("pekko.http.hostname" -> localHostName).asJava)
  }
}
