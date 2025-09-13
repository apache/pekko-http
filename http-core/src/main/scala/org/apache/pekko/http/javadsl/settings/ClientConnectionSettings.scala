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

import java.net.InetSocketAddress
import java.util.function.Supplier
import java.util.{ Optional, Random }

import com.typesafe.config.Config
import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.annotation.ApiMayChange
import pekko.annotation.DoNotInherit
import pekko.http.impl.settings.ClientConnectionSettingsImpl
import pekko.http.javadsl.ClientTransport
import pekko.http.javadsl.model.headers.UserAgent
import pekko.http.impl.util.JavaMapping.Implicits._
import pekko.io.Inet.SocketOption

import scala.concurrent.duration.{ Duration, FiniteDuration }
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

/**
 * Public API but not intended for subclassing
 */
@DoNotInherit
abstract class ClientConnectionSettings private[pekko] () { self: ClientConnectionSettingsImpl =>

  /* JAVA APIs */
  final def getConnectingTimeout: FiniteDuration = connectingTimeout
  final def getParserSettings: ParserSettings = parserSettings
  final def getIdleTimeout: Duration = idleTimeout
  final def getSocketOptions: java.lang.Iterable[SocketOption] = socketOptions.asJava
  final def getUserAgentHeader: Optional[UserAgent] = (userAgentHeader: Option[UserAgent]).toJava
  final def getLogUnencryptedNetworkBytes: Optional[Int] = (logUnencryptedNetworkBytes: Option[Int]).toJava
  final def getStreamCancellationDelay: FiniteDuration = streamCancellationDelay
  final def getRequestHeaderSizeHint: Int = requestHeaderSizeHint
  final def getWebsocketSettings: WebSocketSettings = websocketSettings
  final def getWebsocketRandomFactory: Supplier[Random] = new Supplier[Random] {
    override def get(): Random = websocketRandomFactory()
  }
  final def getLocalAddress: Optional[InetSocketAddress] = (localAddress: Option[InetSocketAddress]).toJava

  /** The underlying transport used to connect to hosts. By default [[ClientTransport.TCP]] is used. */
  @ApiMayChange
  def getTransport: ClientTransport = transport.asJava

  // implemented in Scala variant

  def withConnectingTimeout(newValue: FiniteDuration): ClientConnectionSettings
  def withIdleTimeout(newValue: Duration): ClientConnectionSettings
  def withRequestHeaderSizeHint(newValue: Int): ClientConnectionSettings
  def withStreamCancellationDelay(newValue: FiniteDuration): ClientConnectionSettings

  // Java API versions of mutators

  def withUserAgentHeader(newValue: Optional[UserAgent]): ClientConnectionSettings =
    self.copy(userAgentHeader = (newValue.asScala: Option[UserAgent]).map(_.asScala))
  def withLogUnencryptedNetworkBytes(newValue: Optional[Int]): ClientConnectionSettings =
    self.copy(logUnencryptedNetworkBytes = newValue.toScala)
  def withWebsocketRandomFactory(newValue: java.util.function.Supplier[Random]): ClientConnectionSettings =
    self.copy(websocketSettings = websocketSettings.withRandomFactoryFactory(new Supplier[Random] {
      override def get(): Random = newValue.get()
    }))
  def withWebsocketSettings(newValue: WebSocketSettings): ClientConnectionSettings =
    self.copy(websocketSettings = newValue.asScala)
  def withSocketOptions(newValue: java.lang.Iterable[SocketOption]): ClientConnectionSettings =
    self.copy(socketOptions = newValue.asScala.toList)
  def withParserSettings(newValue: ParserSettings): ClientConnectionSettings =
    self.copy(parserSettings = newValue.asScala)
  def withLocalAddress(newValue: Optional[InetSocketAddress]): ClientConnectionSettings =
    self.copy(localAddress = newValue.asScala)

  @ApiMayChange
  def withTransport(newValue: ClientTransport): ClientConnectionSettings = self.copy(transport = newValue.asScala)
}

object ClientConnectionSettings extends SettingsCompanion[ClientConnectionSettings] {
  def create(config: Config): ClientConnectionSettings = ClientConnectionSettingsImpl(config)
  def create(configOverrides: String): ClientConnectionSettings = ClientConnectionSettingsImpl(configOverrides)
  override def create(system: ActorSystem): ClientConnectionSettings = create(system.settings.config)
}
