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

package org.apache.pekko.http.impl.engine.http2

import org.apache.pekko.annotation.InternalApi

/** INTERNAL API */
@InternalApi
private[http] final class ByteFlag(val value: Int) extends AnyVal {
  def |(that: ByteFlag): ByteFlag = new ByteFlag((this.value | that.value).toByte)
  def &(that: ByteFlag): ByteFlag = new ByteFlag((this.value | that.value).toByte)

  def isSet(byteFlag: ByteFlag): Boolean = (byteFlag.value & value) != 0
  def ifSet(flag: Boolean): ByteFlag = if (flag) this else ByteFlag.Zero
  override def toString: String = s"ByteFlag(${Integer.toHexString(value)})"
}

/** INTERNAL API */
@InternalApi
private[impl] object ByteFlag {
  val Zero = new ByteFlag(0)

  def binaryLeftPad(byte: Byte): String = {
    val string = Integer.toBinaryString(byte)
    val pad = "0" * (8 - string.length) // leftPad
    pad + string
  }
}
