/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model

import javax.net.ssl.SSLSession

import org.apache.pekko.http.scaladsl.{ model => sm }

trait SslSessionInfo {

  /**
   * Java API
   */
  def getSession: SSLSession
}
object SslSessionInfo {
  def create(session: SSLSession): SslSessionInfo = sm.SslSessionInfo(session)
}
