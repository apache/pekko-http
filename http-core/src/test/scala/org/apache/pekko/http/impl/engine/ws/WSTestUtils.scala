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

package org.apache.pekko.http.impl.engine.ws

import java.nio.charset.StandardCharsets

import org.apache.pekko
import pekko.http.impl.engine.ws.Protocol.Opcode
import pekko.util.ByteString

import scala.util.Random

object WSTestUtils {
  def frameHeader(
      opcode: Opcode,
      length: Long,
      fin: Boolean,
      mask: Option[Int] = None,
      rsv1: Boolean = false,
      rsv2: Boolean = false,
      rsv3: Boolean = false): ByteString = {
    def set(should: Boolean, mask: Int): Int =
      if (should) mask else 0

    val flags =
      set(fin, Protocol.FIN_MASK) |
      set(rsv1, Protocol.RSV1_MASK) |
      set(rsv2, Protocol.RSV2_MASK) |
      set(rsv3, Protocol.RSV3_MASK)

    val opcodeByte = opcode.code | flags

    require(length >= 0)
    val (lengthByteComponent, lengthBytes) =
      if (length < 126) (length.toByte, ByteString.empty)
      else if (length < 65536) (126.toByte, shortBE(length.toInt))
      else throw new IllegalArgumentException("Only lengths < 65536 allowed in test")

    val maskMask = if (mask.isDefined) Protocol.MASK_MASK else 0
    val maskBytes = mask match {
      case Some(mask) => intBE(mask)
      case None       => ByteString.empty
    }
    val lengthByte = lengthByteComponent | maskMask
    ByteString(opcodeByte.toByte, lengthByte.toByte) ++ lengthBytes ++ maskBytes
  }
  def frame(opcode: Opcode, data: ByteString, fin: Boolean, mask: Boolean): ByteString =
    if (mask) {
      val mask = Random.nextInt()
      frameHeader(opcode, data.size, fin, mask = Some(mask)) ++
      maskedBytes(data, mask)._1
    } else
      frameHeader(opcode, data.size, fin, mask = None) ++ data

  def closeFrame(closeCode: Int, mask: Boolean, msg: String = ""): ByteString =
    closeFrame(closeCode, mask, ByteString(msg, StandardCharsets.UTF_8))

  def closeFrame(closeCode: Int, mask: Boolean, msgBytes: ByteString): ByteString =
    frame(Opcode.Close, closeFrameData(closeCode, msgBytes), fin = true, mask)

  def closeFrameData(closeCode: Int, msgBytes: ByteString = ByteString.empty): ByteString =
    shortBE(closeCode) ++ msgBytes

  def maskedASCII(str: String, mask: Int): (ByteString, Int) =
    FrameEventParser.mask(ByteString(str, StandardCharsets.US_ASCII), mask)
  def maskedUTF8(str: String, mask: Int): (ByteString, Int) =
    FrameEventParser.mask(ByteString(str, StandardCharsets.UTF_8), mask)
  def maskedBytes(bytes: ByteString, mask: Int): (ByteString, Int) =
    FrameEventParser.mask(bytes, mask)

  def shortBE(value: Int): ByteString = {
    require(value >= 0 && value < 65536, s"Value wasn't in short range: $value")
    ByteString(
      ((value >> 8) & 0xFF).toByte,
      ((value >> 0) & 0xFF).toByte)
  }
  def intBE(value: Int): ByteString =
    ByteString(
      ((value >> 24) & 0xFF).toByte,
      ((value >> 16) & 0xFF).toByte,
      ((value >> 8) & 0xFF).toByte,
      ((value >> 0) & 0xFF).toByte)
}
