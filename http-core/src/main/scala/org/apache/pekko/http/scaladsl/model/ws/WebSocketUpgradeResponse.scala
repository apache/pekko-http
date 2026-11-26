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

import org.apache.pekko.http.scaladsl.model.HttpResponse

/**
 * Represents the response to a websocket upgrade request. Can either be [[ValidUpgrade]] or [[InvalidUpgradeResponse]].
 */
sealed trait WebSocketUpgradeResponse {

  /**
   * Returns the HTTP response for inspection of its status and parsed headers.
   * For responses returned by the WebSocket client, the entity is empty even if the server sent a body.
   */
  def response: HttpResponse
}
final case class ValidUpgrade(response: HttpResponse, chosenSubprotocol: Option[String])
    extends WebSocketUpgradeResponse
final case class InvalidUpgradeResponse(response: HttpResponse, cause: String) extends WebSocketUpgradeResponse
