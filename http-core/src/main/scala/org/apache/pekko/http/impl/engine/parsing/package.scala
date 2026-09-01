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

import org.apache.pekko
import pekko.http.scaladsl.settings.ParserSettings

import pekko.event.LoggingAdapter
import pekko.util.ByteString
import pekko.http.scaladsl.model.{ ErrorInfo, StatusCode, StatusCodes }
import pekko.http.impl.util.ISO88591
import pekko.http.impl.util.SingletonException

/**
 * INTERNAL API
 */
package object parsing {

  private[http] def escape(c: Char): String = c match {
    case '\t'                           => "\\t"
    case '\r'                           => "\\r"
    case '\n'                           => "\\n"
    case x if Character.isISOControl(x) => "\\u%04x".format(c.toInt)
    case x                              => x.toString
  }

  private[http] def byteChar(input: ByteString, ix: Int): Char = (byteAt(input, ix) & 0xFF).toChar

  private[http] def byteAt(input: ByteString, ix: Int): Byte =
    if (ix < input.length) input(ix) else throw NotEnoughDataException

  /**
   * Decodes the given range as a String, one character per byte. Bytes above 0x7F are decoded as
   * ISO-8859-1, as [[pekko.http.impl.util.ByteStringParserInput.sliceString]] already does; most
   * callers have validated the range as 7-bit ASCII, for which the two agree.
   */
  private[http] def asciiString(input: ByteString, start: Int, end: Int): String =
    if (start == end) "" else input.slice(start, end).decodeString(ISO88591)

  private[http] def logParsingError(info: ErrorInfo, log: LoggingAdapter,
      settings: ParserSettings.ErrorLoggingVerbosity,
      ignoreHeaderNames: Set[String] = Set.empty): Unit =
    settings match {
      case ParserSettings.ErrorLoggingVerbosity.Off    => // nothing to do
      case ParserSettings.ErrorLoggingVerbosity.Simple =>
        if (!ignoreHeaderNames.contains(info.errorHeaderName))
          log.warning(info.summary)
      case ParserSettings.ErrorLoggingVerbosity.Full =>
        if (!ignoreHeaderNames.contains(info.errorHeaderName))
          log.warning(info.formatPretty)
    }
}

package parsing {

  import pekko.annotation.InternalApi

  /**
   * INTERNAL API
   */
  @InternalApi
  private[parsing] class ParsingException(
      val status: StatusCode,
      val info: ErrorInfo) extends RuntimeException(info.formatPretty) {
    def this(status: StatusCode, summary: String) =
      this(status, ErrorInfo(if (summary.isEmpty) status.defaultMessage else summary))
    def this(summary: String) =
      this(StatusCodes.BadRequest, ErrorInfo(summary))
    def this(summary: String, detail: String) =
      this(StatusCodes.BadRequest, ErrorInfo(summary, detail))
  }

  /**
   * INTERNAL API
   */
  @InternalApi
  private[parsing] object NotEnoughDataException extends SingletonException
}
