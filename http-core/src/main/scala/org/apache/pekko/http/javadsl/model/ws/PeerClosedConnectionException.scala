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
 * A PeerClosedConnectionException will be reported to the WebSocket handler if the peer has closed the connection.
 * `closeCode` and `closeReason` contain close messages as reported by the peer.
 */
trait PeerClosedConnectionException extends RuntimeException {
  def closeCode: Int
  def closeReason: String
}
