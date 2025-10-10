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

package org.apache.pekko.http.javadsl.server
package directives

import java.util.{ List => JList }
import java.util.Optional
import java.util.function.{ Function => JFunction }

import org.apache.pekko
import pekko.NotUsed
import pekko.http.javadsl.model.ws.Message
import pekko.http.javadsl.model.ws.WebSocketUpgrade
import pekko.http.scaladsl.model.{ ws => s }
import pekko.http.scaladsl.server.{ Directives => D }
import pekko.stream.javadsl.Flow
import pekko.stream.scaladsl

abstract class WebSocketDirectives extends SecurityDirectives {
  import pekko.http.impl.util.JavaMapping.Implicits._

  /**
   * Extract the WebSocketUpgrade attribute if this is a WebSocket request.
   * Rejects with an [[ExpectedWebSocketRequestRejection]], otherwise.
   */
  def extractWebSocketUpgrade(inner: JFunction[WebSocketUpgrade, Route]): Route = RouteAdapter {
    D.extractWebSocketUpgrade { header =>
      inner.apply(header).delegate
    }
  }

  /**
   * Extract the list of WebSocket subprotocols as offered by the client in the [[Sec-WebSocket-Protocol]] header if
   * this is a WebSocket request. Rejects with an [[ExpectedWebSocketRequestRejection]], otherwise.
   */
  def extractOfferedWsProtocols(inner: JFunction[JList[String], Route]): Route = RouteAdapter {
    import scala.jdk.CollectionConverters._
    D.extractOfferedWsProtocols { (list: Seq[String]) =>
      inner.apply(list.asJava).delegate
    }
  }

  /**
   * Handles WebSocket requests with the given handler and rejects other requests with an
   * [[ExpectedWebSocketRequestRejection]].
   */
  def handleWebSocketMessages[T](handler: Flow[Message, Message, T]): Route = RouteAdapter {
    D.handleWebSocketMessages(adapt(handler))
  }

  /**
   * Handles WebSocket requests with the given handler if the given subprotocol is offered in the request and
   * rejects other requests with an [[ExpectedWebSocketRequestRejection]] or an [[UnsupportedWebSocketSubprotocolRejection]].
   */
  def handleWebSocketMessagesForProtocol[T](handler: Flow[Message, Message, T], subprotocol: String): Route =
    RouteAdapter {
      D.handleWebSocketMessagesForProtocol(adapt(handler), subprotocol)
    }

  /**
   * Handles WebSocket requests with the given handler and rejects other requests with an
   * [[ExpectedWebSocketRequestRejection]].
   *
   * If the `subprotocol` parameter is None any WebSocket request is accepted. If the `subprotocol` parameter is
   * `Some(protocol)` a WebSocket request is only accepted if the list of subprotocols supported by the client (as
   * announced in the WebSocket request) contains `protocol`. If the client did not offer the protocol in question
   * the request is rejected with an [[UnsupportedWebSocketSubprotocolRejection]] rejection.
   *
   * To support several subprotocols you may chain several `handleWebSocketMessagesForOptionalProtocol` routes.
   */
  def handleWebSocketMessagesForOptionalProtocol[T](
      handler: Flow[Message, Message, T], subprotocol: Optional[String]): Route = RouteAdapter {
    D.handleWebSocketMessagesForOptionalProtocol(adapt(handler), subprotocol.asScala)
  }

  private def adapt[T](handler: Flow[Message, Message, T]): scaladsl.Flow[s.Message, s.Message, NotUsed] = {
    scaladsl.Flow[s.Message].map(_.asJava).via(handler).map(_.asScala)
  }
}
