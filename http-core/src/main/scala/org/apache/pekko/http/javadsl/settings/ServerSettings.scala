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

import java.util.function.Supplier
import java.util.{ Optional, Random }

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.annotation.{ DoNotInherit, InternalApi }
import pekko.http.impl.settings.ServerSettingsImpl
import pekko.http.javadsl.model.headers.Host
import pekko.http.javadsl.model.headers.Server
import pekko.io.Inet.SocketOption
import pekko.http.impl.util.JavaMapping.Implicits._
import pekko.util.OptionConverters._
import com.typesafe.config.Config

import scala.collection.JavaConverters._
import scala.concurrent.duration.{ Duration, FiniteDuration }

/**
 * Public API but not intended for subclassing
 */
@DoNotInherit abstract class ServerSettings { self: ServerSettingsImpl =>
  def getServerHeader: Optional[Server]
  def getPreviewServerSettings: PreviewServerSettings
  def getTimeouts: ServerSettings.Timeouts
  def getMaxConnections: Int
  def getPipeliningLimit: Int

  /**
   * @deprecated since Akka HTTP 10.2.0, use remoteAddressAttribute instead
   */
  @deprecated("Use remoteAddressAttribute instead", since = "Akka HTTP 10.2.0")
  def getRemoteAddressHeader: Boolean
  def getRemoteAddressAttribute: Boolean
  def getRawRequestUriHeader: Boolean
  def getTransparentHeadRequests: Boolean
  def getVerboseErrorMessages: Boolean
  def getResponseHeaderSizeHint: Int
  def getBacklog: Int
  def getSocketOptions: java.lang.Iterable[SocketOption]
  def getDefaultHostHeader: Host

  /**
   * @deprecated since Akka HTTP 10.2.0, use websocketSettings.getRandomFactory instead
   */
  @deprecated("Kept for binary compatibility; Use websocketSettings.getRandomFactory instead",
    since = "Akka HTTP 10.2.0")
  def getWebsocketRandomFactory: java.util.function.Supplier[Random]
  def getWebsocketSettings: WebSocketSettings
  def getParserSettings: ParserSettings
  def getLogUnencryptedNetworkBytes: Optional[Int]
  def getHttp2Settings: Http2ServerSettings = self.http2Settings
  def getDefaultHttpPort: Int
  def getDefaultHttpsPort: Int
  def getTerminationDeadlineExceededResponse: pekko.http.javadsl.model.HttpResponse
  def getParsingErrorHandler: String
  def getStreamCancellationDelay: FiniteDuration

  // ---

  def withServerHeader(newValue: Optional[Server]): ServerSettings =
    self.copy(serverHeader = newValue.asScala)
  def withPreviewServerSettings(newValue: PreviewServerSettings): ServerSettings =
    self.copy(previewServerSettings = newValue.asScala)
  def withTimeouts(newValue: ServerSettings.Timeouts): ServerSettings = self.copy(timeouts = newValue.asScala)
  def withMaxConnections(newValue: Int): ServerSettings = self.copy(maxConnections = newValue)
  def withPipeliningLimit(newValue: Int): ServerSettings = self.copy(pipeliningLimit = newValue)
  def withRemoteAddressHeader(newValue: Boolean): ServerSettings = self.copy(remoteAddressHeader = newValue)
  def withRemoteAddressAttribute(newValue: Boolean): ServerSettings = self.copy(remoteAddressAttribute = newValue)
  def withRawRequestUriHeader(newValue: Boolean): ServerSettings = self.copy(rawRequestUriHeader = newValue)
  def withTransparentHeadRequests(newValue: Boolean): ServerSettings = self.copy(transparentHeadRequests = newValue)
  def withVerboseErrorMessages(newValue: Boolean): ServerSettings = self.copy(verboseErrorMessages = newValue)
  def withResponseHeaderSizeHint(newValue: Int): ServerSettings = self.copy(responseHeaderSizeHint = newValue)
  def withBacklog(newValue: Int): ServerSettings = self.copy(backlog = newValue)
  def withSocketOptions(newValue: java.lang.Iterable[SocketOption]): ServerSettings =
    self.copy(socketOptions = newValue.asScala.toList)
  def withDefaultHostHeader(newValue: Host): ServerSettings = self.copy(defaultHostHeader = newValue.asScala)
  def withParserSettings(newValue: ParserSettings): ServerSettings = self.copy(parserSettings = newValue.asScala)

  /**
   * @deprecated since Akka HTTP 10.2.0, use websocketSettings.withRandomFactoryFactory instead
   */
  @deprecated("Kept for binary compatibility; Use websocketSettings.withRandomFactoryFactory instead",
    since = "Akka HTTP 10.2.0")
  def withWebsocketRandomFactory(newValue: java.util.function.Supplier[Random]): ServerSettings =
    self.copy(websocketSettings = websocketSettings.withRandomFactoryFactory(new Supplier[Random] {
      override def get(): Random = newValue.get()
    }))
  def withWebsocketSettings(newValue: WebSocketSettings): ServerSettings =
    self.copy(websocketSettings = newValue.asScala)
  def withLogUnencryptedNetworkBytes(newValue: Optional[Int]): ServerSettings =
    self.copy(logUnencryptedNetworkBytes = newValue.toScala)
  def withHttp2Settings(newValue: Http2ServerSettings): ServerSettings = self.copy(http2Settings = newValue.asScala)
  def withDefaultHttpPort(newValue: Int): ServerSettings = self.copy(defaultHttpPort = newValue)
  def withDefaultHttpsPort(newValue: Int): ServerSettings = self.copy(defaultHttpPort = newValue)
  def withTerminationDeadlineExceededResponse(response: pekko.http.javadsl.model.HttpResponse): ServerSettings =
    self.copy(terminationDeadlineExceededResponse = response.asScala)
  def withParsingErrorHandler(newValue: String): ServerSettings = self.copy(parsingErrorHandler = parsingErrorHandler)
  def withStreamCancellationDelay(newValue: FiniteDuration): ServerSettings =
    self.copy(streamCancellationDelay = newValue)
}

object ServerSettings extends SettingsCompanion[ServerSettings] {
  trait Timeouts {
    def idleTimeout: Duration
    def requestTimeout: Duration
    def bindTimeout: FiniteDuration
    def lingerTimeout: Duration

    // ---
    def withIdleTimeout(newValue: Duration): Timeouts = self.copy(idleTimeout = newValue)
    def withRequestTimeout(newValue: Duration): Timeouts = self.copy(requestTimeout = newValue)
    def withBindTimeout(newValue: FiniteDuration): Timeouts = self.copy(bindTimeout = newValue)
    def withLingerTimeout(newValue: Duration): Timeouts = self.copy(lingerTimeout = newValue)

    /** INTERNAL API */
    @InternalApi
    protected def self = this.asInstanceOf[ServerSettingsImpl.Timeouts]
  }

  override def create(config: Config): ServerSettings = ServerSettingsImpl(config)
  override def create(configOverrides: String): ServerSettings = ServerSettingsImpl(configOverrides)
  override def create(system: ActorSystem): ServerSettings = create(system.settings.config)
}
