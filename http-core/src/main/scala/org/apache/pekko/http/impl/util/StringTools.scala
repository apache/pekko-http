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

  def asciiStringBytes(string: String): Array[Byte] =
    // ISO-8859-1 makes this the inverse of asciiStringFromBytes: HPACK string literals are opaque octets, and a char in
    // 0x80-0xFF has to come out as that octet. US-ASCII, as used before on JDK 17+, wrote '?' for it instead.
    string.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)
}
