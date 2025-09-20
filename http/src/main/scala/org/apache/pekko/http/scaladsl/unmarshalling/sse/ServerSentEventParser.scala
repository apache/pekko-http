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

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.event.Logging
import pekko.http.scaladsl.model.sse.ServerSentEvent
import pekko.http.scaladsl.settings.OversizedSseStrategy
import pekko.stream.{ Attributes, FlowShape, Inlet, Outlet }
import pekko.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }

/** INTERNAL API */
@InternalApi
private object ServerSentEventParser {

  object PosInt {
    def unapply(s: String): Option[Int] =
      try { Some(s.trim.toInt) }
      catch { case _: NumberFormatException => None }
  }

  final class Builder {

    private var data = Vector.empty[String]

    private var eventType = Option.empty[String]

    private var id = Option.empty[String]

    private var retry = Option.empty[Int]

    private var _size = 0

    def appendData(value: String): Unit = {
      _size += 5 + value.length
      data :+= value
    }

    def setType(value: String): Unit = {
      val oldSize = eventType.fold(0)(6 + _.length)
      _size += 6 + value.length - oldSize
      eventType = Some(value)
    }

    def setId(value: String): Unit = {
      val oldSize = id.fold(0)(3 + _.length)
      _size += 3 + value.length - oldSize
      id = Some(value)
    }

    def setRetry(value: Int, length: Int): Unit = {
      val oldSize = retry.fold(0)(6 + _.toString.length)
      _size += 6 + length - oldSize
      retry = Some(value)
    }

    def hasData: Boolean =
      data.nonEmpty

    def size: Int =
      _size

    def build(): ServerSentEvent =
      ServerSentEvent(data.mkString("\n"), eventType, id, retry)

    def reset(): Unit = {
      data = Vector.empty[String]
      eventType = None
      id = None
      retry = None
      _size = 0
    }
  }

  private final val Data = "data"

  private final val EventType = "event"

  private final val Id = "id"

  private final val Retry = "retry"

  private val Field = """([^:]+): ?(.*)""".r
}

case class OversizedSseEvent(event: ServerSentEvent)

/** INTERNAL API */
@InternalApi
private final class ServerSentEventParser(
    maxEventSize: Int,
    emitEmptyEvents: Boolean,
    oversizedStrategy: OversizedSseStrategy = OversizedSseStrategy.FailStream)
    extends GraphStage[FlowShape[String, ServerSentEvent]] {

  def this(maxEventSize: Int, emitEmptyEvents: Boolean) =
    this(maxEventSize, emitEmptyEvents, OversizedSseStrategy.FailStream)

  override val shape =
    FlowShape(Inlet[String]("ServerSentEventParser.in"), Outlet[ServerSentEvent]("ServerSentEventParser.out"))

  override def createLogic(attributes: Attributes) =
    new GraphStageLogic(shape) with InHandler with OutHandler {
      import ServerSentEventParser._
      import shape._

      private val builder = new Builder()
      private lazy val log = Logging(materializer.system, classOf[ServerSentEventParser])
      @volatile private var shouldSkipUntilEventEnd = false

      setHandlers(in, out, this)

      override def onPush(): Unit = {
        val line = grab(in)
        if (shouldSkipUntilEventEnd) { // Max event size was previously reached. Skip successive lines until event ends
          if (line.isEmpty) shouldSkipUntilEventEnd = false // Stop skipping when end of event (empty line) is reached
          pull(in) // Already reported oversized event (below). Drop and continue to next line.
        } else if (maxEventSize > 0 && builder.size + line.length > maxEventSize) { // Next line exceeds the size limit
          shouldSkipUntilEventEnd = true
          oversizedStrategy match {
            case OversizedSseStrategy.FailStream =>
              builder.appendData(line)
              val event = builder.build()
              failStage(new IllegalStateException(
                s"Oversized SSE Event ${event.id.fold("") { id => s"at ID: $id " }}" +
                s"with size: ${builder.size} exceeds max-event-size: $maxEventSize." +
                s" Configure pekko.http.sse.max-event-size or use another oversized-message-handling setting."))
            case OversizedSseStrategy.LogAndSkip =>
              builder.appendData(line)
              val event = builder.build()
              log.warning(
                s"Oversized SSE Event ${event.id.fold("") { id => s"at ID: $id " }}" +
                s"with size: ${builder.size} exceeds max-event-size: $maxEventSize.")
              pull(in)
            case OversizedSseStrategy.Truncate =>
              // Because truncating some field types can categorically change the meaning of the event or stream
              // (e.g. `id:` or `event:` or `retry:`), Truncating an event (as opposed to a single line) is interpreted
              // as dropping the entire line which would exceed the message size length. So throw away `line`.
              val event = builder.build()
              log.info(
                s"Oversized SSE Event ${event.id.fold("") { id => s"at ID: $id " }}" +
                s"with size: ${builder.size + line.length} exceeds max-event-size: $maxEventSize." +
                s" Truncating event to last completed line at event size: ${builder.size}.")
              push(out, event)
            case OversizedSseStrategy.DeadLetter =>
              builder.appendData(line)
              val event = builder.build()
              materializer.system.deadLetters ! OversizedSseEvent(event)
              pull(in)
          }
          builder.reset()
        } else if (line.isEmpty) { // An event is terminated with a new line. Complete what has been collected and publish.
          if (builder.hasData) { // Events without data are ignored according to the spec
            val event = builder.build()
            push(out, event)
          } else pull(in)
          builder.reset()
        } else { // Size is under limit. keep building event
          line match {
            case Id                                                    => builder.setId("")
            case Field(Data, data) if data.nonEmpty || emitEmptyEvents => builder.appendData(data)
            case Field(EventType, t) if t.nonEmpty                     => builder.setType(t)
            case Field(Id, id)                                         => builder.setId(id)
            case Field(Retry, s @ PosInt(r)) if r >= 0                 => builder.setRetry(r, s.length)
            case _                                                     => // ignore according to spec
          }
          pull(in)
        }
      }

      override def onPull(): Unit = pull(in)
    }
}
