/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.http.impl.engine.http2

import org.apache.pekko
import pekko.annotation.InternalApi

import java.nio.ByteBuffer
import java.util.function.BiFunction
import java.{ util => ju }
import javax.net.ssl.{ SSLEngine, SSLEngineResult, SSLParameters, SSLSession }

/**
 * INTERNAL API
 *
 * Delegating [[SSLEngine]] that reports the ALPN protocol as soon as the handshake has settled it.
 *
 * The JDK exposes the client-side ALPN result only through [[SSLEngine#getApplicationProtocol]], and neither
 * `SSLEngine` nor Pekko's TLS stage offers an event for "handshake complete". `setHandshakeApplicationProtocolSelector`
 * is only meaningful for the peer that selects the protocol (the server), and `HandshakeCompletedListener` exists on
 * `SSLSocket` only. So the only portable place to observe the transition is around `wrap`/`unwrap`: once either has run
 * far enough for the handshake to complete, `getApplicationProtocol` stops returning `null`.
 *
 * `onNegotiated` is invoked at most once, from whichever thread the TLS stage runs `wrap`/`unwrap` on. It is passed the
 * empty string when the peer did not negotiate any protocol, which is what the JDK reports for a server that does not
 * speak ALPN.
 */
@InternalApi
private[http] final class AlpnObservingSSLEngine(delegate: SSLEngine, onNegotiated: String => Unit)
    extends SSLEngine(delegate.getPeerHost, delegate.getPeerPort) {

  // only ever touched from the TLS stage, whose calls into the engine are serialized
  private[this] var reported = false

  private def observe(): Unit =
    if (!reported) {
      val protocol =
        try delegate.getApplicationProtocol
        catch {
          // engines predating JDK 9 (or custom ones) may not implement it; treat as "no protocol negotiated"
          case _: UnsupportedOperationException => ""
        }
      if (protocol ne null) {
        reported = true
        onNegotiated(protocol)
      }
    }

  override def wrap(srcs: Array[ByteBuffer], offset: Int, length: Int, dst: ByteBuffer): SSLEngineResult = {
    val result = delegate.wrap(srcs, offset, length, dst)
    observe()
    result
  }

  override def unwrap(src: ByteBuffer, dsts: Array[ByteBuffer], offset: Int, length: Int): SSLEngineResult = {
    val result = delegate.unwrap(src, dsts, offset, length)
    observe()
    result
  }

  override def getDelegatedTask: Runnable = delegate.getDelegatedTask

  override def closeInbound(): Unit = delegate.closeInbound()
  override def isInboundDone: Boolean = delegate.isInboundDone
  override def closeOutbound(): Unit = delegate.closeOutbound()
  override def isOutboundDone: Boolean = delegate.isOutboundDone

  override def getSupportedCipherSuites: Array[String] = delegate.getSupportedCipherSuites
  override def getEnabledCipherSuites: Array[String] = delegate.getEnabledCipherSuites
  override def setEnabledCipherSuites(suites: Array[String]): Unit = delegate.setEnabledCipherSuites(suites)

  override def getSupportedProtocols: Array[String] = delegate.getSupportedProtocols
  override def getEnabledProtocols: Array[String] = delegate.getEnabledProtocols
  override def setEnabledProtocols(protocols: Array[String]): Unit = delegate.setEnabledProtocols(protocols)

  override def getSession: SSLSession = delegate.getSession
  override def getHandshakeSession: SSLSession = delegate.getHandshakeSession

  override def beginHandshake(): Unit = delegate.beginHandshake()
  override def getHandshakeStatus: SSLEngineResult.HandshakeStatus = delegate.getHandshakeStatus

  override def setUseClientMode(mode: Boolean): Unit = delegate.setUseClientMode(mode)
  override def getUseClientMode: Boolean = delegate.getUseClientMode
  override def setNeedClientAuth(need: Boolean): Unit = delegate.setNeedClientAuth(need)
  override def getNeedClientAuth: Boolean = delegate.getNeedClientAuth
  override def setWantClientAuth(want: Boolean): Unit = delegate.setWantClientAuth(want)
  override def getWantClientAuth: Boolean = delegate.getWantClientAuth

  override def setEnableSessionCreation(flag: Boolean): Unit = delegate.setEnableSessionCreation(flag)
  override def getEnableSessionCreation: Boolean = delegate.getEnableSessionCreation

  override def getSSLParameters: SSLParameters = delegate.getSSLParameters
  override def setSSLParameters(params: SSLParameters): Unit = delegate.setSSLParameters(params)

  override def getApplicationProtocol: String = delegate.getApplicationProtocol
  override def getHandshakeApplicationProtocol: String = delegate.getHandshakeApplicationProtocol

  override def setHandshakeApplicationProtocolSelector(
      selector: BiFunction[SSLEngine, ju.List[String], String]): Unit =
    delegate.setHandshakeApplicationProtocolSelector(selector)

  override def getHandshakeApplicationProtocolSelector: BiFunction[SSLEngine, ju.List[String], String] =
    delegate.getHandshakeApplicationProtocolSelector
}
