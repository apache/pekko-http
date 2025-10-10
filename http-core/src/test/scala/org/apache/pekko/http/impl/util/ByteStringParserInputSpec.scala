/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.impl.util

import org.apache.pekko.util.ByteString
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ByteStringParserInputSpec extends AnyWordSpec with Matchers {

  "The ByteStringParserInput" should {
    val parser = new ByteStringParserInput(ByteString("abcde", "ISO-8859-1"))
    "return the correct character for index" in {
      parser.charAt(0) should ===('a')
      parser.charAt(4) should ===('e')
    }

    "return the correct length" in {
      parser.length should ===(5)
    }

    "slice the bytes correctly into a string" in {
      parser.sliceString(0, 3) should ===("abc")
      parser.sliceString(3, 5) should ===("de")
    }

    "slice the bytes correctly into a char array" in {
      val array = parser.sliceCharArray(0, 3)
      array(0) should ===('a')
      array(1) should ===('b')
      array(2) should ===('c')
      array.length should ===(3)
    }

  }

}
