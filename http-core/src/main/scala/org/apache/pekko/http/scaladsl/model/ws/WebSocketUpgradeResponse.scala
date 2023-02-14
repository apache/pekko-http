/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.scaladsl.model.ws

import org.apache.pekko.http.scaladsl.model.HttpResponse

/**
 * Represents the response to a websocket upgrade request. Can either be [[ValidUpgrade]] or [[InvalidUpgradeResponse]].
 */
sealed trait WebSocketUpgradeResponse {
  def response: HttpResponse
}
final case class ValidUpgrade(response: HttpResponse, chosenSubprotocol: Option[String])
    extends WebSocketUpgradeResponse
final case class InvalidUpgradeResponse(response: HttpResponse, cause: String) extends WebSocketUpgradeResponse