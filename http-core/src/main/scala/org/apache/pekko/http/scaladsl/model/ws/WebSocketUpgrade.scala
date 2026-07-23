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
import java.util.function.{ Predicate => JPredicate }
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
   * If `permessage-deflate` is negotiated, `shouldCompress` is evaluated once for each outbound text or binary
   * message. Returning `true` compresses that message and returning `false` sends it uncompressed. The filter does not
   * affect inbound messages or whether compression is negotiated.
   *
   * @since 2.0.0
   */
  def handleMessages(
      handlerFlow: Graph[FlowShape[Message, Message], Any],
      shouldCompress: Message => Boolean): HttpResponse =
    handleMessages(handlerFlow, None, shouldCompress)

  /**
   * The high-level interface to create a WebSocket server based on "messages".
   *
   * Optionally, a subprotocol out of the ones requested by the client can be chosen. If `permessage-deflate` is
   * negotiated, `shouldCompress` is evaluated once for each outbound text or binary message. Returning `true`
   * compresses that message and returning `false` sends it uncompressed. The filter does not affect inbound messages
   * or whether compression is negotiated.
   *
   * @since 2.0.0
   */
  def handleMessages(
      handlerFlow: Graph[FlowShape[Message, Message], Any],
      subprotocol: Option[String],
      shouldCompress: Message => Boolean): HttpResponse =
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

  /**
   * The high-level interface to create a WebSocket server based on "messages".
   *
   * If `permessage-deflate` is negotiated, `shouldCompress` is evaluated once for each outbound text or binary
   * message. Returning `true` compresses that message and returning `false` sends it uncompressed. The filter does not
   * affect inbound messages or whether compression is negotiated.
   *
   * @since 2.0.0
   */
  def handleMessagesWithSinkSource(
      inSink: Graph[SinkShape[Message], Any],
      outSource: Graph[SourceShape[Message], Any],
      shouldCompress: Message => Boolean): HttpResponse =
    handleMessages(scaladsl.Flow.fromSinkAndSource(inSink, outSource), None, shouldCompress)

  /**
   * The high-level interface to create a WebSocket server based on "messages".
   *
   * Optionally, a subprotocol out of the ones requested by the client can be chosen. If `permessage-deflate` is
   * negotiated, `shouldCompress` is evaluated once for each outbound text or binary message. Returning `true`
   * compresses that message and returning `false` sends it uncompressed. The filter does not affect inbound messages
   * or whether compression is negotiated.
   *
   * @since 2.0.0
   */
  def handleMessagesWithSinkSource(
      inSink: Graph[SinkShape[Message], Any],
      outSource: Graph[SourceShape[Message], Any],
      subprotocol: Option[String],
      shouldCompress: Message => Boolean): HttpResponse =
    handleMessages(scaladsl.Flow.fromSinkAndSource(inSink, outSource), subprotocol, shouldCompress)

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
   *
   * @since 2.0.0
   */
  override def handleMessagesWith(
      handlerFlow: Graph[FlowShape[jm.ws.Message, jm.ws.Message], ? <: Any],
      shouldCompress: JPredicate[jm.ws.Message]): HttpResponse =
    handleMessages(JavaMapping.toScala(handlerFlow), message => shouldCompress.test(message))

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
   *
   * @since 2.0.0
   */
  override def handleMessagesWith(
      handlerFlow: Graph[FlowShape[jm.ws.Message, jm.ws.Message], ? <: Any],
      subprotocol: String,
      shouldCompress: JPredicate[jm.ws.Message]): HttpResponse =
    handleMessages(JavaMapping.toScala(handlerFlow), Some(subprotocol), message => shouldCompress.test(message))

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
  override def handleMessagesWith(
      inSink: Graph[SinkShape[jm.ws.Message], ? <: Any],
      outSource: Graph[SourceShape[jm.ws.Message], ? <: Any],
      shouldCompress: JPredicate[jm.ws.Message]): HttpResponse =
    handleMessages(createScalaFlow(inSink, outSource), message => shouldCompress.test(message))

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

  /**
   * Java API
   *
   * @since 2.0.0
   */
  override def handleMessagesWith(
      inSink: Graph[SinkShape[jm.ws.Message], ? <: Any],
      outSource: Graph[SourceShape[jm.ws.Message], ? <: Any],
      subprotocol: String,
      shouldCompress: JPredicate[jm.ws.Message]): HttpResponse =
    handleMessages(createScalaFlow(inSink, outSource), Some(subprotocol), message => shouldCompress.test(message))

  private def createScalaFlow(inSink: Graph[SinkShape[jm.ws.Message], ? <: Any],
      outSource: Graph[SourceShape[jm.ws.Message], ? <: Any]): Graph[FlowShape[Message, Message], NotUsed] =
    JavaMapping.toScala(scaladsl.Flow.fromSinkAndSourceMat(inSink, outSource)(scaladsl.Keep.none): Graph[FlowShape[
        jm.ws.Message, jm.ws.Message], NotUsed])
}
