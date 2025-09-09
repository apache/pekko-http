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

package org.apache.pekko.http.scaladsl.testkit

import org.apache.pekko
import pekko.http.impl.engine.server.InternalCustomHeader
import pekko.http.scaladsl.model.headers.{ `Sec-WebSocket-Protocol`, Upgrade, UpgradeProtocol }
import pekko.http.scaladsl.model.{ HttpRequest, HttpResponse, StatusCodes, Uri }
import pekko.http.scaladsl.model.ws.{ Message, WebSocketUpgrade }
import pekko.http.scaladsl.model.AttributeKeys.webSocketUpgrade
import scala.collection.immutable
import pekko.stream.{ FlowShape, Graph, Materializer }
import pekko.stream.scaladsl.Flow

trait WSTestRequestBuilding {
  def WS(uri: Uri, clientSideHandler: Flow[Message, Message, Any], subprotocols: Seq[String] = Nil)(
      implicit materializer: Materializer): HttpRequest = {
    val upgrade =
      new InternalCustomHeader("UpgradeToWebSocketTestHeader") with WebSocketUpgrade {
        def requestedProtocols: immutable.Seq[String] = subprotocols.toList

        def handleMessages(
            handlerFlow: Graph[FlowShape[Message, Message], Any], subprotocol: Option[String]): HttpResponse = {
          clientSideHandler.join(handlerFlow).run()
          HttpResponse(
            StatusCodes.SwitchingProtocols,
            headers =
              Upgrade(UpgradeProtocol("websocket") :: Nil) ::
              subprotocol.map(p => `Sec-WebSocket-Protocol`(p :: Nil)).toList)
        }
      }
    HttpRequest(uri = uri)
      .addAttribute(webSocketUpgrade, upgrade)
      .addHeader(upgrade)
  }
}

object WSTestRequestBuilding extends WSTestRequestBuilding
