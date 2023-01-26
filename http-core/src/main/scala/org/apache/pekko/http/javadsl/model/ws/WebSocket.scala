/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model.ws

import org.apache.pekko
import pekko.stream.javadsl.Flow
import pekko.http.javadsl.model._
import pekko.http.impl.util.JavaMapping.Implicits._

object WebSocket {

  /**
   * If a given request is a WebSocket request a response accepting the request is returned using the given handler to
   * handle the WebSocket message stream. If the request wasn't a WebSocket request a response with status code 400 is
   * returned.
   */
  def handleWebSocketRequestWith(request: HttpRequest, handler: Flow[Message, Message, _]): HttpResponse =
    request.asScala.attribute(AttributeKeys.webSocketUpgrade) match {
      case Some(header) => header.handleMessagesWith(handler)
      case None         => HttpResponse.create().withStatus(StatusCodes.BAD_REQUEST).withEntity("Expected WebSocket request")
    }
}
