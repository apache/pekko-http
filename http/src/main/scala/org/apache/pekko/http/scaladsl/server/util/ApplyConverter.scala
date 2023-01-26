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
