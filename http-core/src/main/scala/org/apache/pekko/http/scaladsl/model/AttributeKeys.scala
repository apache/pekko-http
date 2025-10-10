/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.scaladsl.model

import org.apache.pekko.http.scaladsl.model.ws.WebSocketUpgrade

object AttributeKeys {
  val remoteAddress = AttributeKey[RemoteAddress]("remote-address")
  val webSocketUpgrade = AttributeKey[WebSocketUpgrade](name = "upgrade-to-websocket")
  val sslSession = AttributeKey[SslSessionInfo](name = "ssl-session")
  val trailer = AttributeKey[Trailer](name = "trailer")
}
