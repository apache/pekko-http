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
import pekko.util.{ JavaVersion, Unsafe }

import scala.annotation.nowarn

/**
 * INTERNAL API
 */
@InternalApi
private[http] object StringTools {
  private val avoidUnsafe = JavaVersion.majorVersion >= 17

  @nowarn("msg=deprecated")
  def asciiStringFromBytes(bytes: Array[Byte]): String =
    // Deprecated constructor but also (unfortunately) the fastest way to convert a ASCII encoded byte array
    // into a String without extra copying.
    new String(bytes, 0)

  def asciiStringBytes(string: String): Array[Byte] = {
    if (avoidUnsafe) {
      // this is as fast as Unsafe.copyUSAsciiStrToBytes for recent JDK versions
      // and avoids the use of deprecated Unsafe methods
      string.getBytes(java.nio.charset.StandardCharsets.US_ASCII)
    } else {
      val bytes = new Array[Byte](string.length)
      Unsafe.copyUSAsciiStrToBytes(string, bytes)
      bytes
    }
  }
}
