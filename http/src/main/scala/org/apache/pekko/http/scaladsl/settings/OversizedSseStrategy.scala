/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package org.apache.pekko.http.scaladsl.settings

import org.apache.pekko.annotation.InternalApi

sealed trait OversizedSseStrategy

object OversizedSseStrategy {
  case object FailStream extends OversizedSseStrategy
  case object LogAndSkip extends OversizedSseStrategy
  case object Truncate extends OversizedSseStrategy
  case object DeadLetter extends OversizedSseStrategy

  /**
   * Convert from a Java enum to the corresponding Scala case object.
   * Java API.
   */
  def fromJava(javaStrategy: org.apache.pekko.http.javadsl.settings.OversizedSseStrategy): OversizedSseStrategy =
    javaStrategy.asScala()

  @InternalApi
  private[http] def fromString(value: String): OversizedSseStrategy = value match {
    case "fail-stream"  => FailStream
    case "log-and-skip" => LogAndSkip
    case "truncate"     => Truncate
    case "dead-letter"  => DeadLetter
    case _ => throw new IllegalArgumentException(
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
