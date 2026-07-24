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

package org.apache.pekko.http.javadsl.model.ws

import java.lang.{ Iterable => JIterable }
import java.util.function.Predicate

import org.apache.pekko
import pekko.http.javadsl.model.HttpResponse
import pekko.stream.{ FlowShape, Graph, SinkShape, SourceShape }

/**
 * An attribute that WebSocket requests will contain. Use `WebSocketUpgrade.handleMessagesWith` to
 * create a WebSocket handshake response and handle the WebSocket message stream with the given handler.
 *
 * This is a low-level API. You might want to use `handleWebSocketMessages` instead as documented
 * at https://pekko.apache.org/docs/pekko-http/current/server-side/websocket-support.html#routing-support
 */
trait WebSocketUpgrade {

  /**
   * Returns the sequence of protocols the client accepts.
   *
   * See http://tools.ietf.org/html/rfc6455#section-1.9
   */
  def getRequestedProtocols(): JIterable[String]

  /**
   * Returns a response that can be used to answer a WebSocket handshake request. The connection will afterwards
   * use the given handlerFlow to handle WebSocket messages from the client.
   */
  def handleMessagesWith(handlerFlow: Graph[FlowShape[Message, Message], ? <: Any]): HttpResponse

  /**
   * Returns a response that can be used to answer a WebSocket handshake request. The connection will afterwards
   * use the given handlerFlow to handle WebSocket messages from the client.
   *
   * The compressionEnabled flag allows declining negotiated WebSocket compression for this accepted WebSocket.
   *
   * @since 2.0.0
   */
  def handleMessagesWith(
      handlerFlow: Graph[FlowShape[Message, Message], ? <: Any], compressionEnabled: Boolean): HttpResponse

  /**
   * Returns a response that can be used to answer a WebSocket handshake request. The connection will afterwards
   * use the given handlerFlow to handle WebSocket messages from the client.
   *
   * If {@code permessage-deflate} is negotiated, {@code shouldCompress} is evaluated synchronously once for each
   * outbound text or binary message. The result applies to every fragment of that message. Returning {@code true}
   * compresses the message and returning {@code false} sends it uncompressed. The filter is not invoked for control
   * frames or when compression was not negotiated, and it does not affect inbound messages or whether compression is
   * negotiated.
   *
   * The filter should be fast and non-blocking; if it throws, the WebSocket stream fails. For streamed messages, the
   * complete payload and its final size are not available when the filter is evaluated.
   *
   * @since 2.0.0
   */
  def handleMessagesWith(
      handlerFlow: Graph[FlowShape[Message, Message], ? <: Any], shouldCompress: Predicate[Message]): HttpResponse =
    handleMessagesWith(handlerFlow)

  /**
   * Returns a response that can be used to answer a WebSocket handshake request. The connection will afterwards
   * use the given handlerFlow to handle WebSocket messages from the client. The given subprotocol must be one
   * of the ones offered by the client.
   */
  def handleMessagesWith(handlerFlow: Graph[FlowShape[Message, Message], ? <: Any], subprotocol: String): HttpResponse

  /**
   * Returns a response that can be used to answer a WebSocket handshake request. The connection will afterwards
   * use the given handlerFlow to handle WebSocket messages from the client. The given subprotocol must be one
   * of the ones offered by the client.
   *
   * The compressionEnabled flag allows declining negotiated WebSocket compression for this accepted WebSocket.
   *
   * @since 2.0.0
   */
  def handleMessagesWith(
      handlerFlow: Graph[FlowShape[Message, Message], ? <: Any],
      subprotocol: String,
      compressionEnabled: Boolean): HttpResponse

  /**
   * Returns a response that can be used to answer a WebSocket handshake request. The connection will afterwards
   * use the given handlerFlow to handle WebSocket messages from the client. The given subprotocol must be one
   * of the ones offered by the client.
   *
   * If {@code permessage-deflate} is negotiated, {@code shouldCompress} is evaluated synchronously once for each
   * outbound text or binary message. The result applies to every fragment of that message. Returning {@code true}
   * compresses the message and returning {@code false} sends it uncompressed. The filter is not invoked for control
   * frames or when compression was not negotiated, and it does not affect inbound messages or whether compression is
   * negotiated.
   *
   * The filter should be fast and non-blocking; if it throws, the WebSocket stream fails. For streamed messages, the
   * complete payload and its final size are not available when the filter is evaluated.
   *
   * @since 2.0.0
   */
  def handleMessagesWith(
      handlerFlow: Graph[FlowShape[Message, Message], ? <: Any],
      subprotocol: String,
      shouldCompress: Predicate[Message]): HttpResponse =
    handleMessagesWith(handlerFlow, subprotocol)

  /**
   * Returns a response that can be used to answer a WebSocket handshake request. The connection will afterwards
   * use the given inSink to handle WebSocket messages from the client and the given outSource to send messages to the client.
   */
  def handleMessagesWith(
      inSink: Graph[SinkShape[Message], ? <: Any], outSource: Graph[SourceShape[Message], ? <: Any]): HttpResponse

  /**
   * Returns a response that can be used to answer a WebSocket handshake request. The connection will afterwards
   * use the given inSink to handle WebSocket messages from the client and the given outSource to send messages to the client.
   *
   * The compressionEnabled flag allows declining negotiated WebSocket compression for this accepted WebSocket.
   *
   * @since 2.0.0
   */
  def handleMessagesWith(inSink: Graph[SinkShape[Message], ? <: Any], outSource: Graph[SourceShape[Message], ? <: Any],
      compressionEnabled: Boolean): HttpResponse

  /**
   * Returns a response that can be used to answer a WebSocket handshake request. The connection will afterwards
   * use the given inSink to handle WebSocket messages from the client and the given outSource to send messages to the
   * client.
   *
   * If {@code permessage-deflate} is negotiated, {@code shouldCompress} is evaluated synchronously once for each
   * outbound text or binary message. The result applies to every fragment of that message. Returning {@code true}
   * compresses the message and returning {@code false} sends it uncompressed. The filter is not invoked for control
   * frames or when compression was not negotiated, and it does not affect inbound messages or whether compression is
   * negotiated.
   *
   * The filter should be fast and non-blocking; if it throws, the WebSocket stream fails. For streamed messages, the
   * complete payload and its final size are not available when the filter is evaluated.
   *
   * @since 2.0.0
   */
  def handleMessagesWith(
      inSink: Graph[SinkShape[Message], ? <: Any],
      outSource: Graph[SourceShape[Message], ? <: Any],
      shouldCompress: Predicate[Message]): HttpResponse =
    handleMessagesWith(inSink, outSource)

  /**
   * Returns a response that can be used to answer a WebSocket handshake request. The connection will afterwards
   * use the given inSink to handle WebSocket messages from the client and the given outSource to send messages to the client.
   *
   * The given subprotocol must be one of the ones offered by the client.
   */
  def handleMessagesWith(inSink: Graph[SinkShape[Message], ? <: Any], outSource: Graph[SourceShape[Message], ? <: Any],
      subprotocol: String): HttpResponse

  /**
   * Returns a response that can be used to answer a WebSocket handshake request. The connection will afterwards
   * use the given inSink to handle WebSocket messages from the client and the given outSource to send messages to the client.
   *
   * The given subprotocol must be one of the ones offered by the client.
   * The compressionEnabled flag allows declining negotiated WebSocket compression for this accepted WebSocket.
   *
   * @since 2.0.0
   */
  def handleMessagesWith(inSink: Graph[SinkShape[Message], ? <: Any], outSource: Graph[SourceShape[Message], ? <: Any],
      subprotocol: String, compressionEnabled: Boolean): HttpResponse

  /**
   * Returns a response that can be used to answer a WebSocket handshake request. The connection will afterwards
   * use the given inSink to handle WebSocket messages from the client and the given outSource to send messages to the
   * client. The given subprotocol must be one of the ones offered by the client.
   *
   * If {@code permessage-deflate} is negotiated, {@code shouldCompress} is evaluated synchronously once for each
   * outbound text or binary message. The result applies to every fragment of that message. Returning {@code true}
   * compresses the message and returning {@code false} sends it uncompressed. The filter is not invoked for control
   * frames or when compression was not negotiated, and it does not affect inbound messages or whether compression is
   * negotiated.
   *
   * The filter should be fast and non-blocking; if it throws, the WebSocket stream fails. For streamed messages, the
   * complete payload and its final size are not available when the filter is evaluated.
   *
   * @since 2.0.0
   */
  def handleMessagesWith(
      inSink: Graph[SinkShape[Message], ? <: Any],
      outSource: Graph[SourceShape[Message], ? <: Any],
      subprotocol: String,
      shouldCompress: Predicate[Message]): HttpResponse =
    handleMessagesWith(inSink, outSource, subprotocol)
}
