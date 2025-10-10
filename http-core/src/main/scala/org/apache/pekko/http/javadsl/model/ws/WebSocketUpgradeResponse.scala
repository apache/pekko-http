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

import java.util.Optional

import org.apache.pekko
import pekko.http.javadsl.model.HttpResponse
import pekko.http.scaladsl
import pekko.http.scaladsl.model.ws.{ InvalidUpgradeResponse, ValidUpgrade }

/**
 * Represents an upgrade response for a WebSocket upgrade request. Can either be valid, in which
 * case the `chosenSubprotocol` method is valid, or if invalid, the `invalidationReason` method
 * can be used to find out why the upgrade failed.
 */
trait WebSocketUpgradeResponse {
  def isValid: Boolean

  /**
   * Returns the response object as received from the server for further inspection.
   */
  def response: HttpResponse

  /**
   * If valid, returns `Some(subprotocol)` (if any was requested), or `None` if none was
   * chosen or offered.
   */
  def chosenSubprotocol: Optional[String]

  /**
   * If invalid, the reason why the server's upgrade response could not be accepted.
   */
  def invalidationReason: String
}

object WebSocketUpgradeResponse {
  import pekko.http.impl.util.JavaMapping.Implicits._
  def adapt(scalaResponse: scaladsl.model.ws.WebSocketUpgradeResponse): WebSocketUpgradeResponse =
    scalaResponse match {
      case ValidUpgrade(resp, chosen) =>
        new WebSocketUpgradeResponse {
          def isValid: Boolean = true
          def response: HttpResponse = resp
          def chosenSubprotocol: Optional[String] = chosen.asJava
          def invalidationReason: String =
            throw new UnsupportedOperationException("invalidationReason must not be called for valid response")
        }
      case InvalidUpgradeResponse(resp, cause) =>
        new WebSocketUpgradeResponse {
          def isValid: Boolean = false
          def response: HttpResponse = resp
          def chosenSubprotocol: Optional[String] =
            throw new UnsupportedOperationException("chosenSubprotocol must not be called for valid response")
          def invalidationReason: String = cause
        }
    }

}
