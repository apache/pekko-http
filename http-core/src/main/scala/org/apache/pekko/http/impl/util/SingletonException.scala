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

package org.apache.pekko.http.impl.util

import org.apache.pekko.annotation.InternalApi

import scala.util.control.NoStackTrace

/**
 * INTERNAL API
 *
 * Convenience base class for exception objects.
 */
@InternalApi
private[http] abstract class SingletonException(msg: String) extends RuntimeException(msg) with NoStackTrace {
  def this() = this(null)
}
