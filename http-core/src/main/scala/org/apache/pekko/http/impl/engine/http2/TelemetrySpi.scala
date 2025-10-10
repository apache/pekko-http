/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.impl.engine.http2

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.ActorSystem
import pekko.actor.ExtendedActorSystem
import pekko.annotation.InternalApi
import pekko.annotation.InternalStableApi
import pekko.http.scaladsl.model.HttpRequest
import pekko.http.scaladsl.model.HttpResponse
import pekko.stream.Attributes
import pekko.stream.Attributes.Attribute
import pekko.stream.scaladsl.BidiFlow
import pekko.stream.scaladsl.Flow
import pekko.stream.scaladsl.Tcp

import java.net.InetSocketAddress

/**
 * INTERNAL API
 */
@InternalApi
private[http] object TelemetrySpi {
  private val ConfigKey = "pekko.http.http2-telemetry-class"
  def create(system: ActorSystem): TelemetrySpi = {
    if (!system.settings.config.hasPath(ConfigKey)) NoOpTelemetry
    else {
      val fqcn = system.settings.config.getString(ConfigKey)
      try {
        system.asInstanceOf[ExtendedActorSystem].dynamicAccess
          .createInstanceFor[TelemetrySpi](fqcn, (classOf[ActorSystem], system) :: Nil)
          .get
      } catch {
        case ex: Throwable =>
          system.log.debug(
            "{} references a class that could not be instantiated ({}) falling back to no-op implementation", fqcn,
            ex.toString)
          NoOpTelemetry
      }
    }
  }
}

/**
 * INTERNAL API
 */
@InternalApi
object TelemetryAttributes {
  final case class ClientMeta(remote: InetSocketAddress) extends Attribute
  def prepareClientFlowAttributes(serverHost: String, serverPort: Int): Attributes =
    Attributes(ClientMeta(InetSocketAddress.createUnresolved(serverHost, serverPort)))
}

/**
 * INTERNAL API
 */
@InternalStableApi
trait TelemetrySpi {

  /**
   * Flow to intercept server connections. When run the flow will have the ClientMeta attribute set.
   */
  def client: BidiFlow[HttpRequest, HttpRequest, HttpResponse, HttpResponse, NotUsed]

  /**
   * Flow to intercept server binding.
   */
  def serverBinding: Flow[Tcp.IncomingConnection, Tcp.IncomingConnection, NotUsed]

  /**
   * Flow to intercept server connections.
   */
  def serverConnection: BidiFlow[HttpResponse, HttpResponse, HttpRequest, HttpRequest, NotUsed]
}

/**
 * INTERNAL API
 */
@InternalApi
private[http] object NoOpTelemetry extends TelemetrySpi {
  override def client: BidiFlow[HttpRequest, HttpRequest, HttpResponse, HttpResponse, NotUsed] = BidiFlow.identity
  override def serverBinding: Flow[Tcp.IncomingConnection, Tcp.IncomingConnection, NotUsed] =
    Flow[Tcp.IncomingConnection]
  override def serverConnection: BidiFlow[HttpResponse, HttpResponse, HttpRequest, HttpRequest, NotUsed] =
    BidiFlow.identity
}
