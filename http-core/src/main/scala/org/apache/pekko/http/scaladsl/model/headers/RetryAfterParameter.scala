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

package org.apache.pekko.http.scaladsl.model.headers

import org.apache.pekko.http.scaladsl.model._

/**
 * Defines different values admitted to define a [[`Retry-After`]] header.
 *
 * Spec: https://tools.ietf.org/html/rfc7231#section-7.1.3
 */
sealed abstract class RetryAfterParameter
final case class RetryAfterDuration(delayInSeconds: Long) extends RetryAfterParameter {
  require(delayInSeconds >= 0, "Retry-after header must not contain a negative delay in seconds")
}
final case class RetryAfterDateTime(dateTime: DateTime) extends RetryAfterParameter
