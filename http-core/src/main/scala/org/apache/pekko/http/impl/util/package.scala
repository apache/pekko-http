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

package org.apache.pekko.http.impl

import java.nio.charset.StandardCharsets

import language.implicitConversions
import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, Future }
import scala.reflect.ClassTag
import scala.util.{ Failure, Success }
import scala.util.matching.Regex

import com.typesafe.config.Config
import org.apache.pekko
import pekko.actor._
import pekko.http.impl.engine.parsing.ParserOutput.RequestStart
import pekko.http.scaladsl.model.{ HttpEntity, HttpRequest, HttpResponse }
import pekko.stream.scaladsl.Source
import pekko.stream.stage._
import pekko.util.ByteString

package object util {
  private[http] val UTF8 = StandardCharsets.UTF_8
  private[http] val ASCII = StandardCharsets.US_ASCII
  private[http] val ISO88591 = StandardCharsets.ISO_8859_1

  private[http] val EmptyByteArray = Array.empty[Byte]

  private[http] def actorSystem(implicit refFactory: ActorRefFactory): ExtendedActorSystem =
    refFactory match {
      case x: ActorContext        => actorSystem(x.system)
      case x: ExtendedActorSystem => x
      case x                      => throw new IllegalStateException(s"Unknown factory $x")
    }

  private[http] implicit def enhanceByteArray(array: Array[Byte]): EnhancedByteArray = new EnhancedByteArray(array)
  private[http] implicit def enhanceConfig(config: Config): EnhancedConfig = new EnhancedConfig(config)
  private[http] implicit def enhanceString_(s: String): EnhancedString = new EnhancedString(s)
  private[http] implicit def enhanceRegex(regex: Regex): EnhancedRegex = new EnhancedRegex(regex)
  private[http] implicit def enhanceByteStrings(
      byteStrings: IterableOnce[ByteString]): EnhancedByteStringTraversableOnce =
    new EnhancedByteStringTraversableOnce(byteStrings)
  private[http] implicit def enhanceByteStringsMat[Mat](
      byteStrings: Source[ByteString, Mat]): EnhancedByteStringSource[Mat] =
    new EnhancedByteStringSource(byteStrings)

  private[this] var eventStreamLogger: ActorRef = _
  private[http] def installEventStreamLoggerFor(channel: Class[_])(implicit system: ActorSystem): Unit = {
    synchronized {
      if (eventStreamLogger == null)
        eventStreamLogger =
          system.actorOf(Props[util.EventStreamLogger]().withDeploy(Deploy.local), name = "event-stream-logger")
    }
    system.eventStream.subscribe(eventStreamLogger, channel)
  }
  private[http] def installEventStreamLoggerFor[T](implicit ct: ClassTag[T], system: ActorSystem): Unit =
    installEventStreamLoggerFor(ct.runtimeClass)

  private[http] implicit class AddFutureAwaitResult[T](val future: Future[T]) extends AnyVal {

    /** "Safe" Await.result that doesn't throw away half of the stacktrace */
    def awaitResult(atMost: Duration): T = {
      Await.ready(future, atMost)
      future.value.get match {
        case Success(t)  => t
        case Failure(ex) => throw new RuntimeException(
            "Trying to await result of failed Future, see the cause for the original problem.", ex)
      }
    }
  }

  private[http] def humanReadableByteCount(bytes: Long, si: Boolean): String = {
    val unit = if (si) 1000 else 1024
    if (bytes >= unit) {
      val exp = (math.log(bytes.toDouble) / math.log(unit)).toInt
      val pre = if (si) "kMGTPE".charAt(exp - 1).toString else "KMGTPE".charAt(exp - 1).toString + 'i'
      "%.1f %sB".format(bytes / math.pow(unit, exp), pre)
    } else bytes.toString + "  B"
  }

  private[http] implicit class RichHttpRequest(val request: HttpRequest) extends AnyVal {
    def debugString: String = s"${request.method.value} ${request.uri.path} ${entityDebugInfo(request.entity)}"
  }
  private[http] implicit class RichRequestStart(val request: RequestStart) extends AnyVal {
    def debugString: String = s"${request.method.value} ${request.uri.path}"
  }
  private[http] implicit class RichHttpResponse(val response: HttpResponse) extends AnyVal {
    def debugString: String = s"${response.status.value} ${entityDebugInfo(response.entity)}"
  }
  private def entityDebugInfo(e: HttpEntity): String = e match {
    case HttpEntity.Empty                 => "Empty"
    case HttpEntity.Strict(_, data)       => s"Strict(${data.size} bytes)"
    case HttpEntity.Default(_, length, _) => s"Default($length bytes)"
    case _: HttpEntity.CloseDelimited     => "CloseDelimited"
    case _: HttpEntity.IndefiniteLength   => "IndefiniteLength"
    case _: HttpEntity.Chunked            => "Chunked"
  }
}

package util {

  import pekko.http.scaladsl.model.{ ContentType, EntityStreamException, ErrorInfo, HttpEntity }
  import pekko.stream.{ Attributes, FlowShape, Inlet, Outlet }

  import scala.concurrent.duration.FiniteDuration

  private[http] class ToStrict(timeout: FiniteDuration, maxBytes: Option[Long], contentType: ContentType)
      extends GraphStage[FlowShape[ByteString, HttpEntity.Strict]] {

    val byteStringIn = Inlet[ByteString]("ToStrict.byteStringIn")
    val httpEntityOut = Outlet[HttpEntity.Strict]("ToStrict.httpEntityOut")

    override def initialAttributes = Attributes.name("ToStrict")

    override val shape = FlowShape(byteStringIn, httpEntityOut)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new TimerGraphStageLogic(shape) with InHandler with OutHandler {
        private val bytes = ByteString.newBuilder
        private var emptyStream = false

        override def preStart(): Unit = scheduleOnce("ToStrictTimeoutTimer", timeout)

        override def onPull(): Unit = {
          if (emptyStream) {
            push(httpEntityOut, HttpEntity.Strict(contentType, ByteString.empty))
            completeStage()
          } else pull(byteStringIn)
        }

        override def onPush(): Unit = {
          bytes ++= grab(byteStringIn)
          maxBytes match {
            case Some(max) if bytes.length > max =>
              failStage(new EntityStreamException(new ErrorInfo("Request too large",
                s"Request was longer than the maximum of $max")))
            case _ =>
              pull(byteStringIn)
          }
        }
        override def onUpstreamFinish(): Unit = {
          if (isAvailable(httpEntityOut)) {
            push(httpEntityOut, HttpEntity.Strict(contentType, bytes.result()))
            completeStage()
          } else emptyStream = true
        }

        setHandlers(byteStringIn, httpEntityOut, this)

        override def onTimer(key: Any): Unit =
          failStage(new java.util.concurrent.TimeoutException(
            s"HttpEntity.toStrict timed out after $timeout while still waiting for outstanding data"))
      }

    override def toString = "ToStrict"
  }

  private[http] class EventStreamLogger extends Actor with ActorLogging {
    def receive = { case x => log.warning(x.toString) }
  }

  private[http] trait LogMessages extends ActorLogging { this: Actor =>
    def logMessages(mark: String = "")(r: Receive): Receive =
      new Receive {
        def isDefinedAt(x: Any): Boolean = r.isDefinedAt(x)
        def apply(x: Any): Unit = {
          log.debug(s"[$mark] received: $x")
          r(x)
        }
      }
  }
}
