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

package org.apache.pekko.http.impl.engine.ws

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.http.impl.engine.server.InternalCustomHeader
import pekko.http.scaladsl.model.HttpResponse
import pekko.http.scaladsl.model.ws.WebSocketUpgrade
import pekko.stream.{ FlowShape, Graph }

/**
 * Currently internal API to handle FrameEvents directly.
 *
 * INTERNAL API
 */
@InternalApi
private[http] abstract class UpgradeToWebSocketLowLevel extends InternalCustomHeader("UpgradeToWebSocket")
    with WebSocketUpgrade {

  /**
   * The low-level interface to create WebSocket server based on "frames".
   * The user needs to handle control frames manually in this case.
   *
   * Returns a response to return in a request handler that will signal the
   * low-level HTTP implementation to upgrade the connection to WebSocket and
   * use the supplied handler to handle incoming WebSocket frames.
   *
   * INTERNAL API (for now)
   */
  @InternalApi
  private[http] def handleFrames(handlerFlow: Graph[FlowShape[FrameEvent, FrameEvent], Any],
      subprotocol: Option[String] = None): HttpResponse
}
