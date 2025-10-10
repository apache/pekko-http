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

package org.apache.pekko.http.javadsl

import java.util.concurrent.CompletionStage
import java.net.InetSocketAddress

import org.apache.pekko
import pekko.NotUsed
import pekko.japi.function.Function
import pekko.stream.Materializer
import pekko.stream.javadsl.Flow
import pekko.http.javadsl.model._
import pekko.http.scaladsl.{ model => sm }

import scala.concurrent.Future
import scala.jdk.FutureConverters._

/**
 * Represents one accepted incoming HTTP connection.
 */
class IncomingConnection private[http] (delegate: pekko.http.scaladsl.Http.IncomingConnection) {

  /**
   * The local address of this connection.
   */
  def localAddress: InetSocketAddress = delegate.localAddress

  /**
   * The address of the remote peer.
   */
  def remoteAddress: InetSocketAddress = delegate.remoteAddress

  /**
   * A flow representing the incoming requests and outgoing responses for this connection.
   *
   * Use `Flow.join` or one of the handleXXX methods to consume handle requests on this connection.
   */
  def flow: Flow[HttpResponse, HttpRequest, NotUsed] =
    Flow.fromGraph(delegate.flow).asInstanceOf[Flow[HttpResponse, HttpRequest, NotUsed]]

  /**
   * Handles the connection with the given flow, which is materialized exactly once
   * and the respective materialization result returned.
   */
  def handleWith[Mat](handler: Flow[HttpRequest, HttpResponse, Mat], materializer: Materializer): Mat =
    delegate.handleWith(handler.asInstanceOf[Flow[sm.HttpRequest, sm.HttpResponse, Mat]].asScala)(materializer)

  /**
   * Handles the connection with the given handler function.
   */
  def handleWithSyncHandler(handler: Function[HttpRequest, HttpResponse], materializer: Materializer): Unit =
    delegate.handleWithSyncHandler(handler.apply(_).asInstanceOf[sm.HttpResponse])(materializer)

  /**
   * Handles the connection with the given handler function.
   */
  def handleWithAsyncHandler(
      handler: Function[HttpRequest, CompletionStage[HttpResponse]], materializer: Materializer): Unit =
    delegate.handleWithAsyncHandler(handler.apply(_).asScala.asInstanceOf[Future[sm.HttpResponse]])(materializer)

  /**
   * Handles the connection with the given handler function.
   */
  def handleWithAsyncHandler(handler: Function[HttpRequest, CompletionStage[HttpResponse]], parallelism: Int,
      materializer: Materializer): Unit =
    delegate.handleWithAsyncHandler(handler.apply(_).asScala.asInstanceOf[Future[sm.HttpResponse]], parallelism)(
      materializer)
}
