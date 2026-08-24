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

/**
 * INTERNAL API
 */
@InternalApi
private[http] object StringTools {
  def asciiStringFromBytes(bytes: Array[Byte]): String =
    // ISO-8859-1 rather than US-ASCII: this maps every byte to the character of the same value, which
    // is what the deprecated `new String(bytes, 0)` this replaces did. Since JDK 9 (compact strings) it
    // keeps the array as is with a LATIN1 coder, so it is the same single copy.
    new String(bytes, ISO88591)

  def asciiStringBytes(string: String): Array[Byte] = {
    // this is as fast as Unsafe.copyUSAsciiStrToBytes for recent JDK versions
    // and avoids the use of deprecated Unsafe methods
    string.getBytes(java.nio.charset.StandardCharsets.US_ASCII)
  }
}
