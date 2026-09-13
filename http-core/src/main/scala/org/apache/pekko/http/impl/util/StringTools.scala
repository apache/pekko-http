/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2021-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.impl.util

import org.apache.pekko
import pekko.annotation.InternalApi

import scala.annotation.nowarn

/**
 * INTERNAL API
 */
@InternalApi
private[http] object StringTools {
  @nowarn("msg=deprecated")
  def asciiStringFromBytes(bytes: Array[Byte]): String =
    // Deprecated constructor but also (unfortunately) the fastest way to convert a ASCII encoded byte array
    // into a String without extra copying.
    new String(bytes, 0)

  // Deprecated (not for removal) but the only JDK primitive that copies the low 8 bits of every char, so this is
  // the exact inverse of asciiStringFromBytes: HPACK string literals are opaque octets and a char in 0x80-0xFF has
  // to come out as that octet, not as the '?' that encoding with US-ASCII would substitute. For a Latin-1 coded
  // string this is a single System.arraycopy.
  @nowarn("cat=deprecation")
  def asciiStringBytes(string: String): Array[Byte] = {
    val bytes = new Array[Byte](string.length)
    string.getBytes(0, string.length, bytes, 0)
    bytes
  }
}
