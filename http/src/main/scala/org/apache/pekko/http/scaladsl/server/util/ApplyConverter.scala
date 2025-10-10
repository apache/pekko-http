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

import org.apache.pekko.http.scaladsl.server._

/**
 * ApplyConverter allows generic conversion of functions of type `(T1, T2, ...) => Route` to
 * `(TupleX(T1, T2, ...)) => Route`.
 */
abstract class ApplyConverter[L] {
  type In
  def apply(f: In): L => Route
}

object ApplyConverter extends ApplyConverterInstances
