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
