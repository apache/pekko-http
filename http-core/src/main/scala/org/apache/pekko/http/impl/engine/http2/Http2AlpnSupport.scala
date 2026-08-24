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

package org.apache.pekko.http.impl.engine.http2

import org.apache.pekko
import pekko.annotation.InternalApi

import java.{ util => ju }
import javax.net.ssl.SSLEngine

/**
 * INTERNAL API
 *
 * ALPN support, which every JDK this project builds against provides natively.
 */
@InternalApi
private[http] object Http2AlpnSupport {
  // ALPN Protocol IDs https://www.iana.org/assignments/tls-extensiontype-values/tls-extensiontype-values.xhtml#alpn-protocol-ids
  val H2 = "h2"
  val HTTP11 = "http/1.1"

  /**
   * Enables server-side Http/2 ALPN support for the given engine.
   */
  def enableForServer(engine: SSLEngine, setChosenProtocol: String => Unit): SSLEngine = {
    engine.setHandshakeApplicationProtocolSelector { (_: SSLEngine, protocols: ju.List[String]) =>
      val chosen = chooseProtocol(protocols)
      chosen.foreach(setChosenProtocol)

      // returning null here means aborting the handshake
      // see https://docs.oracle.com/en/java/javase/11/docs/api/java.base/javax/net/ssl/SSLEngine.html#setHandshakeApplicationProtocolSelector(java.util.function.BiFunction)
      chosen.orNull
    }

    engine
  }

  def clientSetApplicationProtocols(engine: SSLEngine, protocols: Array[String]): Unit = {
    val params = engine.getSSLParameters
    params.setApplicationProtocols(protocols)
    engine.setSSLParameters(params)
  }

  private def chooseProtocol(protocols: ju.List[String]): Option[String] =
    if (protocols.contains(H2)) Some(H2)
    else if (protocols.contains(HTTP11)) Some(HTTP11)
    else None
}
