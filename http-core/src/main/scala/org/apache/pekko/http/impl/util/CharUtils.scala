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

package org.apache.pekko.http.impl.util

import scala.annotation.tailrec

import org.apache.pekko
import pekko.annotation.InternalApi

@InternalApi
private[http] object CharUtils {

  /**
   * Internal Pekko HTTP Use only.
   *
   * Efficiently lower-cases the given character.
   * Note: only works for 7-bit ASCII letters (which is enough for header names)
   */
  final def toLowerCase(c: Char): Char =
    if (c >= 'A' && c <= 'Z') (c + 0x20 /* - 'A' + 'a' */ ).toChar else c

  /** Returns true if both strings contain only ASCII and match case-insensitively. */
  def equalsAsciiCaseInsensitive(str1: String, str2: String): Boolean = {
    @tailrec def stringEquals(at: Int, length: Int): Boolean =
      if (at < length) {
        val char1 = str1.charAt(at)
        val char2 = str2.charAt(at)

        (char1 | char2) < 0x80 && toLowerCase(char1) == toLowerCase(char2) && stringEquals(at + 1, length)
      } else true

    str1.length == str2.length && stringEquals(0, str1.length)
  }

}
