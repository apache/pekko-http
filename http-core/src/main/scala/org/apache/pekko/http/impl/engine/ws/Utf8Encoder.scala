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

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.stream.{ Attributes, FlowShape, Inlet, Outlet }
import pekko.stream.stage._
import pekko.util.{ ByteString, ByteStringBuilder }

/**
 * A utf16 (= Java char) to utf8 encoder.
 *
 * INTERNAL API
 */
@InternalApi
private[http] object Utf8Encoder extends GraphStage[FlowShape[String, ByteString]] {
  val SurrogateHighMask = 0xD800
  val SurrogateLowMask = 0xDC00

  val Utf8OneByteLimit = lowerNBitsSet(7)
  val Utf8TwoByteLimit = lowerNBitsSet(11)
  val Utf8ThreeByteLimit = lowerNBitsSet(16)

  def lowerNBitsSet(n: Int): Long = (1L << n) - 1

  val stringIn = Inlet[String]("Utf8Encoder.stringIn")
  val byteStringOut = Outlet[ByteString]("Utf8Encoder.byteStringOut")
  override val shape = FlowShape(stringIn, byteStringOut)
  override val initialAttributes: Attributes = Attributes.name("utf8Encoder")

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler {
      var surrogateValue: Int = 0
      def inSurrogatePair: Boolean = surrogateValue != 0

      override def onPush(): Unit = {
        val builder = new ByteStringBuilder

        def b(v: Int): Unit = {
          builder += v.toByte
        }

        def step(char: Int): Unit =
          if (!inSurrogatePair) {
            if (char <= Utf8OneByteLimit) {
              builder += char.toByte
            } else if (char <= Utf8TwoByteLimit) {
              b(0xC0 | ((char & 0x7C0) >> 6)) // upper 5 bits
              b(0x80 | (char & 0x3F)) // lower 6 bits
            } else if (char >= SurrogateHighMask && char < SurrogateLowMask) {
              surrogateValue = 0x10000 + ((char & 0x3FF) << 10)
            } else if (char >= SurrogateLowMask && char < 0xDFFF) {
              throw new IllegalArgumentException(f"Unexpected UTF-16 surrogate continuation")
            } else if (char <= Utf8ThreeByteLimit) {
              b(0xE0 | ((char & 0xF000) >> 12)) // upper 4 bits
              b(0x80 | ((char & 0x0FC0) >> 6)) // middle 6 bits
              b(0x80 | (char & 0x3F)) // lower 6 bits
            } else {
              throw new IllegalStateException("Char cannot be >= 2^16") // char value was converted from 16bit value
            }
          } else if (char >= SurrogateLowMask && char <= 0xDFFF) {
            surrogateValue |= (char & 0x3FF)
            b(0xF0 | ((surrogateValue & 0x1C0000) >> 18)) // upper 3 bits
            b(0x80 | ((surrogateValue & 0x3F000) >> 12)) // first middle 6 bits
            b(0x80 | ((surrogateValue & 0x0FC0) >> 6)) // second middle 6 bits
            b(0x80 | (surrogateValue & 0x3F)) // lower 6 bits
            surrogateValue = 0
          } else throw new IllegalArgumentException(f"Expected UTF-16 surrogate continuation")

        var offset = 0
        val input = grab(stringIn)
        while (offset < input.length) {
          step(input(offset))
          offset += 1
        }

        if (builder.length > 0) push(byteStringOut, builder.result())
        else pull(stringIn)
      }

      override def onUpstreamFinish(): Unit =
        if (inSurrogatePair)
          failStage(new IllegalArgumentException("Truncated String input (ends in the middle of surrogate pair)"))
        else completeStage()

      override def onPull(): Unit = pull(stringIn)

      setHandlers(stringIn, byteStringOut, this)
    }

  override def toString: String = "Utf8Encoder"
}
