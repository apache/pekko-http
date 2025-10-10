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

package org.apache.pekko.http.javadsl

import java.util.concurrent.CompletionStage

import org.apache.pekko
import pekko.actor.ClassicActorSystemProvider
import pekko.event.LoggingAdapter
import pekko.http.impl.util.JavaMapping.Implicits._
import pekko.http.javadsl.model.{ HttpRequest, HttpResponse }
import pekko.http.javadsl.settings.ServerSettings
import pekko.http.scaladsl
import pekko.http.scaladsl.util.FastFuture
import pekko.http.scaladsl.{ model => sm }
import pekko.japi.function.Function
import pekko.stream.javadsl.{ Flow, Source }
import pekko.stream.{ Materializer, SystemMaterializer }

import scala.concurrent.ExecutionContext
import scala.jdk.FutureConverters._

/**
 * Builder API to create server bindings.
 *
 * Use [[Http.newServerAt()]] to create a builder, use methods to customize settings,
 * and then call one of the bind* methods to bind a server.
 */
trait ServerBuilder {

  /** Change interface to bind to */
  def onInterface(interface: String): ServerBuilder

  /** Change port to bind to */
  def onPort(port: Int): ServerBuilder

  /** Use a custom logger */
  def logTo(log: LoggingAdapter): ServerBuilder

  /**
   * Use custom [[ServerSettings]] for the binding.
   */
  def withSettings(settings: ServerSettings): ServerBuilder

  /**
   * Adapt the current configured settings with a function.
   */
  def adaptSettings(f: Function[ServerSettings, ServerSettings]): ServerBuilder

  /**
   * Enable HTTPS for this binding with the given context.
   */
  def enableHttps(context: HttpsConnectionContext): ServerBuilder

  /**
   * Use custom [[Materializer]] for the binding
   */
  def withMaterializer(materializer: Materializer): ServerBuilder

  /**
   * Bind a new HTTP server and use the given asynchronous `handler`
   * [[pekko.stream.javadsl.Flow]] for processing all incoming connections.
   *
   * The number of concurrently accepted connections can be configured by overriding
   * the `pekko.http.server.max-connections` setting. Please see the documentation in the reference.conf for more
   * information about what kind of guarantees to expect.
   *
   * Supports HTTP/2 on the same port if http2 support is enabled.
   */
  def bind(f: Function[HttpRequest, CompletionStage[HttpResponse]]): CompletionStage[ServerBinding]

  /**
   * Bind a new HTTP server and use the given handler provider to create an asynchronous `handler`
   * [[pekko.stream.javadsl.Flow]] for processing all incoming connections.
   *
   * Most importantly, you can pass a Route to this method because Route implements HandlerProvider.
   *
   * The number of concurrently accepted connections can be configured by overriding
   * the `pekko.http.server.max-connections` setting. Please see the documentation in the reference.conf for more
   * information about what kind of guarantees to expect.
   *
   * Supports HTTP/2 on the same port if http2 support is enabled.
   */
  def bind(handlerProvider: HandlerProvider): CompletionStage[ServerBinding]

  /**
   * Bind a new HTTP server at the given endpoint and uses the given `handler`
   * [[pekko.stream.javadsl.Flow]] for processing all incoming connections.
   *
   * The number of concurrently accepted connections can be configured by overriding
   * the `pekko.http.server.max-connections` setting. Please see the documentation in the reference.conf for more
   * information about what kind of guarantees to expect.
   *
   * Supports HTTP/2 on the same port if http2 support is enabled.
   */
  def bindSync(f: Function[HttpRequest, HttpResponse]): CompletionStage[ServerBinding]

  /**
   * Binds a new HTTP server at the given endpoint and uses the given `handler`
   * [[pekko.stream.scaladsl.Flow]] for processing all incoming connections.
   *
   * The number of concurrently accepted connections can be configured by overriding
   * the `pekko.http.server.max-connections` setting. Please see the documentation in the reference.conf for more
   * information about what kind of guarantees to expect.
   */
  def bindFlow(handlerFlow: Flow[HttpRequest, HttpResponse, _]): CompletionStage[ServerBinding]

  /**
   * Creates a [[pekko.stream.javadsl.Source]] of [[pekko.http.javadsl.IncomingConnection]] instances which represents a prospective HTTP server binding
   * on the given `endpoint`.
   *
   * Note that each materialization will create a new binding, so
   *
   *  * if the configured  port is 0 the resulting source can be materialized several times. Each materialization will
   * then be assigned a new local port by the operating system, which can then be retrieved by the materialized
   * [[pekko.http.javadsl.ServerBinding]].
   *
   *  * if the configured  port is non-zero subsequent materialization attempts of the produced source will immediately
   * fail, unless the first materialization has already been unbound. Unbinding can be triggered via the materialized
   * [[pekko.http.javadsl.ServerBinding]].
   */
  def connectionSource(): Source[IncomingConnection, CompletionStage[ServerBinding]]
}

/**
 * A HandlerProvider can provide an asynchronous request handler given an ClassicActorSystemProvider.
 *
 * The main use case for this class is to enable passing a Route to ServerBuilder.bind().
 */
trait HandlerProvider {
  def handler(system: ClassicActorSystemProvider): Function[HttpRequest, CompletionStage[HttpResponse]]
}

object ServerBuilder {
  private[http] def apply(interface: String, port: Int, system: ClassicActorSystemProvider): ServerBuilder =
    Impl(
      interface, port,
      scaladsl.HttpConnectionContext,
      system.classicSystem.log,
      ServerSettings.create(system.classicSystem),
      system,
      SystemMaterializer(system).materializer)

  private case class Impl(
      interface: String,
      port: Int,
      context: ConnectionContext,
      log: LoggingAdapter,
      settings: ServerSettings,
      system: ClassicActorSystemProvider,
      materializer: Materializer) extends ServerBuilder {
    private implicit def executionContext: ExecutionContext = system.classicSystem.dispatcher
    private def http: scaladsl.HttpExt = scaladsl.Http(system.classicSystem)

    def onInterface(newInterface: String): ServerBuilder = copy(interface = newInterface)
    def onPort(newPort: Int): ServerBuilder = copy(port = newPort)
    def logTo(newLog: LoggingAdapter): ServerBuilder = copy(log = newLog)
    def withSettings(newSettings: ServerSettings): ServerBuilder = copy(settings = newSettings)
    def adaptSettings(f: Function[ServerSettings, ServerSettings]): ServerBuilder = copy(settings = f(settings))
    def enableHttps(newContext: HttpsConnectionContext): ServerBuilder = copy(context = newContext)
    def withMaterializer(newMaterializer: Materializer): ServerBuilder = copy(materializer = newMaterializer)

    def bind(handler: Function[HttpRequest, CompletionStage[HttpResponse]]): CompletionStage[ServerBinding] =
      http.bindAndHandleAsyncImpl(
        handler.apply(_).asScala.map(_.asScala),
        interface, port, context.asScala, settings.asScala, parallelism = 0, log = log)(materializer)
        .map(new ServerBinding(_)).asJava

    def bind(handlerProvider: HandlerProvider): CompletionStage[ServerBinding] = bind(handlerProvider.handler(system))

    def bindSync(handler: Function[HttpRequest, HttpResponse]): CompletionStage[ServerBinding] =
      http.bindAndHandleAsyncImpl(
        req => FastFuture.successful(handler(req).asScala),
        interface, port, context.asScala, settings.asScala, parallelism = 0, log)(materializer)
        .map(new ServerBinding(_)).asJava

    def bindFlow(handlerFlow: Flow[HttpRequest, HttpResponse, _]): CompletionStage[ServerBinding] =
      http.bindAndHandleImpl(
        handlerFlow.asInstanceOf[Flow[sm.HttpRequest, sm.HttpResponse, _]].asScala,
        interface, port, context.asScala, settings.asScala, log)(materializer)
        .map(new ServerBinding(_)).asJava

    def connectionSource(): Source[IncomingConnection, CompletionStage[ServerBinding]] =
      http.bindImpl(interface, port, context.asScala, settings.asScala, log)
        .map(new IncomingConnection(_))
        .mapMaterializedValue(_.map(new ServerBinding(_))(ExecutionContext.parasitic).asJava).asJava
  }
}
