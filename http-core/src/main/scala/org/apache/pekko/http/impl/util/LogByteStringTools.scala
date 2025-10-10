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

import org.apache.pekko
import pekko.NotUsed
import pekko.annotation.InternalApi
import pekko.event.Logging
import pekko.stream.Attributes
import pekko.stream.TLSProtocol._
import pekko.stream.scaladsl.{ BidiFlow, Flow }
import pekko.util.ByteString

import scala.reflect.ClassTag

/**
 * INTERNAL API
 *
 * Flow and BidiFlow stages to log streams of ByteString.
 */
@InternalApi
private[pekko] object LogByteStringTools {
  val MaxBytesPrinted = 16 * 5

  private val LogFailuresOnDebugAttributes = Attributes.logLevels(onFailure = Logging.DebugLevel)

  def logByteStringBidi(name: String, maxBytes: Int = MaxBytesPrinted)
      : BidiFlow[ByteString, ByteString, ByteString, ByteString, NotUsed] =
    BidiFlow.fromFlows(
      logByteString(s"$name DOWN", maxBytes),
      logByteString(s"$name UP  ", maxBytes))

  def logToStringBidi[A: ClassTag, B: ClassTag](
      name: String, maxBytes: Int = MaxBytesPrinted): BidiFlow[A, A, B, B, NotUsed] = {
    def limitedName[T](implicit tag: ClassTag[T]): String = Logging.simpleName(tag.runtimeClass).take(20).mkString
    BidiFlow.fromFlows(
      logToString(s"$name ${limitedName[A]}", maxBytes),
      logToString(s"$name ${limitedName[B]}", maxBytes))
  }

  def logByteString(name: String, maxBytes: Int = MaxBytesPrinted): Flow[ByteString, ByteString, NotUsed] =
    Flow[ByteString].log(name, printByteString(_, maxBytes)).addAttributes(LogFailuresOnDebugAttributes)

  def logToString[A](name: String, maxBytes: Int = MaxBytesPrinted): Flow[A, A, NotUsed] =
    Flow[A].log(name, _.toString().take(maxBytes)).addAttributes(LogFailuresOnDebugAttributes)

  def logTLSBidi(name: String, maxBytes: Int = MaxBytesPrinted)
      : BidiFlow[SslTlsOutbound, SslTlsOutbound, SslTlsInbound, SslTlsInbound, NotUsed] =
    BidiFlow.fromFlows(
      logTlsOutbound(s"$name ToNet  ", maxBytes),
      logTlsInbound(s"$name FromNet", maxBytes))

  def logTlsOutbound(name: String, maxBytes: Int = MaxBytesPrinted): Flow[SslTlsOutbound, SslTlsOutbound, NotUsed] =
    Flow[SslTlsOutbound].log(name,
      {
        case SendBytes(bytes)       => "SendBytes " + printByteString(bytes, maxBytes)
        case n: NegotiateNewSession => n.toString
      }).addAttributes(LogFailuresOnDebugAttributes)

  def logTlsInbound(name: String, maxBytes: Int = MaxBytesPrinted): Flow[SslTlsInbound, SslTlsInbound, NotUsed] =
    Flow[SslTlsInbound].log(name,
      {
        case s: SessionTruncated          => s
        case SessionBytes(session, bytes) => "SessionBytes " + printByteString(bytes, maxBytes)
      }).addAttributes(LogFailuresOnDebugAttributes)

  def printByteString(bytes: ByteString, maxBytes: Int = MaxBytesPrinted, addPrefix: Boolean = true,
      indent: String = " "): String = {
    def formatBytes(bs: ByteString): Iterator[String] = {
      def asHex(b: Byte): String = "%02X".format(b)

      def formatLine(bs: ByteString): String = {
        val hex = bs.map(asHex).mkString(" ")
        val ascii = bs.map(asASCII).mkString
        f"$indent%s$hex%-48s | $ascii"
      }
      def formatBytes(bs: ByteString): String =
        bs.grouped(16).map(formatLine).mkString("\n")

      val prefix = s"${indent}ByteString(${bs.size} bytes)"

      if (bs.size <= maxBytes * 2) Iterator(if (addPrefix) prefix + "\n" else "", formatBytes(bs))
      else
        Iterator(
          if (addPrefix) s"$prefix first + last $maxBytes:\n" else "",
          formatBytes(bs.take(maxBytes)),
          s"\n$indent                    ... [${bs.size - (maxBytes * 2)} bytes omitted] ...\n",
          formatBytes(bs.takeRight(maxBytes)))
    }

    formatBytes(bytes).mkString("")
  }
  def asASCII(b: Byte): Char =
    if (b >= 0x20 && b < 0x7F) b.toChar
    else '.'

  def logTLSBidiBySetting(tag: String, maxBytesSetting: Option[Int])
      : BidiFlow[SslTlsOutbound, SslTlsOutbound, SslTlsInbound, SslTlsInbound, Any] =
    maxBytesSetting
      .map(logTLSBidi(tag, _)).getOrElse(BidiFlow.identity)
}
