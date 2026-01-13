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

package org.apache.pekko.http.scaladsl.settings

import org.apache.pekko.annotation.InternalApi

/**
 * @since 1.3.0
 */
sealed trait OversizedSseStrategy

object OversizedSseStrategy {
  case object FailStream extends OversizedSseStrategy
  case object LogAndSkip extends OversizedSseStrategy
  case object Truncate extends OversizedSseStrategy
  case object DeadLetter extends OversizedSseStrategy

  /**
   * Convert from a Java enum to the corresponding Scala case object.
   * Java API.
   * @since 1.3.0
   */
  def fromJava(javaStrategy: org.apache.pekko.http.javadsl.settings.OversizedSseStrategy): OversizedSseStrategy =
    javaStrategy.asScala()

  @InternalApi
  private[http] def fromString(value: String): OversizedSseStrategy = value match {
    case "fail-stream"  => FailStream
    case "log-and-skip" => LogAndSkip
    case "truncate"     => Truncate
    case "dead-letter"  => DeadLetter
    case _              => throw new IllegalArgumentException(
        s"Invalid oversized-message-handling: '$value'. Valid options are: fail-stream, log-and-skip, truncate, dead-letter")
  }

  @InternalApi
  private[http] def toString(handling: OversizedSseStrategy): String = handling match {
    case FailStream => "fail-stream"
    case LogAndSkip => "log-and-skip"
    case Truncate   => "truncate"
    case DeadLetter => "dead-letter"
  }
}
