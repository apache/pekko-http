/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl

import javax.net.ssl.{ SSLContext, SSLEngine }

import org.apache.pekko
import pekko.annotation.{ ApiMayChange, DoNotInherit }
import pekko.http.scaladsl

object ConnectionContext {
  // #https-server-context-creation
  /**
   * Creates an HttpsConnectionContext for server-side use from the given SSLContext.
   */
  def httpsServer(sslContext: SSLContext): HttpsConnectionContext = // ...
    // #https-server-context-creation
    scaladsl.ConnectionContext.httpsServer(sslContext)

  /**
   *  If you want complete control over how to create the SSLEngine you can use this method.
   */
  @ApiMayChange
  def httpsServer(createEngine: pekko.japi.function.Creator[SSLEngine]): HttpsConnectionContext =
    scaladsl.ConnectionContext.httpsServer(() => createEngine.create())

  // #https-client-context-creation
  /**
   * Creates an HttpsConnectionContext for client-side use from the given SSLContext.
   */
  def httpsClient(sslContext: SSLContext): HttpsConnectionContext = // ...
    // #https-client-context-creation
    scaladsl.ConnectionContext.httpsClient(sslContext)

  /**
   *  If you want complete control over how to create the SSLEngine you can use this method.
   *
   *  Note that this means it is up to you to make sure features like SNI and hostname verification
   *  are enabled as needed.
   */
  @ApiMayChange
  def httpsClient(createEngine: pekko.japi.function.Function2[String, Integer, SSLEngine]): HttpsConnectionContext =
    scaladsl.ConnectionContext.httpsClient((host, port) => createEngine(host, port))

  /** Used to serve HTTP traffic. */
  def noEncryption(): HttpConnectionContext =
    scaladsl.ConnectionContext.noEncryption()
}

@DoNotInherit
abstract class ConnectionContext {
  def isSecure: Boolean
}

@DoNotInherit
abstract class HttpConnectionContext extends pekko.http.javadsl.ConnectionContext {
  override final def isSecure = false
}

@DoNotInherit
abstract class HttpsConnectionContext extends pekko.http.javadsl.ConnectionContext {
  override final def isSecure = true
}
