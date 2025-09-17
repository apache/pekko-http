/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.http.impl.util

import java.time.temporal.ChronoUnit

import org.apache.pekko
import pekko.annotation.InternalApi

import scala.concurrent.duration.FiniteDuration
import scala.jdk.DurationConverters._

/**
 * Internal Pekko HTTP API
 */
@InternalApi
private[http] object JavaDurationConverter {
  def toJava(d: scala.concurrent.duration.Duration): java.time.Duration = d match {
    case fd: scala.concurrent.duration.FiniteDuration => fd.toJava
    case scala.concurrent.duration.Duration.Inf       => ChronoUnit.FOREVER.getDuration
    case scala.concurrent.duration.Duration.MinusInf  => ChronoUnit.FOREVER.getDuration.negated()
    case scala.concurrent.duration.Duration.Undefined => ChronoUnit.FOREVER.getDuration
  }
}
