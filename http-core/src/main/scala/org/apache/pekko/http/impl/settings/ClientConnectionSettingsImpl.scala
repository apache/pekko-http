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

import java.net.InetSocketAddress
import java.util.Random

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.http.impl.util._
import pekko.http.scaladsl.ClientTransport
import pekko.http.scaladsl.model.headers.`User-Agent`
import pekko.http.scaladsl.settings.ClientConnectionSettings.LogUnencryptedNetworkBytes
import pekko.http.scaladsl.settings.Http2ClientSettings.Http2ClientSettingsImpl
import pekko.http.scaladsl.settings.{ Http2ClientSettings, ParserSettings, WebSocketSettings }
import pekko.io.Inet.SocketOption
import pekko.util.JavaDurationConverters._
import com.typesafe.config.Config

import scala.collection.immutable
import scala.concurrent.duration.{ Duration, FiniteDuration }
import scala.util.Try

/** INTERNAL API */
@InternalApi
private[pekko] final case class ClientConnectionSettingsImpl(
    userAgentHeader: Option[`User-Agent`],
    connectingTimeout: FiniteDuration,
    idleTimeout: Duration,
    requestHeaderSizeHint: Int,
    logUnencryptedNetworkBytes: Option[Int],
    websocketSettings: WebSocketSettings,
    socketOptions: immutable.Seq[SocketOption],
    parserSettings: ParserSettings,
    streamCancellationDelay: FiniteDuration,
    localAddress: Option[InetSocketAddress],
    http2Settings: Http2ClientSettings,
    transport: ClientTransport)
    extends pekko.http.scaladsl.settings.ClientConnectionSettings {

  require(connectingTimeout >= Duration.Zero, "connectingTimeout must be >= 0")
  require(requestHeaderSizeHint > 0, "request-size-hint must be > 0")
  require(
    Try { parserSettings.maxContentLength }.isSuccess,
    "The provided ParserSettings is a generic object that does not contain the client-specific settings.")
  override def productPrefix = "ClientConnectionSettings"

  override def withConnectingTimeout(
      newValue: java.time.Duration): pekko.http.scaladsl.settings.ClientConnectionSettings =
    withConnectingTimeout(newValue.asScala)

  override def withIdleTimeout(newValue: java.time.Duration): pekko.http.scaladsl.settings.ClientConnectionSettings =
    withIdleTimeout(newValue.asScala)

  override def withStreamCancellationDelay(
      newValue: java.time.Duration): pekko.http.scaladsl.settings.ClientConnectionSettings =
    withStreamCancellationDelay(newValue.asScala)

  override def websocketRandomFactory: () => Random = websocketSettings.randomFactory
}

/** INTERNAL API */
@InternalApi
private[pekko] object ClientConnectionSettingsImpl
    extends SettingsCompanionImpl[ClientConnectionSettingsImpl]("pekko.http.client") {
  def fromSubConfig(root: Config, inner: Config): ClientConnectionSettingsImpl = {
    val c = inner.withFallback(root.getConfig(prefix))
    new ClientConnectionSettingsImpl(
      userAgentHeader = c.getString("user-agent-header").toOption.map(`User-Agent`(_)),
      connectingTimeout = c.getFiniteDuration("connecting-timeout"),
      idleTimeout = c.getPotentiallyInfiniteDuration("idle-timeout"),
      requestHeaderSizeHint = c.getIntBytes("request-header-size-hint"),
      logUnencryptedNetworkBytes = LogUnencryptedNetworkBytes(c.getString("log-unencrypted-network-bytes")),
      websocketSettings = WebSocketSettingsImpl.client(c.getConfig("websocket")),
      socketOptions = SocketOptionSettings.fromSubConfig(root, c.getConfig("socket-options")),
      parserSettings = ParserSettingsImpl.fromSubConfig(root, c.getConfig("parsing")),
      streamCancellationDelay = c.getFiniteDuration("stream-cancellation-delay"),
      localAddress = None,
      http2Settings = Http2ClientSettingsImpl.fromSubConfig(root, c.getConfig("http2")),
      transport = ClientTransport.TCP)
  }
}
