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

package org.apache.pekko.http.scaladsl.unmarshalling

import scala.concurrent.{ ExecutionContext, Future }

import org.apache.pekko.stream.Materializer

object Unmarshal {
  def apply[T](value: T): Unmarshal[T] = new Unmarshal(value)
}

class Unmarshal[A](val value: A) {

  /**
   * Unmarshals the value to the given Type using the in-scope Unmarshaller.
   *
   * Uses the default materializer [[ExecutionContext]] if no implicit execution context is provided.
   * If you expect the marshalling to be heavy, it is suggested to provide a specialized context for those operations.
   */
  def to[B](implicit um: Unmarshaller[A, B], ec: ExecutionContext = null, mat: Materializer): Future[B] = {
    val context: ExecutionContext = if (ec == null) mat.executionContext else ec

    um(value)(context, mat)
  }
}
