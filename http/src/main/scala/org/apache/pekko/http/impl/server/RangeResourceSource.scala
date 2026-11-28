/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.http.impl.server

import java.util.concurrent.atomic.AtomicBoolean

import scala.concurrent.ExecutionContext.parasitic
import scala.util.{ Failure, Success, Try }
import scala.util.control.NonFatal

import org.apache.pekko
import pekko.NotUsed
import pekko.event.LoggingAdapter
import pekko.http.RangeResponsePolicy
import pekko.http.impl.engine.rendering.BodyPartRenderer
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.model.StatusCodes.{ OK, PartialContent, RangeNotSatisfiable }
import pekko.http.scaladsl.model.headers.{
  `Accept-Ranges`,
  `Content-Range`,
  `Last-Modified`,
  ByteRange,
  ETag,
  Range,
  RangeUnits
}
import pekko.http.scaladsl.server.RangeResource
import pekko.stream.{ Attributes, FlowShape, Inlet, Outlet, SourceShape }
import pekko.stream.scaladsl.Source
import pekko.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import pekko.util.ByteString

private[http] object RangeResourceSource {
  sealed trait Plan
  final case class Serve(status: StatusCode, headers: Seq[HttpHeader], entity: MessageEntity) extends Plan
  final case class TooMany(limit: Int, length: Long) extends Plan
  final case class Unsatisfiable(requested: Seq[ByteRange], length: Long) extends Plan

  def plan(
      resource: RangeResource,
      method: HttpMethod,
      range: Option[Range],
      policy: RangeResponsePolicy,
      log: LoggingAdapter): Option[Plan] = {
    import RangeSelection.{ coalesceRanges, indexRange, satisfiable }
    val length = resource.contentLength
    val acceptsRanges = method == HttpMethods.GET && length > 0L
    if (method != HttpMethods.GET && method != HttpMethods.HEAD) None
    else {
      val headers: Seq[HttpHeader] =
        (if (length > 0L) List(`Accept-Ranges`(RangeUnits.Bytes)) else Nil) ++
        resource.entityTag.map(ETag(_)) ++ resource.lastModified.map(`Last-Modified`(_))
      def fullEntity = {
        // HEAD needs the full selected length as response metadata, but has no reader behind its entity.
        val data = if (method == HttpMethods.HEAD) Source.empty else single(resource, 0L, length, log)
        HttpEntity.Default(resource.contentType, length, data)
      }

      Some(range.filter(header => acceptsRanges && header.rangeUnit == RangeUnits.Bytes) match {
        case Some(header) if header.ranges.size > policy.rangeCountLimit =>
          TooMany(policy.rangeCountLimit, length)
        case Some(header) =>
          header.ranges.filter(satisfiable(length)) match {
            case Nil       => Unsatisfiable(header.ranges, length)
            case Seq(only) =>
              val part = indexRange(length)(only)
              Serve(PartialContent, `Content-Range`(part.contentRange(length)) +: headers,
                HttpEntity.Default(resource.contentType, part.length, single(resource, part.start, part.length, log)))
            case ranges =>
              val coalesced = coalesceRanges(ranges.map(indexRange(length)), policy.rangeCoalescingThreshold)
              val ordered = if (policy.preserveRequestOrder) coalesced else coalesced.sortBy(_.start)
              Serve(PartialContent, headers, multipart(resource, ordered.map(_.contentRange(length)), log))
          }
        case None =>
          Serve(OK, headers, if (length == 0L) HttpEntity(resource.contentType, ByteString.empty) else fullEntity)
      })
    }
  }

  def toResponse(plan: Plan): HttpResponse = plan match {
    case Serve(status, headers, entity) => HttpResponse(status, headers, entity)
    case TooMany(_, length)             => unsatisfiedResponse(length)
    case Unsatisfiable(_, length)       => unsatisfiedResponse(length)
  }

  private def unsatisfiedResponse(length: Long): HttpResponse =
    HttpResponse(RangeNotSatisfiable, List(`Content-Range`(ContentRange.Unsatisfiable(length))))

  private def single(resource: RangeResource, offset: Long, length: Long, log: LoggingAdapter)
      : Source[ByteString, NotUsed] =
    Source.fromGraph(new ReaderScope(resource, reader => read(reader, offset, length), log))

  private def multipart(
      resource: RangeResource,
      ranges: Seq[ContentRange.Default],
      log: LoggingAdapter): MessageEntity = {
    val boundary = BodyPartRenderer.randomBoundary()
    val contentType = MediaTypes.`multipart/byteranges`.withBoundary(boundary).toContentType
    // The part renderer needs selected media metadata only; do not build an unused whole-body reader source.
    val representation = HttpEntity.empty(resource.contentType)
    val bytes = Source.fromGraph(new ReaderScope(resource,
      reader =>
        Source(ranges.zipWithIndex).flatMapConcat { case (range, index) =>
          val header = BodyPartRenderer.byteRangePartHeader(boundary, index == 0, representation, range, log)
          Source.single(header).concatLazy(Source.lazySource(() =>
            read(reader, range.first, range.last - range.first + 1L)))
        }.concatLazy(Source.single(BodyPartRenderer.closingBoundary(boundary))),
      log))
    HttpEntity.Chunked.fromData(contentType, bytes)
  }

  private def read(reader: RangeResource.Reader, offset: Long, length: Long): Source[ByteString, Any] =
    reader.read(offset, length).via(new ExactLength(length))

  private final class ExactLength(length: Long) extends GraphStage[FlowShape[ByteString, ByteString]] {
    private val in = Inlet[ByteString]("RangeResourceLength.in")
    private val out = Outlet[ByteString]("RangeResourceLength.out")
    override val shape: FlowShape[ByteString, ByteString] = FlowShape(in, out)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) with InHandler with OutHandler {
        private var remaining = length

        override def onPull(): Unit = pull(in)

        override def onPush(): Unit = {
          val data = grab(in)
          if (data.size.toLong > remaining)
            failStage(new IllegalStateException("Range reader supplied more than the requested byte count"))
          else if (data.isEmpty)
            pull(in)
          else {
            remaining -= data.size
            push(out, data)
          }
        }

        override def onUpstreamFinish(): Unit =
          if (remaining == 0L) completeStage()
          else failStage(new IllegalStateException("Range reader supplied fewer than the requested byte count"))

        setHandlers(in, out, this)
      }
  }

  private final class OpenedReader(val reader: RangeResource.Reader, log: LoggingAdapter) {
    private val released = new AtomicBoolean(false)

    def release(): Unit =
      if (released.compareAndSet(false, true))
        try reader.close()
        catch { case NonFatal(ex) => log.error(ex, "Closing a custom range reader failed") }
  }

  // Hold the reader for the actual bytes of the whole response, not for an outer stream of part metadata.
  private final class ReaderScope(
      resource: RangeResource,
      body: RangeResource.Reader => Source[ByteString, Any],
      log: LoggingAdapter) extends GraphStage[SourceShape[ByteString]] {
    private val out = Outlet[ByteString]("RangeResource.out")
    override val shape: SourceShape[ByteString] = SourceShape(out)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) with OutHandler {
        private var opening = false
        private var owner: OpenedReader = _
        private val bodyIn = new SubSinkInlet[ByteString]("RangeResourceBody")

        bodyIn.setHandler(new InHandler {
          override def onPush(): Unit = push(out, bodyIn.grab())
          override def onUpstreamFinish(): Unit = completeStage()
          override def onUpstreamFailure(ex: Throwable): Unit = failStage(ex)
        })

        private val opened = getAsyncCallback[Try[OpenedReader]] {
          case Success(value) =>
            owner = value
            body(value.reader).runWith(bodyIn.sink)(subFusingMaterializer)
            bodyIn.pull()
          case Failure(ex) => failStage(ex)
        }

        override def onPull(): Unit =
          if (owner ne null) bodyIn.pull()
          else if (!opening) {
            opening = true
            resource.openReader().onComplete {
              case Success(reader) if reader ne null =>
                val value = new OpenedReader(reader, log)
                // If the stage stops before handoff, release the late result without starting its byte stream.
                // The single-release guard also covers an exception during materialization after handoff.
                opened.invokeWithFeedback(Success(value)).failed.foreach(_ => value.release())(parasitic)
              case Success(_) =>
                opened.invoke(Failure(new IllegalStateException("Range reader factory returned a null reader")))
              case Failure(ex) =>
                opened.invoke(Failure(ex))
            }(parasitic)
          }

        override def onDownstreamFinish(cause: Throwable): Unit = {
          if ((owner ne null) && !bodyIn.isClosed) bodyIn.cancel(cause)
          completeStage()
        }

        override def postStop(): Unit =
          if (owner ne null) owner.release()

        setHandler(out, this)
      }
  }
}
