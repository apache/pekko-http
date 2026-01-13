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

package org.apache.pekko.http.scaladsl

import javax.net.ssl._

import org.apache.pekko
import pekko.annotation.{ ApiMayChange, InternalApi }

trait ConnectionContext extends pekko.http.javadsl.ConnectionContext {
  @deprecated("Internal method, left for binary compatibility", since = "Akka HTTP 10.2.0")
  protected[http] def defaultPort: Int
}

object ConnectionContext {
  // #https-server-context-creation
  /**
   *  Creates an HttpsConnectionContext for server-side use from the given SSLContext.
   */
  def httpsServer(sslContext: SSLContext): HttpsConnectionContext = // ...
    // #https-server-context-creation
    httpsServer(() => {
      val engine = sslContext.createSSLEngine()
      engine.setUseClientMode(false)
      engine
    })

  /**
   *  If you want complete control over how to create the SSLEngine you can use this method.
   */
  @ApiMayChange
  def httpsServer(createSSLEngine: () => SSLEngine): HttpsConnectionContext =
    new HttpsConnectionContext({
      case None    => createSSLEngine()
      case Some(_) =>
        throw new IllegalArgumentException("host and port supplied for connection based on server connection context")
    }: Option[(String, Int)] => SSLEngine)

  // #https-client-context-creation
  /**
   *  Creates an HttpsConnectionContext for client-side use from the given SSLContext.
   */
  def httpsClient(context: SSLContext): HttpsConnectionContext = // ...
    // #https-client-context-creation
    httpsClient((host, port) => {
      val engine = context.createSSLEngine(host, port)
      engine.setUseClientMode(true)

      engine.setSSLParameters {
        val params = engine.getSSLParameters
        params.setEndpointIdentificationAlgorithm("https")
        params
      }

      engine
    })

  /**
   *  If you want complete control over how to create the SSLEngine you can use this method.
   *
   *  Note that this means it is up to you to make sure features like SNI and hostname verification
   *  are enabled as needed.
   */
  @ApiMayChange
  def httpsClient(createSSLEngine: (String, Int) => SSLEngine): HttpsConnectionContext = // ...
    new HttpsConnectionContext({
      case None =>
        throw new IllegalArgumentException("host and port missing for connection based on client connection context")
      case Some((host, port)) => createSSLEngine(host, port)
    }: Option[(String, Int)] => SSLEngine)

  def noEncryption() = HttpConnectionContext
}

/**
 *  Context with all information needed to set up a HTTPS connection
 *
 * This constructor is INTERNAL API, use ConnectionContext.https instead
 */
@InternalApi
final class HttpsConnectionContext private[http] (
    private[http] val engineCreator: Option[(String, Int)] => SSLEngine)
    extends pekko.http.javadsl.HttpsConnectionContext with ConnectionContext {
  protected[http] override final def defaultPort: Int = 443
}

sealed class HttpConnectionContext extends pekko.http.javadsl.HttpConnectionContext with ConnectionContext {
  protected[http] override final def defaultPort: Int = 80
}

object HttpConnectionContext extends HttpConnectionContext {

  /** Java API */
  def getInstance() = this

  /** Java API */
  def create() = this

  def apply(): HttpConnectionContext = this
}
