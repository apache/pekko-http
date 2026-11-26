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

package org.apache.pekko.http.javadsl.model.ws

import org.apache.pekko
import pekko.http.impl.engine.ws.FrameOutHandler
import pekko.http.impl.util.JavaMapping.Implicits._
import pekko.http.javadsl.model._
import pekko.stream.javadsl.Flow

object WebSocket {

  /**
   * If a given request is a WebSocket request a response accepting the request is returned using the given handler to
   * handle the WebSocket message stream. If the request wasn't a WebSocket request a response with status code 400 is
   * returned.
   */
  def handleWebSocketRequestWith(request: HttpRequest, handler: Flow[? >: Message, ? <: Message, ?]): HttpResponse =
    request.asScala.attribute(AttributeKeys.webSocketUpgrade) match {
      case Some(header) => header.handleMessagesWith(handler)
      case None         => HttpResponse.create().withStatus(StatusCodes.BAD_REQUEST).withEntity("Expected WebSocket request")
    }

  /**
   * Sets the Close status and reason sent by a server WebSocket message handler when its outgoing stream
   * completes normally before the peer initiates closing. Apply this to the response returned by any message
   * handler overload; it does not change the negotiated subprotocol, compression, peer-initiated closes or failures.
   * Other responses are rendered normally. The status must be permitted for servers by RFC 6455, and the reason
   * must encode to at most 123 UTF-8 bytes.
   *
   * @since 2.1.0
   */
  def withCompletionClose(response: HttpResponse, closeCode: Int, closeReason: String): HttpResponse =
    response.addAttribute(FrameOutHandler.completionCloseKey,
      FrameOutHandler.completionClose(closeCode, closeReason))
}
