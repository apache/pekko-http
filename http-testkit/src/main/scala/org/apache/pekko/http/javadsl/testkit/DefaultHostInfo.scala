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

package org.apache.pekko.http.javadsl.testkit

import org.apache.pekko.http.javadsl.model.headers.Host

final case class DefaultHostInfo(private val host: Host, private val securedConnection: Boolean) {

  def getHost(): Host = host

  def isSecuredConnection(): Boolean = securedConnection

}
