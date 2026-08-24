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

package org.apache.pekko.http
package scaladsl
package unmarshalling
package sse

import scala.annotation.tailrec

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.event.Logging
import pekko.http.impl.util.HttpConstants._
import pekko.http.scaladsl.settings.OversizedSseStrategy
import pekko.stream.{ Attributes, FlowShape, Inlet, Outlet }
import pekko.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import pekko.util.ByteString

/**
 * A wrapper for an SSE line which exceeds the configured limit. Used for pattern matching.
 * @param line The oversized contents of the SSE line being parsed.
 */
case class OversizedSseLine(line: String)

/** INTERNAL API */
@InternalApi
private final class LineParser(maxLineSize: Int,
    oversizedStrategy: OversizedSseStrategy = OversizedSseStrategy.FailStream)
    extends GraphStage[FlowShape[ByteString, String]] {

  def this(maxLineSize: Int) = this(maxLineSize, OversizedSseStrategy.FailStream)

  override val shape = FlowShape(Inlet[ByteString]("LineParser.in"), Outlet[String]("LineParser.out"))

  override def createLogic(attributes: Attributes) =
    new GraphStageLogic(shape) with InHandler with OutHandler {
      import shape._

      private var buffer = ByteString.empty
      private var lastCharWasCr = false
      private lazy val log = Logging(materializer.system, classOf[LineParser])

      setHandlers(in, out, this)

      override def onPush() = {
        def handleLineOversized(lineByteSize: Int, line: String): Option[String] = {
          oversizedStrategy match {
            case OversizedSseStrategy.FailStream =>
              failStage(new IllegalStateException(
                s"SSE line size: $lineByteSize exceeds max-line-size: $maxLineSize. " +
                s"Configure pekko.http.sse.max-line-size or use oversized-message-handling setting."))
              None
            case OversizedSseStrategy.LogAndSkip =>
              log.warning("Skipping oversized SSE message: {} bytes > {} max-line-size", lineByteSize, maxLineSize)
              None
            case OversizedSseStrategy.Truncate =>
              log.info("Truncating oversized SSE message: {} bytes > {} max-line-size", lineByteSize, maxLineSize)
              Some(line.take(maxLineSize))
            case OversizedSseStrategy.DeadLetter =>
              materializer.system.deadLetters ! OversizedSseLine(line)
              None
          }
        }

        def lineAt(bs: ByteString, from: Int, until: Int): Option[String] = {
          val lineByteSize = until - from
          val line = bs.slice(from, until).utf8String
          if (maxLineSize > 0 && lineByteSize > maxLineSize) handleLineOversized(lineByteSize, line)
          else Some(line)
        }

        @tailrec
        def parseLines(
            bs: ByteString,
            from: Int = 0,
            at: Int,
            parsedLines: Vector[String] = Vector.empty,
            lastCharWasCr: Boolean): (ByteString, Vector[String], Boolean) =
          if (at >= bs.length)
            (bs.drop(from), parsedLines, lastCharWasCr)
          else if (lastCharWasCr && bs(at) == LF_BYTE)
            // the LF of a CRLF whose CR already ended a line in a previous chunk, simply advance
            parseLines(bs, at + 1, at + 1, parsedLines, lastCharWasCr = false)
          else {
            // jump straight to the next line terminator instead of testing every single byte:
            // ByteString.indexOf scans several bytes at a time and, unlike indexed access, does not
            // walk the fragment list of a multi-chunk ByteString on every byte
            val crIx = bs.indexOf(CR_BYTE, at)
            val lfIx = bs.indexOf(LF_BYTE, at)
            val terminator =
              if (crIx == -1) lfIx
              else if (lfIx == -1) crIx
              else math.min(crIx, lfIx)

            if (terminator == -1)
              // no line terminator in the rest of the buffer
              (bs.drop(from), parsedLines, false)
            else {
              val newParsedLines = lineAt(bs, from, terminator).fold(parsedLines)(parsedLines :+ _)
              if (terminator == lfIx)
                parseLines(bs, terminator + 1, terminator + 1, newParsedLines, lastCharWasCr = false)
              else if (terminator < bs.length - 1 && bs(terminator + 1) == LF_BYTE)
                // lookahead for LF after CR
                parseLines(bs, terminator + 2, terminator + 2, newParsedLines, lastCharWasCr = false)
              else
                // a CR but we don't know the next character yet, flag it so that a LF starting the
                // next chunk is ignored
                parseLines(bs, terminator + 1, terminator + 1, newParsedLines, lastCharWasCr = true)
            }
          }

        // start the search where it ended, prevent iterating over all the buffer again
        val currentBufferStart = math.max(0, buffer.length - 1)
        buffer = parseLines(buffer ++ grab(in), at = currentBufferStart, lastCharWasCr = lastCharWasCr) match {
          case (remaining, parsedLines, _lastCharWasCr) =>
            if (parsedLines.nonEmpty) emitMultiple(out, parsedLines) else pull(in)
            lastCharWasCr = _lastCharWasCr
            remaining
        }
      }

      override def onPull() = pull(in)
    }
}
