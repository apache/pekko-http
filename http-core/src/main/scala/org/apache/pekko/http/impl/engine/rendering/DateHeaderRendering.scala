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

package org.apache.pekko.http.impl.engine.rendering

import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.{ ClassicActorSystemProvider, Scheduler }
import pekko.annotation.InternalApi
import pekko.http.impl.util.ByteArrayRendering
import pekko.http.impl.util.Rendering.CrLf
import pekko.http.javadsl.model.headers.LastModified.StaticResourceOrigin
import pekko.http.scaladsl.model.headers.`Last-Modified`
import pekko.http.scaladsl.model.{ DateTime, headers }

/** INTERNAL API */
@InternalApi private[http] trait DateHeaderRendering {
  def renderHeaderPair(): (String, String)
  def renderHeaderBytes(): Array[Byte]
  def renderHeaderValue(): String
}

/** INTERNAL API */
@InternalApi private[http] object DateHeaderRendering {
  def forLastModified(lastModified: `Last-Modified`, rendering: DateHeaderRendering): DateHeaderRendering = {
    val origin = if (lastModified eq null) null else StaticResourceOrigin.of(lastModified)
    if (origin eq null) rendering
    else
      rendering match {
        case cached: ResourceDateRendering => cached.forResourceDate(lastModified.date, origin)
        case _                             => renderResourceDate(lastModified.date, origin)
      }
  }

  private trait ResourceDateRendering extends DateHeaderRendering {
    def forResourceDate(lastModified: DateTime, origin: StaticResourceOrigin): DateHeaderRendering
  }

  private def renderResourceDate(lastModified: DateTime, origin: StaticResourceOrigin)
      : DateHeaderRendering = {
    // For future file metadata the conditional and Last-Modified already use this exact origin sample.
    val date = if (origin.wasClamped()) lastModified else DateTime(origin.sampledAtMillis())
    new DateHeaderRendering {
      override def renderHeaderValue(): String = date.toRfc1123DateTimeString
      override def renderHeaderPair(): (String, String) = "date" -> renderHeaderValue()
      override def renderHeaderBytes(): Array[Byte] = headerBytes(renderHeaderValue())
    }
  }

  private def headerBytes(value: String): Array[Byte] =
    (new ByteArrayRendering(48) ~~ headers.Date ~~ value ~~ CrLf).get

  def apply(now: () => Long = () => System.currentTimeMillis())(
      implicit system: ClassicActorSystemProvider): DateHeaderRendering =
    apply(system.classicSystem.scheduler, now)(system.classicSystem.dispatcher)

  def apply(scheduler: Scheduler, now: () => Long)(implicit ec: ExecutionContext): DateHeaderRendering = {
    sealed trait DateState

    // Date has not been used for a while
    case object Idle extends DateState
    final case class AutoUpdated(date: DateTime) extends DateState with DateHeaderRendering {
      val value: String = date.toRfc1123DateTimeString
      var wasUsed: Boolean = false
      val headerPair: (String, String) = "date" -> value
      val bytes: Array[Byte] = headerBytes(value)
      override def renderHeaderValue(): String = value
      override def renderHeaderPair(): (String, String) = headerPair
      override def renderHeaderBytes(): Array[Byte] = bytes
    }
    def sampleDate(): AutoUpdated = AutoUpdated(DateTime(now()))

    val dateState = new AtomicReference[DateState](Idle)
    val updateInterval = 1.second
    def scheduleAutoUpdate(): Unit =
      try scheduler.scheduleOnce(updateInterval)(autoUpdate())
      catch {
        case _: IllegalStateException =>
          // can fail during shutdown, no need to be noisy here
          dateState.set(Idle)
      }

    def autoUpdate(): Unit =
      dateState.get() match {
        case a: AutoUpdated =>
          if (a.wasUsed) {
            dateState.set(sampleDate())
            scheduleAutoUpdate()
          } else
            dateState.set(Idle) // wasn't retrieved, no reason to continue autoupdating
        case Idle =>
          new IllegalStateException("Should not happen, invariant is either state == Idle or scheduled both never both")
      }

    def get(rendered: AutoUpdated): AutoUpdated =
      dateState.get() match {
        case a: AutoUpdated =>
          // might not be instantly visible on updater thread
          // which might prevent automatic rescheduling in the worst case
          a.wasUsed = true
          a
        case s @ Idle =>
          val newValue = if (rendered ne null) rendered else sampleDate()
          newValue.wasUsed = true
          // use CAS to avoid that multiple accessing threads schedule multiple timers
          if (!dateState.compareAndSet(s, newValue)) get(newValue)
          else {
            scheduleAutoUpdate()
            newValue
          }
      }

    new ResourceDateRendering {
      override def renderHeaderPair(): (String, String) = get(null).headerPair
      override def renderHeaderBytes(): Array[Byte] = get(null).bytes
      override def renderHeaderValue(): String = get(null).value

      override def forResourceDate(lastModified: DateTime, origin: StaticResourceOrigin)
          : DateHeaderRendering = {
        val cached = get(null)
        val suitable =
          if (origin.wasClamped()) cached.date.clicks == lastModified.clicks
          else cached.date.clicks >= lastModified.clicks
        if (suitable) cached else renderResourceDate(lastModified, origin)
      }
    }
  }

  val Unavailable = new DateHeaderRendering {
    override def renderHeaderPair(): (String, String) =
      throw new IllegalStateException("DateHeaderRendering is not available here")
    override def renderHeaderBytes(): Array[Byte] =
      throw new IllegalStateException("DateHeaderRendering is not available here")
    override def renderHeaderValue(): String =
      throw new IllegalStateException("DateHeaderRendering is not available here")
  }
}
