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
    def apply(hhp: HttpHeaderParser, input: ByteString, valueStart: Int, onIllegalHeader: ErrorInfo => Unit)
        : (HttpHeader, Int) = {
      @tailrec def recurse(ix: Int = valueStart, result: Long = 0): (HttpHeader, Int) = {
        val c = byteChar(input, ix)
        if (result < 0) fail("`Content-Length` header value must not exceed 63-bit integer range")
        else if (DIGIT(c)) recurse(ix + 1, result * 10 + c - '0')
        else if (WSP(c)) recurse(ix + 1, result)
        else if (c == '\r' && byteAt(input, ix + 1) == LF_BYTE) (`Content-Length`(result), ix + 2)
        else if (c == '\n') (`Content-Length`(result), ix + 1)
        else fail("Illegal `Content-Length` header value")
      }
      recurse()
    }
  }
}
