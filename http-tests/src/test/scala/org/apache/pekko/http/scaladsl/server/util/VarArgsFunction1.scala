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

package org.apache.pekko.http.scaladsl.server.util

// in 2.13 (T*) => U is not a valid type any more, this works on 2.12+ as a drop in replacement
trait VarArgsFunction1[-T, +U] {
  def apply(alternatives: T*): U
}
