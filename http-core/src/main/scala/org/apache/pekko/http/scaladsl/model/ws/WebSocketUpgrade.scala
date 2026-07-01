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

package org.apache.pekko.http.scaladsl.model.ws

import java.lang.Iterable
import scala.collection.immutable
import org.apache.pekko
import pekko.NotUsed
import pekko.stream._
import pekko.http.impl.util.JavaMapping
import pekko.http.javadsl.{ model => jm }
import pekko.http.scaladsl.model.HttpResponse

/**
 * An attribute that will be added to an WebSocket upgrade HttpRequest that
 * enables a request handler to upgrade this connection to a WebSocket connection and
 * registers a WebSocket handler.
 *
 * This is a low-level API. You might want to use `handleWebSocketMessages` instead as documented
 * at https://pekko.apache.org/docs/pekko-http/current/server-side/websocket-support.html#routing-support
 */
trait WebSocketUpgrade extends jm.ws.WebSocketUpgrade {

  /**
   * A sequence of protocols the client accepts.
   *
   * See http://tools.ietf.org/html/rfc6455#section-1.9
   */
  def requestedProtocols: immutable.Seq[String]

  /**
   * The high-level interface to create a WebSocket server based on "messages".
   *
   * Returns a response to return in a request handler that will signal the
   * low-level HTTP implementation to upgrade the connection to WebSocket and
   * use the supplied handler to handle incoming WebSocket messages.
   *
   * Optionally, a subprotocol out of the ones requested by the client can be chosen.
   */
  def handleMessages(
      handlerFlow: Graph[FlowShape[Message, Message], Any],
      subprotocol: Option[String] = None): HttpResponse

  /**
   * The high-level interface to create a WebSocket server based on "messages".
   *
   * Returns a response to return in a request handler that will signal the
   * low-level HTTP implementation to upgrade the connection to WebSocket and
   * use the supplied handler to handle incoming WebSocket messages.
   *
   * Optionally, a subprotocol out of the ones requested by the client can be chosen.
   * The `compressionEnabled` flag allows declining negotiated WebSocket compression
   * for this accepted WebSocket.
   *
   * @since 2.0.0
   */
  def handleMessages(
      handlerFlow: Graph[FlowShape[Message, Message], Any],
      subprotocol: Option[String],
      compressionEnabled: Boolean): HttpResponse =
    handleMessages(handlerFlow, subprotocol)

  /**
   * The high-level interface to create a WebSocket server based on "messages".
   *
   * Returns a response to return in a request handler that will signal the
   * low-level HTTP implementation to upgrade the connection to WebSocket and
   * use the supplied inSink to consume messages received from the client and
   * the supplied outSource to produce message to sent to the client.
   *
   * Optionally, a subprotocol out of the ones requested by the client can be chosen.
   */
  def handleMessagesWithSinkSource(
      inSink: Graph[SinkShape[Message], Any],
      outSource: Graph[SourceShape[Message], Any],
      subprotocol: Option[String] = None): HttpResponse =
    handleMessages(scaladsl.Flow.fromSinkAndSource(inSink, outSource), subprotocol)

  /**
   * The high-level interface to create a WebSocket server based on "messages".
   *
   * Returns a response to return in a request handler that will signal the
   * low-level HTTP implementation to upgrade the connection to WebSocket and
   * use the supplied inSink to consume messages received from the client and
   * the supplied outSource to produce message to sent to the client.
   *
   * Optionally, a subprotocol out of the ones requested by the client can be chosen.
   * The `compressionEnabled` flag allows declining negotiated WebSocket compression
   * for this accepted WebSocket.
   *
   * @since 2.0.0
   */
  def handleMessagesWithSinkSource(
      inSink: Graph[SinkShape[Message], Any],
      outSource: Graph[SourceShape[Message], Any],
      subprotocol: Option[String],
      compressionEnabled: Boolean): HttpResponse =
    handleMessages(scaladsl.Flow.fromSinkAndSource(inSink, outSource), subprotocol, compressionEnabled)

  import scala.jdk.CollectionConverters._

  /**
   * Java API
   */
  def getRequestedProtocols(): Iterable[String] = requestedProtocols.asJava

  /**
   * Java API
   */
  def handleMessagesWith(handlerFlow: Graph[FlowShape[jm.ws.Message, jm.ws.Message], ? <: Any]): HttpResponse =
    handleMessages(JavaMapping.toScala(handlerFlow))

  /**
   * Java API
   *
   * @since 2.0.0
   */
  def handleMessagesWith(
      handlerFlow: Graph[FlowShape[jm.ws.Message, jm.ws.Message], ? <: Any],
      compressionEnabled: Boolean): HttpResponse =
    handleMessages(JavaMapping.toScala(handlerFlow), None, compressionEnabled)

  /**
   * Java API
   */
  def handleMessagesWith(
      handlerFlow: Graph[FlowShape[jm.ws.Message, jm.ws.Message], ? <: Any], subprotocol: String): HttpResponse =
    handleMessages(JavaMapping.toScala(handlerFlow), subprotocol = Some(subprotocol))

  /**
   * Java API
   *
   * @since 2.0.0
   */
  def handleMessagesWith(
      handlerFlow: Graph[FlowShape[jm.ws.Message, jm.ws.Message], ? <: Any],
      subprotocol: String,
      compressionEnabled: Boolean): HttpResponse =
    handleMessages(JavaMapping.toScala(handlerFlow), subprotocol = Some(subprotocol), compressionEnabled)

  /**
   * Java API
   */
  def handleMessagesWith(inSink: Graph[SinkShape[jm.ws.Message], ? <: Any],
      outSource: Graph[SourceShape[jm.ws.Message], ? <: Any]): HttpResponse =
    handleMessages(createScalaFlow(inSink, outSource))

  /**
   * Java API
   *
   * @since 2.0.0
   */
  def handleMessagesWith(
      inSink: Graph[SinkShape[jm.ws.Message], ? <: Any],
      outSource: Graph[SourceShape[jm.ws.Message], ? <: Any],
      compressionEnabled: Boolean): HttpResponse =
    handleMessages(createScalaFlow(inSink, outSource), None, compressionEnabled)

  /**
   * Java API
   *
   * @since 2.0.0
   */
  def handleMessagesWith(
      inSink: Graph[SinkShape[jm.ws.Message], ? <: Any],
      outSource: Graph[SourceShape[jm.ws.Message], ? <: Any],
      subprotocol: String): HttpResponse =
    handleMessages(createScalaFlow(inSink, outSource), subprotocol = Some(subprotocol))

  /**
   * Java API
   *
   * @since 2.0.0
   */
  def handleMessagesWith(
      inSink: Graph[SinkShape[jm.ws.Message], ? <: Any],
      outSource: Graph[SourceShape[jm.ws.Message], ? <: Any],
      subprotocol: String,
      compressionEnabled: Boolean): HttpResponse =
    handleMessages(createScalaFlow(inSink, outSource), subprotocol = Some(subprotocol), compressionEnabled)

  private def createScalaFlow(inSink: Graph[SinkShape[jm.ws.Message], ? <: Any],
      outSource: Graph[SourceShape[jm.ws.Message], ? <: Any]): Graph[FlowShape[Message, Message], NotUsed] =
    JavaMapping.toScala(scaladsl.Flow.fromSinkAndSourceMat(inSink, outSource)(scaladsl.Keep.none): Graph[FlowShape[
        jm.ws.Message, jm.ws.Message], NotUsed])
}
