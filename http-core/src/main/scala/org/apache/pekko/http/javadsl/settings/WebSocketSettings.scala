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

import java.time.{ Duration => JDuration }
import java.util.Random
import java.util.function.Supplier

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.annotation.DoNotInherit
import pekko.http.impl.settings.WebSocketSettingsImpl
import pekko.util.ByteString
import com.typesafe.config.Config

import scala.concurrent.duration.Duration
import scala.jdk.DurationConverters._

/**
 * Public API but not intended for subclassing
 */
@DoNotInherit
trait WebSocketSettings { self: WebSocketSettingsImpl =>
  def getRandomFactory: Supplier[Random]
  def periodicKeepAliveMode: String
  def periodicKeepAliveMaxIdle: Duration

  /**
   * Java API
   * @since 1.3.0
   */
  def getPeriodicKeepAliveMaxIdle: JDuration

  /**
   * The provided supplier will be invoked for each new keep-alive frame that is sent.
   * The ByteString will be included in the Ping or Pong frame sent as heartbeat,
   * so keep in mind to keep it relatively small, in order not to make the frames too bloated.
   */
  def getPeriodicKeepAliveData: Supplier[ByteString]

  def withRandomFactoryFactory(newValue: Supplier[Random]): WebSocketSettings =
    copy(randomFactory = () => newValue.get())
  def withPeriodicKeepAliveMode(newValue: String): WebSocketSettings =
    copy(periodicKeepAliveMode = newValue)
  def withPeriodicKeepAliveMaxIdle(newValue: Duration): WebSocketSettings =
    copy(periodicKeepAliveMaxIdle = newValue)

  /**
   * @since 1.3.0
   */
  def withPeriodicKeepAliveMaxIdle(newValue: JDuration): WebSocketSettings =
    copy(periodicKeepAliveMaxIdle = newValue.toScala)
  def withPeriodicKeepAliveData(newValue: Supplier[ByteString]): WebSocketSettings =
    copy(periodicKeepAliveData = () => newValue.get())

  def logFrames: Boolean
  def withLogFrames(shouldLog: Boolean): WebSocketSettings
}

object WebSocketSettings {
  def server(config: Config): WebSocketSettings = WebSocketSettingsImpl.server(config)
  def server(system: ActorSystem): WebSocketSettings = server(system.settings.config)

  def client(config: Config): WebSocketSettings = WebSocketSettingsImpl.client(config)
  def client(system: ActorSystem): WebSocketSettings = client(system.settings.config)
}
