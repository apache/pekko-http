/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2017-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.scaladsl.settings
import java.util.Random
import java.util.function.Supplier

import scala.concurrent.duration._

import org.apache.pekko
import pekko.annotation.DoNotInherit
import pekko.http.impl.settings.WebSocketSettingsImpl
import pekko.util.ByteString

@DoNotInherit
abstract class WebSocketSettings extends pekko.http.javadsl.settings.WebSocketSettings { self: WebSocketSettingsImpl =>
  def randomFactory: () => Random
  override final val getRandomFactory: Supplier[Random] = () => self.randomFactory()
  override def periodicKeepAliveMode: String
  override def periodicKeepAliveMaxIdle: Duration

  /**
   * The provided function will be invoked for each new keep-alive frame that is sent.
   * The ByteString will be included in the Ping or Pong frame sent as heartbeat,
   * so keep in mind to keep it relatively small, in order not to make the frames too bloated.
   */
  def periodicKeepAliveData: () => ByteString
  final def getPeriodicKeepAliveData: Supplier[ByteString] = () => self.periodicKeepAliveData()

  /**
   * @since 2.0.0
   */
  override def receiveIdleTimeout: Duration

  /**
   * @since 2.0.0
   */
  override def sendIdleTimeout: Duration

  override def withRandomFactoryFactory(newValue: Supplier[Random]): WebSocketSettings =
    copy(randomFactory = () => newValue.get())
  override def withPeriodicKeepAliveMode(newValue: String): WebSocketSettings =
    copy(periodicKeepAliveMode = newValue)
  override def withPeriodicKeepAliveMaxIdle(newValue: Duration): WebSocketSettings =
    copy(periodicKeepAliveMaxIdle = newValue)
  def withPeriodicKeepAliveData(newValue: () => ByteString): WebSocketSettings =
    copy(periodicKeepAliveData = newValue)

  /**
   * @since 2.0.0
   */
  override def withReceiveIdleTimeout(newValue: Duration): WebSocketSettings =
    copy(receiveIdleTimeout = newValue)

  /**
   * @since 2.0.0
   */
  override def withSendIdleTimeout(newValue: Duration): WebSocketSettings =
    copy(sendIdleTimeout = newValue)

  /** @since 2.1.0 */
  override def withCloseTimeout(newValue: FiniteDuration): WebSocketSettings = copy(closeTimeout = newValue)

  def logFrames: Boolean
  override def withLogFrames(shouldLog: Boolean): WebSocketSettings = copy(logFrames = shouldLog)

  /**
   * Whether managed client-side permessage-deflate is enabled.
   *
   * @since 2.1.0
   */
  def clientCompressionEnabled: Boolean = getClientCompressionEnabled

  /**
   * Enable or disable managed client-side permessage-deflate. Does not enable server compression.
   *
   * @since 2.1.0
   */
  override def withClientCompressionEnabled(enabled: Boolean): WebSocketSettings =
    copy(compression = self.compression.copy(clientEnabled = enabled))

  /** Client-only opening upgrade response timeout; use `Duration.Inf` to disable it. */
  override def withOpeningTimeout(newValue: Duration): WebSocketSettings = copy(openingTimeout = newValue)

}
