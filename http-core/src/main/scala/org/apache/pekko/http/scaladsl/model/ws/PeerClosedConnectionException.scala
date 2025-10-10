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

import org.apache.pekko.http.javadsl

/**
 * A PeerClosedConnectionException will be reported to the WebSocket handler if the peer has closed the connection.
 * `closeCode` and `closeReason` contain close messages as reported by the peer.
 */
class PeerClosedConnectionException(val closeCode: Int, val closeReason: String)
    extends RuntimeException(s"Peer closed connection with code $closeCode '$closeReason'")
    with javadsl.model.ws.PeerClosedConnectionException
