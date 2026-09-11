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

package org.apache.pekko.http.impl.model.parser

import org.apache.pekko
import pekko.annotation.InternalApi
import org.parboiled2.{ ParseError, ParserInput }

/**
 * INTERNAL API
 *
 * Renders the input line a parse error occurred on with a caret under the failing position, the way parboiled2's
 * `ErrorFormatter.formatErrorLine` does, but with every control character escaped the way `ErrorFormatter.format`
 * already escapes the offending character in its summary.
 *
 * The line is attacker-controlled -- a request target, a header value -- and the default
 * `error-logging-verbosity = full` writes it to the log, so a raw CR, ESC or NUL in it would let a client inject
 * line breaks or terminal control sequences into log output.
 */
@InternalApi
private[http] object ParseErrorLine {

  def render(error: ParseError, input: ParserInput): String = {
    val line = input.getLine(error.position.line)
    val column = error.position.column // 1-based; one past the end of the line for an error at end of input
    val sb = new java.lang.StringBuilder(line.length + 16)
    var caret = 0
    var i = 0
    while (i < line.length) {
      val escaped = escape(line.charAt(i))
      if (i < column - 1) caret += escaped.length
      sb.append(escaped)
      i += 1
    }
    sb.append('\n')
    var j = 0
    while (j < caret) {
      sb.append(' ')
      j += 1
    }
    sb.append('^').toString
  }

  // the same escapes `org.parboiled2.CharUtils.escape` applies to the offending character in the summary, except that
  // the EOI sentinel (U+FFFF, which is not a control character) is left alone: `HeaderParser` appends it to every
  // header value it parses and strips it from the error afterwards, which only works if it is still that character
  private def escape(c: Char): String = c match {
    case '\t'                          => "\\t"
    case '\r'                          => "\\r"
    case '\n'                          => "\\n"
    case c if Character.isISOControl(c) => "\\u%04x".format(c.toInt)
    case c                              => c.toString
  }
}
