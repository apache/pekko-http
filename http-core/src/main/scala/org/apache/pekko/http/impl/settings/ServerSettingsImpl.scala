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

package org.apache.pekko.http.impl.settings

import scala.language.implicitConversions
import scala.annotation.nowarn
import scala.collection.immutable
import scala.concurrent.duration._
import scala.util.Try

import com.typesafe.config.Config
import org.apache.pekko
import pekko.ConfigurationException
import pekko.actor.{ ActorSystem, ExtendedActorSystem }
import pekko.annotation.InternalApi
import pekko.http.ParsingErrorHandler
import pekko.http.impl.util._
import pekko.http.javadsl.{ settings => js }
import pekko.http.scaladsl.model.{ HttpHeader, HttpResponse, StatusCodes }
import pekko.http.scaladsl.model.headers.{ Host, Server }
import pekko.http.scaladsl.settings.{ SettingsCompanion => _, _ }
import pekko.http.scaladsl.settings.ServerSettings.LogUnencryptedNetworkBytes
import pekko.io.Inet.SocketOption

/** INTERNAL API */
@InternalApi
private[pekko] final case class ServerSettingsImpl(
    serverHeader: Option[Server],
    @nowarn("msg=deprecated") previewServerSettings: PreviewServerSettings,
    timeouts: ServerSettings.Timeouts,
    maxConnections: Int,
    pipeliningLimit: Int,
    remoteAddressAttribute: Boolean,
    rawRequestUriHeader: Boolean,
    transparentHeadRequests: Boolean,
    verboseErrorMessages: Boolean,
    responseHeaderSizeHint: Int,
    backlog: Int,
    logUnencryptedNetworkBytes: Option[Int],
    socketOptions: immutable.Seq[SocketOption],
    defaultHostHeader: Host,
    websocketSettings: WebSocketSettings,
    parserSettings: ParserSettings,
    http2Settings: Http2ServerSettings,
    defaultHttpPort: Int,
    defaultHttpsPort: Int,
    terminationDeadlineExceededResponse: HttpResponse,
    parsingErrorHandler: String,
    streamCancellationDelay: FiniteDuration,
    enableHttp2: Boolean) extends ServerSettings {

  require(0 < maxConnections, "max-connections must be > 0")
  require(0 < pipeliningLimit && pipeliningLimit <= 1024, "pipelining-limit must be > 0 and <= 1024")
  require(0 < responseHeaderSizeHint, "response-size-hint must be > 0")
  require(0 < backlog, "backlog must be > 0")
  require(
    Try { parserSettings.maxContentLength }.isSuccess,
    "The provided ParserSettings is a generic object that does not contain the server-specific settings.")

  override def productPrefix = "ServerSettings"

  private[http] def parsingErrorHandlerInstance(system: ActorSystem): ParsingErrorHandler =
    system.asInstanceOf[ExtendedActorSystem].dynamicAccess.createInstanceFor[ParsingErrorHandler](parsingErrorHandler,
      Nil).get
}

/** INTERNAL API */
@InternalApi
private[http] object ServerSettingsImpl extends SettingsCompanionImpl[ServerSettingsImpl]("pekko.http.server") {
  implicit def timeoutsShortcut(s: js.ServerSettings): js.ServerSettings.Timeouts = s.getTimeouts

  final case class Timeouts(
      idleTimeout: Duration,
      requestTimeout: Duration,
      bindTimeout: FiniteDuration,
      lingerTimeout: Duration) extends ServerSettings.Timeouts {
    require(idleTimeout > Duration.Zero, "idleTimeout must be infinite or > 0")
    require(bindTimeout > Duration.Zero, "bindTimeout must be > 0")
    require(lingerTimeout > Duration.Zero, "lingerTimeout must be infinite or > 0")
  }

  def fromSubConfig(root: Config, c: Config) = {
    val parserSettings = ParserSettingsImpl.fromSubConfig(root, c.getConfig("parsing"))
    val previewSettings = PreviewServerSettingsImpl.fromSubConfig(root, c.getConfig("preview"))
    new ServerSettingsImpl(
      c.getString("server-header").toOption.map(Server(_)),
      previewSettings,
      Timeouts(
        c.getPotentiallyInfiniteDuration("idle-timeout"),
        if (c.getString("request-timeout") == "off") Duration.Zero
        else c.getPotentiallyInfiniteDuration("request-timeout"),
        c.getFiniteDuration("bind-timeout"),
        c.getPotentiallyInfiniteDuration("linger-timeout")),
      c.getInt("max-connections"),
      c.getInt("pipelining-limit"),
      c.getBoolean("remote-address-attribute"),
      c.getBoolean("raw-request-uri-header"),
      c.getBoolean("transparent-head-requests"),
      c.getBoolean("verbose-error-messages"),
      c.getIntBytes("response-header-size-hint"),
      c.getInt("backlog"),
      LogUnencryptedNetworkBytes(c.getString("log-unencrypted-network-bytes")),
      SocketOptionSettings.fromSubConfig(root, c.getConfig("socket-options")),
      defaultHostHeader =
        HttpHeader.parse("Host", c.getString("default-host-header"), parserSettings) match {
          case HttpHeader.ParsingResult.Ok(x: Host, Nil) => x
          case result =>
            val info = result.errors.head.withSummary("Configured `default-host-header` is illegal")
            throw new ConfigurationException(info.formatPretty)
        },
      WebSocketSettingsImpl.server(c.getConfig("websocket")),
      parserSettings,
      Http2ServerSettings.Http2ServerSettingsImpl.fromSubConfig(root, c.getConfig("http2")),
      c.getInt("default-http-port"),
      c.getInt("default-https-port"),
      terminationDeadlineExceededResponseFrom(c),
      c.getString("parsing.error-handler"),
      c.getFiniteDuration("stream-cancellation-delay"),
      c.getBoolean("enable-http2") || previewSettings.enableHttp2)
  }

  private def terminationDeadlineExceededResponseFrom(c: Config): HttpResponse = {
    val status = c.getInt("termination-deadline-exceeded-response.status")
    HttpResponse(
      status = StatusCodes.getForKey(status)
        .getOrElse(throw new IllegalArgumentException(
          s"Illegal status code set for `termination-deadline-exceeded-response.status`, was: [$status]")))
  }

}
