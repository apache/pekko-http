/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.ccompat

import scala.collection.immutable

/**
 * INTERNAL API
 */
package object imm {
  implicit class SortedSetOps[A](val real: immutable.SortedSet[A]) extends AnyVal {
    def unsorted: immutable.Set[A] = real
  }

  implicit class StreamOps[A](val underlying: immutable.Stream[A]) extends AnyVal {
    // renamed in 2.13
    def lazyAppendedAll[B >: A](rest: => TraversableOnce[B]): Stream[B] = underlying.append(rest)
  }
}
