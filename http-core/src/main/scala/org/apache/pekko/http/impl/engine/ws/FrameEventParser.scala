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

import java.lang.invoke.MethodHandles
import java.nio.ByteOrder

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.stream.Attributes
import pekko.stream.impl.io.ByteStringParser
import pekko.util.ByteString

/**
 * Streaming parser for the base WebSocket frame format defined in RFC 6455 section 5.2.
 *
 * https://www.rfc-editor.org/rfc/rfc6455.html#section-5.2
 *
 * 0                   1                   2                   3
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-------+-+-------------+-------------------------------+
 * |F|R|R|R| opcode|M| Payload len |    Extended payload length    |
 * |I|S|S|S|  (4)  |A|     (7)     |             (16/64)           |
 * |N|V|V|V|       |S|             |   (if payload len==126/127)   |
 * | |1|2|3|       |K|             |                               |
 * +-+-+-+-+-------+-+-------------+ - - - - - - - - - - - - - - - +
 * |     Extended payload length continued, if payload len == 127  |
 * + - - - - - - - - - - - - - - - +-------------------------------+
 * |                               |Masking-key, if MASK set to 1  |
 * +-------------------------------+-------------------------------+
 * | Masking-key (continued)       |          Payload Data         |
 * +-------------------------------- - - - - - - - - - - - - - - - +
 * :                     Payload Data continued ...                :
 * + - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - +
 * |                     Payload Data continued ...                |
 * +---------------------------------------------------------------+
 *
 * INTERNAL API
 */
@InternalApi
private[http] object FrameEventParser extends ByteStringParser[FrameEvent] {
  import ByteStringParser._

  override def createLogic(attr: Attributes) = new ParsingLogic {
    startWith(ReadFrameHeader)

    trait Step extends ParseStep[FrameEvent] {
      override def onTruncation(): Unit = failStage(new ProtocolException("Data truncated"))
    }

    object ReadFrameHeader extends Step {
      override def parse(reader: ByteReader): ParseResult[FrameEvent] = {
        import Protocol._

        val flagsAndOp = reader.readByte()
        val maskAndLength = reader.readByte()

        val flags = flagsAndOp & FLAGS_MASK
        val op = flagsAndOp & OP_MASK

        val maskBit = (maskAndLength & MASK_MASK) != 0
        val length7 = maskAndLength & LENGTH_MASK

        val length =
          length7 match {
            case 126 => reader.readShortBE().toLong
            case 127 => reader.readLongBE()
            case x   => x.toLong
          }

        if (length < 0) throw new ProtocolException("Highest bit of 64bit length was set")

        val mask =
          if (maskBit) Some(reader.readIntBE())
          else None

        def isFlagSet(mask: Int): Boolean = (flags & mask) != 0
        val header =
          FrameHeader(
            Opcode.forCode(op.toByte),
            mask,
            length,
            fin = isFlagSet(FIN_MASK),
            rsv1 = isFlagSet(RSV1_MASK),
            rsv2 = isFlagSet(RSV2_MASK),
            rsv3 = isFlagSet(RSV3_MASK))

        val takeNow = (header.length min reader.remainingSize).toInt
        val thisFrameData = reader.take(takeNow)
        val noMoreData = thisFrameData.length == length

        val nextState =
          if (noMoreData) ReadFrameHeader
          else new ReadData(length - thisFrameData.length)

        ParseResult(Some(FrameStart(header, thisFrameData.compact)), nextState, true)
      }
    }

    class ReadData(_remaining: Long) extends Step {
      override def canWorkWithPartialData = true
      var remaining = _remaining
      override def parse(reader: ByteReader): ParseResult[FrameEvent] =
        if (reader.remainingSize < remaining) {
          remaining -= reader.remainingSize
          ParseResult(Some(FrameData(reader.takeAll(), lastPart = false)), this, true)
        } else {
          ParseResult(Some(FrameData(reader.take(remaining.toInt), lastPart = true)), ReadFrameHeader, true)
        }
    }
  }

  def mask(bytes: ByteString, _mask: Option[Int]): ByteString =
    _mask match {
      case Some(m) => mask(bytes, m)._1
      case None    => bytes
    }

  def mask(bytes: ByteString, mask: Int): (ByteString, Int) = {
    val buffer = bytes.toArray[Byte]
    val end = buffer.length & ~7
    if (end != 0) maskWords(buffer, end, mask)

    var offset = end
    while (offset < buffer.length) {
      val shift = 24 - ((offset & 3) << 3)
      buffer(offset) = (buffer(offset) ^ (mask >>> shift)).toByte
      offset += 1
    }

    val newMask = Integer.rotateLeft(mask, (bytes.length % 4) * 8)
    (ByteString.fromArrayUnsafe(buffer), newMask)
  }

  private val MaskWordView = MethodHandles.byteArrayViewVarHandle(classOf[Array[Long]], ByteOrder.BIG_ENDIAN)

  private def maskWords(buffer: Array[Byte], end: Int, mask: Int): Unit = {
    // The network-order mask repeats every four bytes.
    val key = Integer.toUnsignedLong(mask)
    val repeated = (key << 32) | key
    var offset = 0
    while (offset < end) {
      // An explicit type ascription keeps the VarHandle load primitive on both Scala 2 and 3.
      val word = MaskWordView.get(buffer, offset): Long
      MaskWordView.set(buffer, offset, word ^ repeated)
      offset += java.lang.Long.BYTES
    }
  }

  def parseCloseCode(data: ByteString): Option[(Int, String)] = {
    def invalid(reason: String) = Some((Protocol.CloseCodes.ProtocolError, s"Peer sent illegal close frame ($reason)."))

    if (data.length >= 2) {
      val code = ((data(0) & 0xFF) << 8) | (data(1) & 0xFF)
      val message = Utf8Decoder.decode(data.drop(2))
      if (!Protocol.CloseCodes.isValid(code)) invalid(s"invalid close code '$code'")
      else if (message.isFailure) invalid("close reason message is invalid UTF8")
      else Some((code, message.get))
    } else if (data.length == 1) invalid("close code must be length 2 but was 1") // must be >= length 2 if not empty
    else None
  }

  override def toString: String = "FrameEventParser"
}
