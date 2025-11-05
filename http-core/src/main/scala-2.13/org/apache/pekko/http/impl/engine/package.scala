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

package org.apache.pekko.http.impl.engine

import java.lang.{ StringBuilder => JStringBuilder }

import org.apache.pekko
import pekko.event.LoggingAdapter
import pekko.http.scaladsl.model.ErrorInfo
import pekko.http.scaladsl.settings.ParserSettings
import pekko.util.ByteString

import scala.annotation.tailrec

/**
 * INTERNAL API
 */
package object parsing {

  @inline
  private[http] def escape(c: Char): String = c match {
    case '\t'                           => "\\t"
    case '\r'                           => "\\r"
    case '\n'                           => "\\n"
    case x if Character.isISOControl(x) => "\\u%04x".format(c.toInt)
    case x                              => x.toString
  }

  /**
   * Like `byteChar` but doesn't throw `NotEnoughDataException` if the index is out of bounds.
   * Used in places where we know that the index is valid because we checked the length beforehand.
   */
  @inline
  private[http] def safeByteChar(input: ByteString, ix: Int): Char =
    (input(ix) & 0xFF).toChar

  @inline
  private[http] def byteChar(input: ByteString, ix: Int): Char = (byteAt(input, ix) & 0xFF).toChar

  @inline
  private[http] def byteAt(input: ByteString, ix: Int): Byte =
    if (ix < input.length) input(ix) else throw NotEnoughDataException

  @inline
  private[http] def asciiString(input: ByteString, start: Int, end: Int): String = {
    @tailrec def build(ix: Int = start, sb: JStringBuilder = new JStringBuilder(end - start)): String =
      if (ix == end) sb.toString else build(ix + 1, sb.append(input(ix).toChar))
    if (start == end) "" else build()
  }

  @inline
  private[http] def logParsingError(info: ErrorInfo, log: LoggingAdapter,
      settings: ParserSettings.ErrorLoggingVerbosity,
      ignoreHeaderNames: Set[String] = Set.empty): Unit =
    settings match {
      case ParserSettings.ErrorLoggingVerbosity.Off => // nothing to do
      case ParserSettings.ErrorLoggingVerbosity.Simple =>
        if (!ignoreHeaderNames.contains(info.errorHeaderName))
          log.warning(info.summary)
      case ParserSettings.ErrorLoggingVerbosity.Full =>
        if (!ignoreHeaderNames.contains(info.errorHeaderName))
          log.warning(info.formatPretty)
    }
}
