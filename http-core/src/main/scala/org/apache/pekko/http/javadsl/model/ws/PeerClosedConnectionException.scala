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

/**
 * The incoming WebSocket message stream fails with this exception when a Close frame from the peer is interpreted
 * as an error. `closeCode` and `closeReason` describe that error.
 *
 * When no message is in progress, a peer Close with no status code or a status of 1000 (normal closure) or 1001
 * (going away) completes the incoming stream without exposing the close code or reason through this exception.
 */
trait PeerClosedConnectionException extends RuntimeException {
  def closeCode: Int
  def closeReason: String
}
