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
import pekko.NotUsed
import pekko.annotation.InternalApi
import pekko.event.Logging
import pekko.http.impl.util.LogByteStringTools
import pekko.stream.Attributes
import pekko.stream.Attributes.LogLevels
import pekko.stream.scaladsl.{ BidiFlow, Flow }
import pekko.util.ByteString

/**
 * INTERNAL API
 */
@InternalApi
private[ws] object FrameLogger {
  final val maxBytes = 16

  def logFramesIfEnabled(
      shouldLog: Boolean): BidiFlow[FrameEventOrError, FrameEventOrError, FrameEvent, FrameEvent, NotUsed] =
    if (shouldLog) bidi
    else BidiFlow.identity

  def bidi: BidiFlow[FrameEventOrError, FrameEventOrError, FrameEvent, FrameEvent, NotUsed] =
    BidiFlow.fromFlows(
      Flow[FrameEventOrError].log(s"${Console.RED}DOWN${Console.RESET}", FrameLogger.logEvent),
      Flow[FrameEvent].log(s"${Console.GREEN} UP ${Console.RESET}", FrameLogger.logEvent))
      .addAttributes(Attributes(LogLevels(Logging.DebugLevel, Logging.DebugLevel, Logging.DebugLevel)))

  def logEvent(frameEvent: FrameEventOrError): String = {
    import Console._

    def displayLogEntry(
        frameType: String, length: Long, data: String, lastPart: Boolean, flags: Option[String]*): String = {
      val f = if (flags.nonEmpty) s" $RED${flags.flatten.mkString(" ")}" else ""
      val l = if (length > 0) f" $YELLOW$length%d bytes" else ""
      f"$GREEN$frameType%s$f$l$RESET $data${if (!lastPart) " ..." else ""}"
    }

    def flag(value: Boolean, name: String): Option[String] = if (value) Some(name) else None
    def hex(bytes: ByteString): String = {
      val num = math.min(maxBytes, bytes.size)
      val ellipsis = if (num < bytes.size) s" [... ${bytes.size - num} more bytes]" else ""
      val first = bytes.take(num)
      val h = first.map("%02x" format _).mkString(" ")
      val ascii = first.map(LogByteStringTools.asASCII).mkString
      s"$WHITE$h$RESET | $WHITE$ascii$RESET$ellipsis"
    }

    frameEvent match {
      case f @ FrameStart(header, data) => displayLogEntry(header.opcode.short, header.length, hex(data), f.lastPart,
          flag(header.fin, "FIN"), flag(header.rsv1, "RSV1"), flag(header.rsv2, "RSV2"), flag(header.rsv3, "RSV3"))
      case FrameData(data, lastPart) => displayLogEntry("DATA", 0, hex(data), lastPart)
      case FrameError(ex)            =>
        f"${RED}Error: ${ex.getMessage}$RESET"
    }
  }
}
