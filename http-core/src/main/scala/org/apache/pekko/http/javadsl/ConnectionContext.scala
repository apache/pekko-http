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

import java.util.{ Collection => JCollection, Optional }

import scala.annotation.nowarn

import org.apache.pekko
import pekko.annotation.{ ApiMayChange, DoNotInherit }
import pekko.http.impl.util.Util
import pekko.http.scaladsl
import pekko.stream.TLSClientAuth
import pekko.util.OptionConverters._
import com.typesafe.sslconfig.pekko.PekkoSSLConfig
import javax.net.ssl.{ SSLContext, SSLEngine, SSLParameters }

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

  // ConnectionContext
  /** Used to serve HTTPS traffic. */
  @Deprecated @deprecated("use httpsServer, httpsClient or the method that takes a custom factory",
    since = "Akka HTTP 10.2.0")
  def https(sslContext: SSLContext): HttpsConnectionContext = // ...
    // #https-context-creation
    scaladsl.ConnectionContext.https(sslContext)

  /** Used to serve HTTPS traffic. */
  @nowarn("msg=deprecated")
  @Deprecated @deprecated("use httpsServer, httpsClient or the method that takes a custom factory",
    since = "Akka HTTP 10.2.0")
  def https(
      sslContext: SSLContext,
      sslConfig: Optional[PekkoSSLConfig],
      enabledCipherSuites: Optional[JCollection[String]],
      enabledProtocols: Optional[JCollection[String]],
      clientAuth: Optional[TLSClientAuth],
      sslParameters: Optional[SSLParameters]) = // ...
    // #https-context-creation
    scaladsl.ConnectionContext.https(
      sslContext,
      sslConfig.toScala,
      enabledCipherSuites.toScala.map(Util.convertIterable[String, String](_)),
      enabledProtocols.toScala.map(Util.convertIterable[String, String](_)),
      clientAuth.toScala,
      sslParameters.toScala)

  /** Used to serve HTTPS traffic. */
  @Deprecated @deprecated("use httpsServer, httpsClient or the method that takes a custom factory",
    since = "Akka HTTP 10.2.0")
  def https(
      sslContext: SSLContext,
      enabledCipherSuites: Optional[JCollection[String]],
      enabledProtocols: Optional[JCollection[String]],
      clientAuth: Optional[TLSClientAuth],
      sslParameters: Optional[SSLParameters]) =
    scaladsl.ConnectionContext.https(
      sslContext,
      None,
      enabledCipherSuites.toScala.map(Util.convertIterable[String, String](_)),
      enabledProtocols.toScala.map(Util.convertIterable[String, String](_)),
      clientAuth.toScala,
      sslParameters.toScala)

  /** Used to serve HTTP traffic. */
  def noEncryption(): HttpConnectionContext =
    scaladsl.ConnectionContext.noEncryption()
}

@DoNotInherit
abstract class ConnectionContext {
  def isSecure: Boolean
  @Deprecated
  @deprecated("Not always available", since = "Akka HTTP 10.2.0")
  def sslConfig: Option[PekkoSSLConfig]
}

@DoNotInherit
abstract class HttpConnectionContext extends pekko.http.javadsl.ConnectionContext {
  override final def isSecure = false
  @nowarn("msg=deprecated")
  override def sslConfig: Option[PekkoSSLConfig] = None
}

@DoNotInherit
abstract class HttpsConnectionContext extends pekko.http.javadsl.ConnectionContext {
  override final def isSecure = true

  /** Java API */
  @Deprecated @deprecated("here for binary compatibility", since = "Akka HTTP 10.2.0")
  def getEnabledCipherSuites: Optional[JCollection[String]]

  /** Java API */
  @Deprecated @deprecated("here for binary compatibility", since = "Akka HTTP 10.2.0")
  def getEnabledProtocols: Optional[JCollection[String]]

  /** Java API */
  @Deprecated @deprecated("here for binary compatibility", since = "Akka HTTP 10.2.0")
  def getClientAuth: Optional[TLSClientAuth]

  /** Java API */
  @Deprecated @deprecated("here for binary compatibility, not always available", since = "Akka HTTP 10.2.0")
  def getSslContext: SSLContext

  /** Java API */
  @Deprecated @deprecated("here for binary compatibility", since = "Akka HTTP 10.2.0")
  def getSslParameters: Optional[SSLParameters]
}
