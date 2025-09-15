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

import java.util.concurrent.CompletionStage
import org.apache.pekko
import pekko.NotUsed
import pekko.actor.ClassicActorSystemProvider
import pekko.annotation.InternalApi
import pekko.event.LoggingAdapter
import pekko.http.impl.engine.http2.client.PersistentConnection
import pekko.http.scaladsl.Http.OutgoingConnection
import pekko.http.scaladsl.model.HttpRequest
import pekko.http.scaladsl.model.HttpResponse
import pekko.http.scaladsl.settings.ClientConnectionSettings
import pekko.stream.scaladsl.Flow
import pekko.http.javadsl
import pekko.http.javadsl.{ OutgoingConnectionBuilder => JOutgoingConnectionBuilder }
import pekko.http.scaladsl.ConnectionContext
import pekko.http.scaladsl.Http
import pekko.http.scaladsl.HttpsConnectionContext
import pekko.http.scaladsl.OutgoingConnectionBuilder
import pekko.stream.javadsl.{ Flow => JFlow }

import scala.concurrent.{ ExecutionContext, Future }

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] object OutgoingConnectionBuilderImpl {

  def apply(host: String, system: ClassicActorSystemProvider): OutgoingConnectionBuilder =
    Impl(
      host,
      None,
      clientConnectionSettings = ClientConnectionSettings(system),
      connectionContext = None,
      log = system.classicSystem.log,
      system = system,
      usingHttp2 = false)

  private final case class Impl(
      host: String,
      port: Option[Int],
      clientConnectionSettings: ClientConnectionSettings,
      connectionContext: Option[HttpsConnectionContext],
      log: LoggingAdapter,
      system: ClassicActorSystemProvider,
      usingHttp2: Boolean) extends OutgoingConnectionBuilder {

    override def toHost(host: String): OutgoingConnectionBuilder = copy(host = host)

    override def toPort(port: Int): OutgoingConnectionBuilder = copy(port = Some(port))

    override def withCustomHttpsConnectionContext(
        httpsConnectionContext: HttpsConnectionContext): OutgoingConnectionBuilder =
      copy(connectionContext = Some(httpsConnectionContext))

    override def withClientConnectionSettings(settings: ClientConnectionSettings): OutgoingConnectionBuilder =
      copy(clientConnectionSettings = settings)

    override def logTo(logger: LoggingAdapter): OutgoingConnectionBuilder = copy(log = logger)

    override def http(): Flow[HttpRequest, HttpResponse, Future[OutgoingConnection]] = {
      // http/1.1 plaintext
      Http(system.classicSystem).outgoingConnectionUsingContext(host, port.getOrElse(80),
        ConnectionContext.noEncryption(), clientConnectionSettings, log)
    }

    override def https(): Flow[HttpRequest, HttpResponse, Future[OutgoingConnection]] = {
      // http/1.1 tls
      Http(system.classicSystem).outgoingConnectionHttps(host, port.getOrElse(443),
        connectionContext.getOrElse(Http(system.classicSystem).defaultClientHttpsContext), None,
        clientConnectionSettings, log)
    }

    override def http2(): Flow[HttpRequest, HttpResponse, Future[OutgoingConnection]] = {
      // http/2 tls
      val port = this.port.getOrElse(443)
      Http2(system.classicSystem).outgoingConnection(host, port,
        connectionContext.getOrElse(Http(system.classicSystem).defaultClientHttpsContext), clientConnectionSettings,
        log)
    }

    override def managedPersistentHttp2(): Flow[HttpRequest, HttpResponse, NotUsed] =
      PersistentConnection.managedConnection(
        http2(),
        clientConnectionSettings.http2Settings)

    override def http2WithPriorKnowledge(): Flow[HttpRequest, HttpResponse, Future[OutgoingConnection]] = {
      // http/2 prior knowledge plaintext
      Http2(system.classicSystem).outgoingConnectionPriorKnowledge(host, port.getOrElse(80), clientConnectionSettings,
        log)
    }

    override def managedPersistentHttp2WithPriorKnowledge(): Flow[HttpRequest, HttpResponse, NotUsed] =
      PersistentConnection.managedConnection(
        http2WithPriorKnowledge(),
        clientConnectionSettings.http2Settings)

    override private[pekko] def toJava: JOutgoingConnectionBuilder = new JavaAdapter(this)
  }

  private class JavaAdapter(actual: Impl) extends JOutgoingConnectionBuilder {

    override def toHost(host: String): JOutgoingConnectionBuilder =
      new JavaAdapter(actual.toHost(host).asInstanceOf[Impl])

    override def toPort(port: Int): JOutgoingConnectionBuilder = new JavaAdapter(actual.toPort(port).asInstanceOf[Impl])

    override def http()
        : JFlow[javadsl.model.HttpRequest, javadsl.model.HttpResponse, CompletionStage[javadsl.OutgoingConnection]] =
      javaFlow(actual.http())

    override def https()
        : JFlow[javadsl.model.HttpRequest, javadsl.model.HttpResponse, CompletionStage[javadsl.OutgoingConnection]] =
      javaFlow(actual.https())

    override def managedPersistentHttp2(): JFlow[javadsl.model.HttpRequest, javadsl.model.HttpResponse, NotUsed] =
      javaFlowKeepMatVal(actual.managedPersistentHttp2())

    override def http2WithPriorKnowledge()
        : JFlow[javadsl.model.HttpRequest, javadsl.model.HttpResponse, CompletionStage[javadsl.OutgoingConnection]] =
      javaFlow(actual.http2WithPriorKnowledge())

    override def managedPersistentHttp2WithPriorKnowledge()
        : JFlow[javadsl.model.HttpRequest, javadsl.model.HttpResponse, NotUsed] =
      javaFlowKeepMatVal(actual.managedPersistentHttp2WithPriorKnowledge())

    override def http2()
        : JFlow[javadsl.model.HttpRequest, javadsl.model.HttpResponse, CompletionStage[javadsl.OutgoingConnection]] =
      javaFlow(actual.http2())

    override def withCustomHttpsConnectionContext(
        httpsConnectionContext: javadsl.HttpsConnectionContext): JOutgoingConnectionBuilder =
      new JavaAdapter(actual.withCustomHttpsConnectionContext(
        httpsConnectionContext.asInstanceOf[HttpsConnectionContext]).asInstanceOf[Impl])

    override def withClientConnectionSettings(
        settings: pekko.http.javadsl.settings.ClientConnectionSettings): JOutgoingConnectionBuilder =
      new JavaAdapter(
        actual.withClientConnectionSettings(settings.asInstanceOf[ClientConnectionSettings]).asInstanceOf[Impl])

    override def logTo(logger: LoggingAdapter): JOutgoingConnectionBuilder =
      new JavaAdapter(actual.logTo(logger).asInstanceOf[Impl])

    private def javaFlow(flow: Flow[HttpRequest, HttpResponse, Future[OutgoingConnection]])
        : JFlow[javadsl.model.HttpRequest, javadsl.model.HttpResponse, CompletionStage[javadsl.OutgoingConnection]] = {
      import scala.jdk.FutureConverters._
      javaFlowKeepMatVal(flow.mapMaterializedValue(f =>
        f.map(oc => new javadsl.OutgoingConnection(oc))(ExecutionContext.parasitic).asJava))
    }

    private def javaFlowKeepMatVal[M](
        flow: Flow[HttpRequest, HttpResponse, M]): JFlow[javadsl.model.HttpRequest, javadsl.model.HttpResponse, M] =
      flow.asInstanceOf[Flow[javadsl.model.HttpRequest, javadsl.model.HttpResponse, M]].asJava
  }
}
