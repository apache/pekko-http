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

import java.util.Random

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.http.impl.engine.ws.Randoms
import pekko.http.impl.util._
import pekko.util.ByteString
import com.typesafe.config.Config

import scala.concurrent.duration.Duration

/** INTERNAL API */
@InternalApi
private[pekko] final case class WebSocketSettingsImpl(
    randomFactory: () => Random,
    periodicKeepAliveMode: String,
    periodicKeepAliveMaxIdle: Duration,
    periodicKeepAliveData: () => ByteString,
    logFrames: Boolean,
    compression: WebSocketCompressionSettingsImpl)
    extends pekko.http.scaladsl.settings.WebSocketSettings {

  require(
    WebSocketSettingsImpl.KeepAliveModes contains periodicKeepAliveMode,
    s"Unsupported keep-alive mode detected! Was [$periodicKeepAliveMode], yet only: ${WebSocketSettingsImpl.KeepAliveModes} are supported.")

  override def getPeriodicKeepAliveMaxIdle: java.time.Duration =
    JavaDurationConverter.toJava(periodicKeepAliveMaxIdle)
  override def productPrefix = "WebSocketSettings"

}

/** INTERNAL API */
@InternalApi
private[pekko] object WebSocketSettingsImpl { // on purpose not extending SettingsCompanion since this setting object exists on both server/client side

  private val KeepAliveModes = Seq("ping", "pong")

  // constant value used to identity check and avoid invoking the generation function if no data payload needed
  private val NoPeriodicKeepAliveData = () => ByteString.empty
  def hasNoCustomPeriodicKeepAliveData(settings: pekko.http.javadsl.settings.WebSocketSettings): Boolean =
    settings.asInstanceOf[WebSocketSettingsImpl].periodicKeepAliveData eq NoPeriodicKeepAliveData

  def serverFromRoot(root: Config): WebSocketSettingsImpl =
    server(root.getConfig("pekko.http.server.websocket"))
  def server(config: Config): WebSocketSettingsImpl =
    fromConfig(config, WebSocketCompressionSettingsImpl.fromConfig(config.getConfig("compression")))

  def clientFromRoot(root: Config): WebSocketSettingsImpl =
    client(root.getConfig("pekko.http.client.websocket"))
  def client(config: Config): WebSocketSettingsImpl =
    fromConfig(config, WebSocketCompressionSettingsImpl.Disabled)

  private def fromConfig(inner: Config, compression: WebSocketCompressionSettingsImpl): WebSocketSettingsImpl = {
    val c = inner
    WebSocketSettingsImpl(
      Randoms.SecureRandomInstances,
      c.getString("periodic-keep-alive-mode"), // mode could be extended to be a factory of pings, if we'd need control over the data field
      c.getPotentiallyInfiniteDuration("periodic-keep-alive-max-idle"),
      NoPeriodicKeepAliveData,
      c.getBoolean("log-frames"),
      compression)
  }

}

/** INTERNAL API */
@InternalApi
private[pekko] final case class WebSocketCompressionSettingsImpl(
    enabled: Boolean,
    maxAllocation: Long,
    compressionLevel: Int,
    preferredClientWindowSize: Int,
    allowServerNoContext: Boolean,
    preferredClientNoContext: Boolean) {
  require(maxAllocation >= 0, "websocket compression max-allocation must be >= 0")
  require(compressionLevel >= 0 && compressionLevel <= 9, "websocket compression level must be between 0 and 9")
  require(
    preferredClientWindowSize >= 8 && preferredClientWindowSize <= 15,
    "websocket compression preferred-client-window-size must be between 8 and 15")
}

/** INTERNAL API */
@InternalApi
private[pekko] object WebSocketCompressionSettingsImpl {
  val Disabled: WebSocketCompressionSettingsImpl =
    WebSocketCompressionSettingsImpl(
      enabled = false,
      maxAllocation = 64 * 1024,
      compressionLevel = 6,
      preferredClientWindowSize = 15,
      allowServerNoContext = false,
      preferredClientNoContext = false)

  def fromConfig(c: Config): WebSocketCompressionSettingsImpl = {
    val perMessageDeflate = c.getConfig("permessage-deflate")
    WebSocketCompressionSettingsImpl(
      c.getBoolean("enabled"),
      c.getBytes("max-allocation"),
      perMessageDeflate.getInt("compression-level"),
      perMessageDeflate.getInt("preferred-client-window-size"),
      perMessageDeflate.getBoolean("allow-server-no-context"),
      perMessageDeflate.getBoolean("preferred-client-no-context"))
  }
}
