/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.testkit

import org.apache.pekko.http.javadsl.model.headers.Host

final case class DefaultHostInfo(private val host: Host, private val securedConnection: Boolean) {

  def getHost(): Host = host

  def isSecuredConnection(): Boolean = securedConnection

}
