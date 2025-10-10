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

package org.apache.pekko.http.impl.engine.ws

import java.security.SecureRandom
import java.util.Random

import org.apache.pekko.annotation.InternalApi

/** INTERNAL API */
@InternalApi
private[http] object Randoms {

  /** A factory that creates SecureRandom instances */
  private[http] case object SecureRandomInstances extends (() => Random) {
    override def apply(): Random = new SecureRandom()
  }
}
