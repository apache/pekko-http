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
import pekko.http.scaladsl.settings.OversizedSseStrategy
import pekko.stream.{ Attributes, FlowShape, Inlet, Outlet }
import pekko.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import pekko.util.ByteString

/** INTERNAL API */
@InternalApi
private object LineParser {
  val CR = '\r'.toByte
  val LF = '\n'.toByte
}

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
      import LineParser._
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

        @tailrec
        def parseLines(
            bs: ByteString,
            from: Int = 0,
            at: Int,
            parsedLines: Vector[String] = Vector.empty,
            lastCharWasCr: Boolean): (ByteString, Vector[String], Boolean) =
          if (at >= bs.length)
            (bs.drop(from), parsedLines, lastCharWasCr)
          else
            bs(at) match {
              case CR if at < bs.length - 1 && bs(at + 1) == LF =>
                // Lookahead for LF after CR
                val lineByteSize = at - from
                val line = bs.slice(from, at).utf8String
                val processedLine = if (maxLineSize > 0 && lineByteSize > maxLineSize) {
                  handleLineOversized(lineByteSize, line)
                } else {
                  Some(line)
                }
                val newParsedLines = processedLine.fold(parsedLines)(parsedLines :+ _)
                parseLines(bs, at + 2, at + 2, newParsedLines, lastCharWasCr = false)
              case CR =>
                // if is a CR but we don't know the next character, slice it but flag that the last character was a CR so if the next happens to be a LF we just ignore
                val lineByteSize = at - from
                val line = bs.slice(from, at).utf8String
                val processedLine = if (maxLineSize > 0 && lineByteSize > maxLineSize) {
                  handleLineOversized(lineByteSize, line)
                } else {
                  Some(line)
                }
                val newParsedLines = processedLine.fold(parsedLines)(parsedLines :+ _)
                parseLines(bs, at + 1, at + 1, newParsedLines, lastCharWasCr = true)
              case LF if lastCharWasCr =>
                // if is a LF and we just sliced a CR then we simply advance
                parseLines(bs, at + 1, at + 1, parsedLines, lastCharWasCr = false)
              case LF =>
                // a LF that wasn't preceded by a CR means we found a new slice
                val lineByteSize = at - from
                val line = bs.slice(from, at).utf8String
                val processedLine = if (maxLineSize > 0 && lineByteSize > maxLineSize) {
                  handleLineOversized(lineByteSize, line)
                } else {
                  Some(line)
                }
                val newParsedLines = processedLine.fold(parsedLines)(parsedLines :+ _)
                parseLines(bs, at + 1, at + 1, newParsedLines, lastCharWasCr = false)
              case _ =>
                // for other input, simply advance
                // Reset lastCharWasCr if we encounter any non-LF character after CR
                parseLines(bs, from, at + 1, parsedLines, lastCharWasCr = false)
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
