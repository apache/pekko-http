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

package org.apache.pekko.http.scaladsl.settings

import com.typesafe.config.Config
import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.annotation.{ DoNotInherit, InternalApi }
import pekko.http.ParsingErrorHandler
import pekko.http.impl.settings.ServerSettingsImpl
import pekko.http.impl.util._
import pekko.http.impl.util.JavaMapping.Implicits._
import pekko.http.javadsl.{ settings => js }
import pekko.http.scaladsl.model.HttpResponse
import pekko.http.scaladsl.model.headers.{ Host, Server }
import pekko.io.Inet.SocketOption

import scala.collection.immutable
import scala.concurrent.duration.{ Duration, FiniteDuration }
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import scala.language.implicitConversions

/**
 * Public API but not intended for subclassing
 */
@DoNotInherit
abstract class ServerSettings private[pekko] () extends pekko.http.javadsl.settings.ServerSettings {
  self: ServerSettingsImpl =>
  def serverHeader: Option[Server]
  def previewServerSettings: PreviewServerSettings
  def timeouts: ServerSettings.Timeouts
  def maxConnections: Int
  def pipeliningLimit: Int
  def remoteAddressAttribute: Boolean
  def rawRequestUriHeader: Boolean
  def transparentHeadRequests: Boolean
  def verboseErrorMessages: Boolean
  def responseHeaderSizeHint: Int
  def backlog: Int
  def socketOptions: immutable.Seq[SocketOption]
  def defaultHostHeader: Host
  def websocketSettings: WebSocketSettings
  def parserSettings: ParserSettings
  def logUnencryptedNetworkBytes: Option[Int]
  def http2Settings: Http2ServerSettings
  def defaultHttpPort: Int
  def defaultHttpsPort: Int
  def terminationDeadlineExceededResponse: HttpResponse
  def parsingErrorHandler: String
  def streamCancellationDelay: FiniteDuration

  /* Java APIs */

  override def getBacklog = this.backlog
  override def getPreviewServerSettings: pekko.http.javadsl.settings.PreviewServerSettings = this.previewServerSettings
  override def getDefaultHostHeader = this.defaultHostHeader.asJava
  override def getPipeliningLimit = this.pipeliningLimit
  override def getParserSettings: js.ParserSettings = this.parserSettings
  override def getMaxConnections = this.maxConnections
  override def getTransparentHeadRequests = this.transparentHeadRequests
  override def getResponseHeaderSizeHint = this.responseHeaderSizeHint
  override def getVerboseErrorMessages = this.verboseErrorMessages
  override def getSocketOptions = this.socketOptions.asJava
  override def getServerHeader = this.serverHeader.map(_.asJava).toJava
  override def getTimeouts = this.timeouts
  override def getRawRequestUriHeader = this.rawRequestUriHeader
  override def getRemoteAddressAttribute: Boolean = this.remoteAddressAttribute
  override def getLogUnencryptedNetworkBytes = this.logUnencryptedNetworkBytes.toJava
  override def getDefaultHttpPort: Int = this.defaultHttpPort
  override def getDefaultHttpsPort: Int = this.defaultHttpsPort
  override def getTerminationDeadlineExceededResponse: pekko.http.javadsl.model.HttpResponse =
    this.terminationDeadlineExceededResponse
  override def getParsingErrorHandler: String = this.parsingErrorHandler
  override def getStreamCancellationDelay: FiniteDuration = this.streamCancellationDelay
  // ---

  // override for more specific return type
  def withPreviewServerSettings(newValue: PreviewServerSettings): ServerSettings =
    self.copy(previewServerSettings = newValue)
  override def withMaxConnections(newValue: Int): ServerSettings = self.copy(maxConnections = newValue)
  override def withPipeliningLimit(newValue: Int): ServerSettings = self.copy(pipeliningLimit = newValue)
  override def withRemoteAddressAttribute(newValue: Boolean): ServerSettings =
    self.copy(remoteAddressAttribute = newValue)
  override def withRawRequestUriHeader(newValue: Boolean): ServerSettings = self.copy(rawRequestUriHeader = newValue)
  override def withTransparentHeadRequests(newValue: Boolean): ServerSettings =
    self.copy(transparentHeadRequests = newValue)
  override def withVerboseErrorMessages(newValue: Boolean): ServerSettings = self.copy(verboseErrorMessages = newValue)
  override def withResponseHeaderSizeHint(newValue: Int): ServerSettings = self.copy(responseHeaderSizeHint = newValue)
  override def withBacklog(newValue: Int): ServerSettings = self.copy(backlog = newValue)
  override def withSocketOptions(newValue: java.lang.Iterable[SocketOption]): ServerSettings =
    self.copy(socketOptions = newValue.asScala.toList)
  override def getWebsocketSettings: WebSocketSettings = self.websocketSettings
  override def withDefaultHttpPort(newValue: Int): ServerSettings = self.copy(defaultHttpPort = newValue)
  override def withDefaultHttpsPort(newValue: Int): ServerSettings = self.copy(defaultHttpsPort = newValue)
  override def withTerminationDeadlineExceededResponse(
      response: pekko.http.javadsl.model.HttpResponse): ServerSettings =
    self.copy(terminationDeadlineExceededResponse = response.asScala)
  override def withParsingErrorHandler(newValue: String): ServerSettings = self.copy(parsingErrorHandler = newValue)
  override def withStreamCancellationDelay(newValue: FiniteDuration): ServerSettings =
    self.copy(streamCancellationDelay = newValue)

  // overloads for Scala idiomatic use
  def withTimeouts(newValue: ServerSettings.Timeouts): ServerSettings = self.copy(timeouts = newValue)
  def withServerHeader(newValue: Option[Server]): ServerSettings = self.copy(serverHeader = newValue)
  def withLogUnencryptedNetworkBytes(newValue: Option[Int]): ServerSettings =
    self.copy(logUnencryptedNetworkBytes = newValue)
  def withDefaultHostHeader(newValue: Host): ServerSettings = self.copy(defaultHostHeader = newValue)
  def withParserSettings(newValue: ParserSettings): ServerSettings = self.copy(parserSettings = newValue)
  def withWebsocketSettings(newValue: WebSocketSettings): ServerSettings = self.copy(websocketSettings = newValue)
  def withSocketOptions(newValue: immutable.Seq[SocketOption]): ServerSettings = self.copy(socketOptions = newValue)
  def withHttp2Settings(newValue: Http2ServerSettings): ServerSettings = copy(http2Settings = newValue)

  // Scala-only lenses
  def mapHttp2Settings(f: Http2ServerSettings => Http2ServerSettings): ServerSettings =
    withHttp2Settings(f(this.http2Settings))
  def mapParserSettings(f: ParserSettings => ParserSettings): ServerSettings =
    withParserSettings(f(this.parserSettings))
  def mapPreviewServerSettings(f: PreviewServerSettings => PreviewServerSettings): ServerSettings =
    withPreviewServerSettings(f(this.previewServerSettings))
  def mapWebsocketSettings(f: WebSocketSettings => WebSocketSettings): ServerSettings =
    withWebsocketSettings(f(this.websocketSettings))
  def mapTimeouts(f: ServerSettings.Timeouts => ServerSettings.Timeouts): ServerSettings =
    withTimeouts(f(this.timeouts))

  /**
   * INTERNAL API
   *
   * Returns an instance of the ParsingErrorHandler as specified by `parsingErrorHandler`
   */
  @InternalApi
  private[http] def parsingErrorHandlerInstance(system: ActorSystem): ParsingErrorHandler
}

object ServerSettings extends SettingsCompanion[ServerSettings] {
  trait Timeouts extends pekko.http.javadsl.settings.ServerSettings.Timeouts {
    // ---
    // override for more specific return types
    override def withIdleTimeout(newValue: Duration): Timeouts = self.copy(idleTimeout = newValue)
    override def withRequestTimeout(newValue: Duration): Timeouts = self.copy(requestTimeout = newValue)
    override def withBindTimeout(newValue: FiniteDuration): Timeouts = self.copy(bindTimeout = newValue)
    override def withLingerTimeout(newValue: Duration): Timeouts = self.copy(lingerTimeout = newValue)
  }

  implicit def timeoutsShortcut(s: ServerSettings): Timeouts = s.timeouts

  override def apply(config: Config): ServerSettings = ServerSettingsImpl(config)
  override def apply(configOverrides: String): ServerSettings = ServerSettingsImpl(configOverrides)

  object LogUnencryptedNetworkBytes {
    def apply(string: String): Option[Int] =
      string.toRootLowerCase match {
        case "off" => None
        case value => Option(value.toInt)
      }
  }
}
