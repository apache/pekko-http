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

package org.apache.pekko.http.scaladsl.model

/** Helper trait for objects that allow creating new instances with a modified qValue. */
trait WithQValue[T] {

  /** truncates Double qValue to float and returns a new instance with this qValue set */
  def withQValue(qValue: Double): T = withQValue(qValue.toFloat)
  def withQValue(qValue: Float): T
}
