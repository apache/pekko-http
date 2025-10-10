/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.scaladsl.model

import org.apache.pekko
import pekko.http.javadsl.{ model => jm }
import pekko.stream.scaladsl.ScalaSessionAPI
import javax.net.ssl.SSLSession

class SslSessionInfo(val session: SSLSession) extends jm.SslSessionInfo with ScalaSessionAPI {

  /**
   * Java API
   */
  override def getSession: SSLSession = session

  override def equals(other: Any): Boolean = other match {
    case SslSessionInfo(`session`) => true
    case _                         => false
  }
  override def hashCode(): Int = session.hashCode()
}

object SslSessionInfo {
  def apply(session: SSLSession): SslSessionInfo = new SslSessionInfo(session)
  def unapply(sslSession: SslSessionInfo): Option[SSLSession] = Some(sslSession.session)
}
