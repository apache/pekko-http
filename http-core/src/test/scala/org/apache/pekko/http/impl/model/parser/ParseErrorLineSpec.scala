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

import org.parboiled2.{ ParseError, ParserInput, Position }
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ParseErrorLineSpec extends AnyWordSpec with Matchers {

  def errorAt(input: String, index: Int): (ParseError, ParserInput) = {
    val in = ParserInput(input)
    val pos = Position(index, in)
    (ParseError(pos, pos, Vector.empty), in)
  }

  "ParseErrorLine" should {
    "render the failing line with a caret under the failing position" in {
      val (error, input) = errorAt("a^=b", 1)
      ParseErrorLine.render(error, input) shouldEqual "a^=b\n ^"
    }

    "put the caret after the last character for an error at end of input" in {
      val (error, input) = errorAt("abc", 3)
      ParseErrorLine.render(error, input) shouldEqual "abc\n   ^"
    }

    "escape control characters instead of rendering them raw" in {
      val (error, input) = errorAt("/a\u001b[31m", 2)
      ParseErrorLine.render(error, input) shouldEqual "/a\\u001b[31m\n  ^"
    }

    "escape CR, tab and NUL with their short forms" in {
      val (error, input) = errorAt("a\r\t\u0000", 1)
      ParseErrorLine.render(error, input) shouldEqual "a\\r\\t\\u0000\n ^"
    }

    "keep the caret aligned when an escaped character precedes the failing position" in {
      // the tab renders as two characters, so the caret moves one column right of where it would sit on the raw line
      val (error, input) = errorAt("a\tb^c", 3)
      ParseErrorLine.render(error, input) shouldEqual "a\\tb^c\n    ^"
    }

    "render only the line the error is on" in {
      val (error, input) = errorAt("first\nsec^ond", 9)
      ParseErrorLine.render(error, input) shouldEqual "sec^ond\n   ^"
    }
  }
}
