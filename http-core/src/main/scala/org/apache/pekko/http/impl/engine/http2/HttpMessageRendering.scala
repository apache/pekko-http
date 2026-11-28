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

package org.apache.pekko.http.impl.engine.http2

import scala.collection.immutable.VectorBuilder

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.event.LoggingAdapter
import pekko.http.impl.engine.http2.FrameEvent.ParsedHeadersFrame
import pekko.http.impl.engine.rendering.DateHeaderRendering
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.model.headers.{ Host, `Raw-Request-URI` }
import pekko.http.scaladsl.model.http2.StreamIdExhaustedException
import pekko.http.scaladsl.settings.ClientConnectionSettings
import pekko.http.scaladsl.settings.ServerSettings
import pekko.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import pekko.stream.{ Attributes, FlowShape, Inlet, Outlet }
import pekko.util.OptionVal

/** INTERNAL API */
@InternalApi
private[http2] class ResponseRendering(settings: ServerSettings, val log: LoggingAdapter,
    val dateHeaderRendering: DateHeaderRendering) extends MessageRendering[HttpResponse] {

  private def failBecauseOfMissingAttribute: Nothing =
    // attribute is missing, shutting down because we will most likely otherwise miss a response and leak a substream
    // TODO: optionally a less drastic measure would be only resetting all the active substreams
    throw new RuntimeException(
      "Received response for HTTP/2 request without x-http2-stream-id attribute. Failing connection.")

  protected override def nextStreamId(response: HttpResponse): Int =
    response.attribute(Http2.streamId).getOrElse(failBecauseOfMissingAttribute)

  protected override def initialHeaderPairs(response: HttpResponse): VectorBuilder[(String, String)] = {
    val headerPairs = new VectorBuilder[(String, String)]()
    // From https://tools.ietf.org/html/rfc7540#section-8.1.2.4:
    //   HTTP/2 does not define a way to carry the version or reason phrase
    //   that is included in an HTTP/1.1 status line.
    headerPairs += ":status" -> response.status.intValue.toString
  }

  override lazy val peerIdHeader: Option[(String, String)] = settings.serverHeader.map(h => h.lowercaseName -> h.value)

  // Mirrors the status based part of the rules the HTTP/1.1 renderer applies through
  // HttpMethod.contentLengthAllowed: no content-length for 1xx, 204 or 304 (RFC 9110 sections 8.6 and 15.4.5, where a
  // 304 is supposed to carry the content-length a 200 would have had rather than a made up zero). GET is used here
  // only because it carries the rules that are common to all methods; the request method is not known on this path.
  protected override def contentLengthAllowed(response: HttpResponse): Boolean =
    HttpMethods.GET.contentLengthAllowed(response.status)

}

private[http2] object ClientStreamIds {
  // RFC 9113 §5.1.1: client-initiated stream IDs are positive, odd, and never reused on a connection.
  final val First = 1
  final val Last = Int.MaxValue

  def remainingFrom(firstStreamId: Int): Int = {
    require(firstStreamId > 0 && (firstStreamId & 1) == 1)
    (Last - firstStreamId) / 2 + 1
  }

  final class Allocator(firstStreamId: Int) {
    require(firstStreamId > 0 && (firstStreamId & 1) == 1)
    // Keep the exhausted state outside the Int range, without wrapping or resetting the connection's allocation.
    private var nextId: Long = firstStreamId.toLong

    def next(): Int = {
      if (nextId > Last) throw new IllegalStateException("No HTTP/2 client stream IDs remain on this connection")
      val allocated = nextId.toInt
      nextId += 2
      allocated
    }
  }
}

private[http2] final class ClientRequestRendering(
    settings: ClientConnectionSettings, log: LoggingAdapter,
    connectionScheme: String, connectionAuthority: Uri.Authority, firstStreamId: Int)
    extends GraphStage[FlowShape[HttpRequest, Http2SubStream]] {

  private val in = Inlet[HttpRequest]("Http2ClientRequestRendering.in")
  private val out = Outlet[Http2SubStream]("Http2ClientRequestRendering.out")
  override val shape: FlowShape[HttpRequest, Http2SubStream] = FlowShape(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler {
      // The renderer and its allocator belong to this physical connection, not to a restartable map operator.
      private val render = new RequestRendering(settings, log, connectionScheme, connectionAuthority, firstStreamId)
      setHandlers(in, out, this)

      override def onPull(): Unit = pull(in)

      override def onPush(): Unit = {
        val substream = render(grab(in))
        push(out, substream)
        if (substream.streamId == ClientStreamIds.Last) {
          // Report retirement to the request producer without failing the response stream. A failure on the
          // response side could discard already delivered responses still in downstream buffers.
          cancel(in, new StreamIdExhaustedException)
          complete(out)
        }
      }
    }
}

/** INTERNAL API */
@InternalApi
private[http2] class RequestRendering(
    settings: ClientConnectionSettings, val log: LoggingAdapter,
    connectionScheme: String, connectionAuthority: Uri.Authority, firstStreamId: Int)
    extends MessageRendering[HttpRequest] {

  def this(settings: ClientConnectionSettings, log: LoggingAdapter,
      connectionScheme: String = "", connectionAuthority: Uri.Authority = Uri.Authority.Empty) =
    this(settings, log, connectionScheme, connectionAuthority, ClientStreamIds.First)

  private val streamIds = new ClientStreamIds.Allocator(firstStreamId)
  protected override def nextStreamId(r: HttpRequest): Int = streamIds.next()

  protected override def initialHeaderPairs(request: HttpRequest): VectorBuilder[(String, String)] = {
    val headerPairs = new VectorBuilder[(String, String)]()
    headerPairs += ":method" -> request.method.value
    // RFC 9113 8.3.1: ':scheme' is mandatory and ':authority', when present, must not be empty. A relative URI, as
    // used with the connection-level client API, carries neither, so they are taken from the connection.
    headerPairs += ":scheme" -> (if (request.uri.scheme.nonEmpty) request.uri.scheme else connectionScheme)
    headerPairs += ":authority" -> authority(request)
    // a `Raw-Request-URI` header supplies the request target verbatim, as it does in the HTTP/1.1 renderer. `Uri`
    // percent-decodes path segments when parsing and re-encodes them with a keep-set that leaves sub-delims raw, so
    // it cannot round-trip an encoded pchar (`%2B` renders as `+`). Callers that must reproduce the target
    // byte-for-byte -- AWS SigV4 signs the encoded path, for instance -- pass it through this header.
    headerPairs += ":path" -> rawRequestTarget(request).getOrElse(
      request.uri.toHttpRequestTargetOriginForm.toString)
    headerPairs
  }

  // a `Host` header names the authority of a relative URI, as it does in HTTP/1.1, ahead of the connection's
  private def authority(request: HttpRequest): String =
    if (!request.uri.authority.isEmpty) request.uri.authority.toString
    else
      request.header[Host] match {
        case Some(host) if !host.isEmpty => Uri.Authority(host.host, host.port).toString
        case _                           => connectionAuthority.toString
      }

  // `Raw-Request-URI` is a SyntheticHeader, so it is already excluded from the rendered header block by the
  // `renderInRequests` filter and is only consumed here. As in HTTP/1.1, the value is taken as given; the header
  // rejects anything but visible ASCII on construction, so it cannot carry a character the HPACK guard would drop
  // the `:path` field for. Whether it is a well-formed origin-form target is still the caller's responsibility.
  private def rawRequestTarget(request: HttpRequest): Option[String] =
    request.headers.collectFirst { case `Raw-Request-URI`(rawUri) => rawUri }

  override lazy val peerIdHeader: Option[(String, String)] =
    settings.userAgentHeader.map(h => h.lowercaseName -> h.value)

  override protected def dateHeaderRendering: DateHeaderRendering = DateHeaderRendering.Unavailable
}

/** INTERNAL API */
@InternalApi
private[http2] sealed abstract class MessageRendering[R <: HttpMessage] extends (R => Http2SubStream) {

  protected def log: LoggingAdapter
  protected def nextStreamId(r: R): Int
  protected def initialHeaderPairs(r: R): VectorBuilder[(String, String)]
  protected def peerIdHeader: Option[(String, String)]
  protected def dateHeaderRendering: DateHeaderRendering

  /**
   * Whether a `content-length` may be rendered for this message. Always true for requests, where the length simply
   * describes the request body.
   */
  protected def contentLengthAllowed(r: R): Boolean = true

  def apply(r: R): Http2SubStream = {
    val headerPairs = initialHeaderPairs(r)

    HttpMessageRendering.addContentHeaders(headerPairs, r.entity, contentLengthAllowed(r))
    HttpMessageRendering.renderHeaders(r.headers, headerPairs, peerIdHeader, log,
      isServer = r.isResponse,
      shouldRenderAutoHeaders = true, isTrailer = false, dateHeaderRendering)

    val streamId = nextStreamId(r)
    val headersFrame = ParsedHeadersFrame(streamId, endStream = r.entity.isKnownEmpty, headerPairs.result(), None, None)
    val trailingHeadersFrame =
      r.attribute(AttributeKeys.trailer) match {
        case Some(trailer) if trailer.headers.nonEmpty =>
          val pairs = trailer.headers.flatMap { case (name, value) =>
            val header = pekko.http.scaladsl.model.headers.RawHeader(name, value)
            if (HttpMessageRendering.allowsHeader(header, r.isResponse, isTrailer = true, log))
              Some(header.lowercaseName -> value)
            else None
          }
          OptionVal.Some(ParsedHeadersFrame(streamId, endStream = true, pairs, None, None))
        case _ => OptionVal.None
      }

    Http2SubStream(r.entity, headersFrame, trailingHeadersFrame,
      r.attributes.filter(_._2.isInstanceOf[RequestResponseAssociation]))
  }
}

/** INTERNAL API */
@InternalApi
private[http2] object HttpMessageRendering {

  // One policy for initial fields and both streamed and attribute-supplied trailers.
  def allowsHeader(header: HttpHeader, isServer: Boolean, isTrailer: Boolean,
      log: LoggingAdapter): Boolean = {
    def suppress(): Boolean = {
      log.warning("Explicitly set HTTP header '{}' is ignored, header is not allowed in this HTTP/2 field section",
        header)
      false
    }
    header.lowercaseName match {
      case "connection" | "keep-alive" | "proxy-connection" | "transfer-encoding" | "upgrade" => suppress()
      case "te"                                                                               =>
        if (!isServer && !isTrailer && header.value.trim.equalsIgnoreCase("trailers")) true
        else suppress()
      case _ => true
    }
  }

  /**
   * Mutates `headerPairs` adding headers related to content (type and length).
   */
  def addContentHeaders(headerPairs: VectorBuilder[(String, String)], entity: HttpEntity,
      contentLengthAllowed: Boolean): Unit = {
    if (entity.contentType ne ContentTypes.NoContentType)
      headerPairs += "content-type" -> entity.contentType.toString
    if (contentLengthAllowed)
      entity.contentLengthOption.foreach(headerPairs += "content-length" -> _.toString)
  }

  def renderHeaders(
      headers: Seq[HttpHeader],
      log: LoggingAdapter,
      isServer: Boolean,
      shouldRenderAutoHeaders: Boolean,
      isTrailer: Boolean,
      dateHeaderRendering: DateHeaderRendering): Seq[(String, String)] = {
    val headerPairs = new VectorBuilder[(String, String)]()
    renderHeaders(headers, headerPairs, None, log, isServer, shouldRenderAutoHeaders, isTrailer, dateHeaderRendering)
    headerPairs.result()
  }

  /**
   * Mutates `headerPairs` adding any valid header from `headersSeq`.
   * @param peerIdHeader a header providing extra information (e.g. vendor and version) about the
   *                     peer. For example, a User-Agent on the client or a Server header on the server.
   * @param isTrailer whether the headers are rendered for a trailer section rather than an initial one, which
   *                  changes which fields are allowed (see `allowsHeader`)
   */
  def renderHeaders(
      headersSeq: Seq[HttpHeader],
      headerPairs: VectorBuilder[(String, String)],
      peerIdHeader: Option[(String, String)],
      log: LoggingAdapter,
      isServer: Boolean,
      shouldRenderAutoHeaders: Boolean,
      isTrailer: Boolean,
      dateHeaderRendering: DateHeaderRendering): Unit = {
    def suppressionWarning(h: HttpHeader, msg: String): Unit =
      log.warning("Explicitly set HTTP header '{}' is ignored, {}", h, msg)

    val it = headersSeq.iterator
    var peerIdSeen, dateSeen = false
    def addHeader(h: HttpHeader): Unit = headerPairs += h.lowercaseName -> h.value

    while (it.hasNext) {
      import pekko.http.scaladsl.model.headers._
      val header = it.next()
      if ((header.renderInResponses && isServer) || (header.renderInRequests && !isServer)) {
        if (allowsHeader(header, isServer, isTrailer, log)) {
          header match {
            case x if x.is("te") =>
              headerPairs += "te" -> "trailers"

            case x: Server if isServer =>
              addHeader(x)
              peerIdSeen = true

            case x: `User-Agent` if !isServer =>
              addHeader(x)
              peerIdSeen = true

            case x: Date if isServer =>
              addHeader(x)
              dateSeen = true

            case x: CustomHeader =>
              addHeader(x)

            case x: RawHeader
                if (x.is("content-type")) || (x.is("content-length")) ||
                (x.is("date")) || (x.is("server")) || (x.is("user-agent")) =>
              suppressionWarning(x, "illegal RawHeader")

            case x: `Content-Length` =>
              suppressionWarning(x,
                "explicit `Content-Length` header is not allowed. Use the appropriate HttpEntity subtype.")

            case x: `Content-Type` =>
              suppressionWarning(x,
                "explicit `Content-Type` header is not allowed. Set `HttpResponse.entity.contentType` instead.")

            case x =>
              addHeader(x)
          }
        }
      }
    }

    if (shouldRenderAutoHeaders && !dateSeen && isServer) {
      headerPairs += dateHeaderRendering.renderHeaderPair()
    }

    if (shouldRenderAutoHeaders && !peerIdSeen) {
      peerIdHeader match {
        case Some(peerIdTuple) => headerPairs += peerIdTuple
        case None              =>
      }
    }

  }

}
