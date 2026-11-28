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

package org.apache.pekko.http.scaladsl.server
package directives

import java.io.{ EOFException, IOException }
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.{ Path, StandardOpenOption }

import org.apache.pekko
import pekko.event.LoggingAdapter
import pekko.http.RangeResponsePolicy
import pekko.http.impl.engine.rendering.BodyPartRenderer
import pekko.http.impl.server.{ RangeResourceSource, RangeSelection }
import pekko.http.impl.util._
import pekko.http.scaladsl.marshalling.ToResponseMarshallable
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.model.StatusCodes._
import pekko.http.scaladsl.model.headers._
import pekko.http.scaladsl.server.RouteResult.Complete
import pekko.stream.ActorAttributes
import pekko.stream.OverflowStrategy
import pekko.stream.SourceShape
import pekko.stream.Supervision
import pekko.stream.scaladsl._
import pekko.util.ByteString

/**
 * @groupname range Range directives
 * @groupprio range 180
 */
trait RangeDirectives {
  import pekko.http.scaladsl.server.directives.BasicDirectives._
  import pekko.http.scaladsl.server.directives.RouteDirectives._

  /**
   * Serves a known-length representation through position-based reads. Pekko HTTP selects and renders the
   * ranges; the reader supplies only their bytes. Uses the same limits and coalescing as [[withRangeSupport]].
   * Setting `preserve-range-request-order` requests multipart order rather than increasing byte-offset order.
   *
   * The resource is opened only on body demand. To apply Range only to GET, keep
   * `pekko.http.server.transparent-head-requests` off (the default); otherwise the server can pass HEAD to this
   * route as GET. A zero-length representation needs no reader.
   *
   * Optional validators use the standard conditional directive. With If-Range, options other than comparable
   * strong tags may result in sending the full representation.
   *
   * @group range
   */
  def getFromRangeResource(resource: RangeResource): Route = (MethodDirectives.get | MethodDirectives.head) {
    CacheConditionDirectives.conditional(resource.entityTag, resource.lastModified) {
      ctx =>
        val range = ctx.request.header[`If-Range`] match {
          case Some(`If-Range`(Left(_))) if resource.entityTag.exists(!_.weak) => ctx.request.header[Range]
          case Some(_)                                                         => None
          case None                                                            => ctx.request.header[Range]
        }
        val policy = RangeResponsePolicy.fromRoutingSettings(ctx.settings)
        RangeResourceSource.plan(resource, ctx.request.method, range, policy, ctx.log) match {
          case Some(RangeResourceSource.Serve(status, headers, entity)) => ctx.complete((status, headers, entity))
          case Some(RangeResourceSource.TooMany(limit, _))              => ctx.reject(TooManyRangesRejection(limit))
          case Some(RangeResourceSource.Unsatisfiable(ranges, length))  =>
            ctx.reject(UnsatisfiableRangeRejection(ranges, length))
          case None => ctx.reject()
        }
    }
  }

  /**
   * Answers GET requests with an `Accept-Ranges: bytes` header and converts HttpResponses coming back from its inner
   * route into partial responses if the initial request contained a valid `Range` request header. The requested
   * byte-ranges may be coalesced.
   * This directive is transparent to non-GET requests
   * Rejects requests with unsatisfiable ranges `UnsatisfiableRangeRejection`.
   * Rejects requests with too many expected ranges.
   *
   * Note: if you want to combine this directive with `conditional(...)` you need to put
   * it on the *inside* of the `conditional(...)` directive, i.e. `conditional(...)` must be
   * on a higher level in your route structure in order to function correctly.
   *
   * @see [[https://www.rfc-editor.org/rfc/rfc9110.html#section-15.3.7.2]]
   *
   * @group range
   */
  def withRangeSupport: Directive0 =
    withRangeSupportFor(None)

  private def withRangeSupportFor(knownFile: Option[(HttpEntity.Default, Path)]): Directive0 =
    extractRequestContext.flatMap { ctx =>
      import RangeSelection.{ coalesceRanges, indexRange, satisfiable }
      val settings = ctx.settings
      implicit val log = ctx.log
      import settings.{ rangeCountLimit, rangeCoalescingThreshold }

      def multipartRanges(
          ranges: Seq[ByteRange],
          entity: UniversalEntity,
          headers: Seq[HttpHeader]): ToResponseMarshallable = {
        val length = entity.contentLength
        val coalescedRanges = coalesceRanges(ranges.map(indexRange(length)), rangeCoalescingThreshold)
        if (settings.preserveRangeRequestOrder && coalescedRanges.lengthCompare(1) > 0)
          entity match {
            case strict: HttpEntity.Strict =>
              val parts = Source(coalescedRanges).map { range =>
                Multipart.ByteRanges.BodyPart(
                  range.contentRange(length),
                  HttpEntity(strict.contentType, strict.data.slice(range.start.toInt, range.end.toInt)))
              }
              return ToResponseMarshallable((PartialContent, headers, Multipart.ByteRanges(parts)))
            case _ =>
              knownFile match {
                case Some((selectedEntity, path)) if selectedEntity eq entity =>
                  val multipart = RangeDirectives.fileMultipartEntity(
                    path, selectedEntity, coalescedRanges.map(_.contentRange(length)), log)
                  return ToResponseMarshallable((PartialContent, headers, multipart))
                case _ =>
              }
          }

        // It's only possible to run once over the input entity data stream because it's not known if the
        // source is reusable.
        // Therefore, ranges need to be sorted to prevent that some selected ranges already start to accumulate data
        // but cannot be sent out because another range is blocking the queue.
        val sortedRanges = coalescedRanges.sortBy(_.start)
        val source = sortedRanges.size match {
          case 0 => Source.empty
          case 1 =>
            val range = sortedRanges.head
            val flow = StreamUtils.sliceBytesTransformer(range.start, range.length)
            val bytes = entity.dataBytes.via(flow)
            val part = Multipart.ByteRanges.BodyPart(range.contentRange(length),
              HttpEntity(entity.contentType, range.length, bytes))
            Source.single(part)
          case n =>
            Source.fromGraph(GraphDSL.create() { implicit b =>
              import GraphDSL.Implicits._
              val bcast = b.add(Broadcast[ByteString](n))
              val merge = b.add(Concat[Multipart.ByteRanges.BodyPart](n))
              for (range <- sortedRanges) {
                val flow = StreamUtils.sliceBytesTransformer(range.start, range.length)
                bcast ~> flow.buffer(16, OverflowStrategy.backpressure).prefixAndTail(0).map {
                  case (_, bytes) =>
                    Multipart.ByteRanges.BodyPart(range.contentRange(length),
                      HttpEntity(entity.contentType, range.length, bytes))
                } ~> merge
              }
              entity.dataBytes ~> bcast
              SourceShape(merge.out)
            })
        }
        ToResponseMarshallable((PartialContent, headers, Multipart.ByteRanges(source)))
      }

      def rangeResponse(range: ByteRange, entity: UniversalEntity, length: Long, headers: Seq[HttpHeader]) = {
        val aiRange = indexRange(length)(range)
        val selected =
          entity.transformDataBytes(aiRange.length, StreamUtils.sliceBytesTransformer(aiRange.start, aiRange.length))
        HttpResponse(PartialContent, `Content-Range`(aiRange.contentRange(length)) +: headers, selected)
      }

      def universal(entity: HttpEntity): Option[UniversalEntity] = entity match {
        case u: UniversalEntity => Some(u)
        case _                  => None
      }

      def applyRanges(ranges: Seq[ByteRange]): Directive0 =
        extractRequestContext.flatMap { ctx =>
          mapRouteResultWithPF {
            case Complete(HttpResponse(OK, headers, entity, protocol)) =>
              universal(entity) match {
                case Some(entity) =>
                  val length = entity.contentLength
                  ranges.filter(satisfiable(length)) match {
                    case Nil                   => ctx.reject(UnsatisfiableRangeRejection(ranges, length))
                    case Seq(satisfiableRange) => ctx.complete(rangeResponse(satisfiableRange, entity, length, headers))
                    case satisfiableRanges     =>
                      ctx.complete(multipartRanges(satisfiableRanges, entity, headers))
                  }
                case None =>
                  // Ranges not supported for Chunked or CloseDelimited responses
                  ctx.reject(UnsatisfiableRangeRejection(ranges, -1)) // FIXME: provide better error
              }
          }
        }

      def rangeHeaderOfGetRequests(ctx: RequestContext): Option[Range] =
        if (ctx.request.method == HttpMethods.GET) ctx.request.header[Range] else None

      extract(rangeHeaderOfGetRequests).flatMap {
        case Some(Range(RangeUnits.Bytes, ranges)) =>
          if (ranges.size <= rangeCountLimit) applyRanges(ranges) & RangeDirectives.respondWithAcceptByteRangesHeader
          else reject(TooManyRangesRejection(rangeCountLimit))
        case _ => MethodDirectives.get & RangeDirectives.respondWithAcceptByteRangesHeader | pass
      }
    }
}

object RangeDirectives extends RangeDirectives {
  private val respondWithAcceptByteRangesHeader: Directive0 =
    RespondWithDirectives.respondWithHeader(`Accept-Ranges`(RangeUnits.Bytes))

  // The file directive supplies the exact entity it constructed from this path. A transformed response
  // falls back to the ordinary one-pass stream.
  private[directives] def withFileRangeSupport(entity: HttpEntity.Default, path: Path): Directive0 =
    (this: RangeDirectives).withRangeSupportFor(Some((entity, path)))

  private def fileMultipartEntity(
      path: Path,
      representation: HttpEntity.Default,
      ranges: Seq[ContentRange.Default],
      log: LoggingAdapter): MessageEntity = {
    val boundary = BodyPartRenderer.randomBoundary()
    val contentType = MediaTypes.`multipart/byteranges`.withBoundary(boundary).toContentType
    val bytes = Source.lazySource(() =>
      Source
        .unfoldResource[ByteString, FileRangeReader](
          () => new FileRangeReader(path, representation, ranges.iterator, boundary, log),
          _.read(),
          _.close())
        .addAttributes(ActorAttributes.supervisionStrategy(Supervision.stoppingDecider)))
    // The entire rendered entity owns the one resource, not a stream of descriptors for separate part sources.
    HttpEntity.Chunked.fromData(contentType, bytes)
  }

  private final class FileRangeReader(
      path: Path,
      representation: HttpEntity.Default,
      ranges: Iterator[ContentRange.Default],
      boundary: String,
      log: LoggingAdapter) {
    private val buffer = ByteBuffer.allocate(8192)
    private var position = 0L
    private var remaining = 0L
    private var first = true
    private var closingBoundaryEmitted = false
    private val channel = FileChannel.open(path, StandardOpenOption.READ)

    def read(): Option[ByteString] =
      if (remaining > 0L) {
        buffer.clear()
        buffer.limit(math.min(buffer.capacity().toLong, remaining).toInt)
        val count = channel.read(buffer, position)
        if (count < 0) throw new EOFException("File ended before the selected byte range was read")
        if (count == 0) throw new IOException("No progress reading the selected byte range")
        position += count
        remaining -= count
        buffer.flip()
        Some(ByteString.fromByteBuffer(buffer))
      } else if (ranges.hasNext) {
        val range = ranges.next()
        position = range.first
        remaining = range.last - range.first + 1L
        val header = BodyPartRenderer.byteRangePartHeader(boundary, first, representation, range, log)
        first = false
        Some(header)
      } else if (!closingBoundaryEmitted) {
        closingBoundaryEmitted = true
        Some(BodyPartRenderer.closingBoundary(boundary))
      } else None

    def close(): Unit = channel.close()
  }
}
