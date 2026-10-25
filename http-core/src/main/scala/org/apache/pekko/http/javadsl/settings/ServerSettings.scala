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
import java.util.Optional

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.annotation.{ DoNotInherit, InternalApi }
import pekko.http.impl.settings.ServerSettingsImpl
import pekko.http.impl.util.JavaDurationConverter
import pekko.http.impl.util.JavaMapping.Implicits._
import pekko.http.javadsl.model.headers.Host
import pekko.http.javadsl.model.headers.Server
import pekko.io.Inet.SocketOption
import com.typesafe.config.Config

import scala.jdk.CollectionConverters._
import scala.jdk.DurationConverters._
import scala.jdk.OptionConverters._
import scala.concurrent.duration.{ Duration, FiniteDuration }

/**
 * Public API but not intended for subclassing
 */
@DoNotInherit abstract class ServerSettings { self: ServerSettingsImpl =>
  def getServerHeader: Optional[Server]
  def getTimeouts: ServerSettings.Timeouts
  def getMaxConnections: Int
  def getPipeliningLimit: Int
  def getRemoteAddressAttribute: Boolean
  def getRawRequestUriHeader: Boolean
  def getTransparentHeadRequests: Boolean
  def getVerboseErrorMessages: Boolean
  def getResponseHeaderSizeHint: Int
  def getBacklog: Int
  def getSocketOptions: java.lang.Iterable[SocketOption]
  def getDefaultHostHeader: Host
  def getWebsocketSettings: WebSocketSettings
  def getParserSettings: ParserSettings
  def getLogUnencryptedNetworkBytes: Optional[Int]
  def getHttp2Settings: Http2ServerSettings = self.http2Settings
  def getDefaultHttpPort: Int
  def getDefaultHttpsPort: Int
  def getTerminationDeadlineExceededResponse: pekko.http.javadsl.model.HttpResponse
  def getParsingErrorHandler: String
  def getStreamCancellationDelay: FiniteDuration

  /**
   * Configures the Http extension to bind using HTTP/2 if given an
   * [[pekko.http.scaladsl.HttpsConnectionContext]]. Otherwise binds as plain HTTP.
   *
   * @since 1.3.0
   */
  def enableHttp2: Boolean

  // ---

  def withServerHeader(newValue: Optional[Server]): ServerSettings =
    self.copy(serverHeader = newValue.toScala.map(_.asScala))
  def withTimeouts(newValue: ServerSettings.Timeouts): ServerSettings = self.copy(timeouts = newValue.asScala)
  def withMaxConnections(newValue: Int): ServerSettings = self.copy(maxConnections = newValue)
  def withPipeliningLimit(newValue: Int): ServerSettings = self.copy(pipeliningLimit = newValue)
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

  /**
   * @since 1.3.0
   */
  def withEnableHttp2(newValue: Boolean): ServerSettings = self.copy(enableHttp2 = newValue)
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

    /**
     * Java API: an infinite idle timeout is returned as `ChronoUnit.FOREVER.getDuration`.
     *
     * @since 2.0.0
     */
    def getIdleTimeout: JDuration = JavaDurationConverter.toJava(idleTimeout)

    /**
     * Java API: an infinite request timeout is returned as `ChronoUnit.FOREVER.getDuration`.
     *
     * @since 2.0.0
     */
    def getRequestTimeout: JDuration = JavaDurationConverter.toJava(requestTimeout)

    /**
     * Java API
     *
     * @since 2.0.0
     */
    def getBindTimeout: JDuration = bindTimeout.toJava

    /**
     * Java API: an infinite linger timeout is returned as `ChronoUnit.FOREVER.getDuration`.
     *
     * @since 2.0.0
     */
    def getLingerTimeout: JDuration = JavaDurationConverter.toJava(lingerTimeout)

    /**
     * Java API: use `ChronoUnit.FOREVER.getDuration` for an infinite idle timeout.
     *
     * @since 2.0.0
     */
    def withIdleTimeout(newValue: JDuration): Timeouts = withIdleTimeout(JavaDurationConverter.toScala(newValue))

    /**
     * Java API: use `ChronoUnit.FOREVER.getDuration` for an infinite request timeout.
     *
     * @since 2.0.0
     */
    def withRequestTimeout(newValue: JDuration): Timeouts =
      withRequestTimeout(JavaDurationConverter.toScala(newValue))

    /**
     * Java API
     *
     * @since 2.0.0
     */
    def withBindTimeout(newValue: JDuration): Timeouts = withBindTimeout(newValue.toScala)

    /**
     * Java API: use `ChronoUnit.FOREVER.getDuration` for an infinite linger timeout.
     *
     * @since 2.0.0
     */
    def withLingerTimeout(newValue: JDuration): Timeouts = withLingerTimeout(JavaDurationConverter.toScala(newValue))

    /** INTERNAL API */
    @InternalApi
    protected def self = this.asInstanceOf[ServerSettingsImpl.Timeouts]
  }

  override def create(config: Config): ServerSettings = ServerSettingsImpl(config)
  override def create(configOverrides: String): ServerSettings = ServerSettingsImpl(configOverrides)
  override def create(system: ActorSystem): ServerSettings = create(system.settings.config)
}
