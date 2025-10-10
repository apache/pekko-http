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

package org.apache.pekko.http.impl.engine.server

import java.net.InetSocketAddress
import javax.net.ssl.SSLSession

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.stream.Attributes

/**
 * INTERNAL API
 * Internally used attributes set in the HTTP pipeline.
 * May potentially be opened up in the future.
 */
@InternalApi
private[pekko] object HttpAttributes {
  import Attributes._

  private[pekko] final case class RemoteAddress(address: InetSocketAddress) extends Attribute

  private[pekko] def remoteAddress(address: Option[InetSocketAddress]): Attributes =
    address match {
      case Some(addr) => remoteAddress(addr)
      case None       => empty
    }

  private[pekko] def remoteAddress(address: InetSocketAddress): Attributes =
    Attributes(RemoteAddress(address))

  /**
   * INTERNAL API
   * Internally used TLS session info in the HTTP pipeline.
   */
  @InternalApi
  private[pekko] final case class TLSSessionInfo(session: SSLSession) extends Attribute

  private[pekko] def tlsSessionInfo(session: SSLSession): Attributes =
    Attributes(TLSSessionInfo(session))

  private[pekko] val empty: Attributes =
    Attributes()
}
