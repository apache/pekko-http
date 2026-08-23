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

package org.apache.pekko.http.impl.engine.parsing

import org.apache.pekko
import pekko.annotation.InternalApi

import scala.annotation.tailrec
import pekko.util.ByteString
import pekko.http.impl.model.parser.CharacterClasses._
import pekko.http.impl.util.HttpConstants._
import pekko.http.scaladsl.model.{ ErrorInfo, HttpHeader }
import pekko.http.scaladsl.model.headers.`Content-Length`

/**
 * INTERNAL API
 */
@InternalApi
private[parsing] object SpecializedHeaderValueParsers {
  import HttpHeaderParser._

  def specializedHeaderValueParsers = Seq(ContentLengthParser)

  object ContentLengthParser extends HeaderValueParser("Content-Length", maxValueCount = 1) {
    // The field value is `1*DIGIT`, surrounded by optional whitespace (RFC 9110, section 8.6 and RFC 9112,
    // section 5). Whitespace within the digits must not be skipped: a value like `1 2` would then be read as `12`
    // here while another implementation in the request path rejects it or reads it as `1`.
    def apply(hhp: HttpHeaderParser, input: ByteString, valueStart: Int, onIllegalHeader: ErrorInfo => Unit)
        : (HttpHeader, Int) = {
      @tailrec def skipWhitespace(ix: Int): Int = if (WSP(byteChar(input, ix))) skipWhitespace(ix + 1) else ix

      @tailrec def digits(ix: Int, result: Long, seenDigit: Boolean): (HttpHeader, Int) = {
        val c = byteChar(input, ix)
        if (DIGIT(c)) {
          val digit = c - '0'
          if (result > (Long.MaxValue - digit) / 10)
            fail("`Content-Length` header value must not exceed 63-bit integer range")
          else digits(ix + 1, result * 10 + digit, seenDigit = true)
        } else if (!seenDigit) fail("Illegal `Content-Length` header value")
        else lineEnd(skipWhitespace(ix), result)
      }

      def lineEnd(ix: Int, result: Long): (HttpHeader, Int) = {
        val c = byteChar(input, ix)
        if (c == '\r' && byteAt(input, ix + 1) == LF_BYTE) (`Content-Length`(result), ix + 2)
        else if (c == '\n') (`Content-Length`(result), ix + 1)
        else fail("Illegal `Content-Length` header value")
      }

      digits(skipWhitespace(valueStart), 0, seenDigit = false)
    }
  }
}
