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

import java.util.concurrent.atomic.AtomicInteger

import scala.collection.immutable.VectorBuilder
import scala.concurrent.{ Future, Promise }

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.event.LoggingAdapter
import pekko.http.impl.engine.http2.FrameEvent.{ OutboundPushPromise, ParsedHeadersFrame, Setting }
import pekko.http.impl.engine.http2.Http2Protocol.SettingIdentifier
import pekko.http.impl.engine.rendering.DateHeaderRendering
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.model.headers.{ Host, `Raw-Request-URI` }
import pekko.http.scaladsl.settings.ClientConnectionSettings
import pekko.http.scaladsl.settings.ServerSettings
import pekko.stream.Attributes
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

  private[http2] def forOwnedPush(owned: Http2ServerPush.OwnedPushStream, response: HttpResponse): Http2SubStream = {
    val stream = assembleOnStream(response, owned.streamId, prepareInitialHeaderPairs(response))
    if (stream.correlationAttributes.isEmpty) stream else stream.withCorrelationAttributes(Map.empty)
  }

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

/**
 * INTERNAL API
 *
 * Server-local push producer. Its owner lives with one enabled connection. It only accepts a plan tied to a
 * positively admitted parent of that connection; a response's usual stream-id attribute cannot mint a reservation.
 */
@InternalApi
private[http2] object Http2ServerPush {
  final class AdmittedParent private[Http2ServerPush] (
      private[Http2ServerPush] val owner: Owner,
      val streamId: Int)

  private final class Origin(val parent: AdmittedParent, val scheme: String, val authority: Uri.Authority)

  /**
   * Private prototype observation. HandedOff acknowledges the control item entering the existing outgoing graph;
   * it does not acknowledge delivery to the client.
   */
  sealed trait Admission
  case object NotOffered extends Admission
  case object NotStarted extends Admission
  final class HandedOff(val streamId: Int) extends Admission

  private final class Plan(
      val origin: Origin, val target: Uri, val response: HttpResponse,
      val result: Option[Promise[Admission]])

  final class Enabled(val renderer: ResponseRendering) extends Attributes.Attribute

  final class OwnedPushStream private[Http2ServerPush] (val parent: AdmittedParent, val streamId: Int)

  final class Prepared private[Http2ServerPush] (
      val owned: OwnedPushStream,
      val promise: OutboundPushPromise,
      val response: Http2SubStream)

  private val originKey = AttributeKey[Origin]("pekko.http2.push-origin")
  private val planKey = AttributeKey[Plan]("pekko.http2.push-plan")

  private val admittedParentKey = AttributeKey[AdmittedParent]("pekko.http2.push-admitted-parent")

  def markParent(owner: Owner, substream: Http2SubStream): Http2SubStream =
    if (substream.initialHeaders.endStream &&
      substream.initialHeaders.keyValuePairs.exists { case (name, value) =>
        name == ":method" && (value eq HttpMethods.GET)
      })
      substream.withCorrelationAttributes(
        substream.correlationAttributes.updated(admittedParentKey, owner.admit(substream.streamId)))
    else substream

  def markParsed(substream: Http2SubStream, parsed: RequestParsing.ParseRequestResult)
      : RequestParsing.ParseRequestResult =
    parsed match {
      case ok @ RequestParsing.OkRequest(request) =>
        substream.correlationAttributes.get(admittedParentKey) match {
          case Some(parent: AdmittedParent) => RequestParsing.OkRequest(onAdmittedRequest(request, parent))
          case _                            => ok
        }
      case other => other
    }

  def onAdmittedRequest(request: HttpRequest, parent: AdmittedParent): HttpRequest = {
    val uri = request.uri
    if (request.attribute(Http2.streamId).contains(parent.streamId) &&
      request.method == HttpMethods.GET && request.entity.isKnownEmpty &&
      (uri.scheme == "http" || uri.scheme == "https") && !uri.authority.host.isEmpty && uri.authority.userinfo.isEmpty)
      request.addAttribute(originKey, new Origin(parent, uri.scheme, uri.authority))
    else request
  }

  /**
   * Attach an optional ready response whose entity has not been materialized. The promised method is always GET and
   * its scheme and authority come from the original request, not the application target.
   */
  def offer(request: HttpRequest, target: Uri, pushed: HttpResponse, primary: HttpResponse): HttpResponse =
    offer(request, target, pushed, primary, None)

  def offerWithOutcome(
      request: HttpRequest, target: Uri, pushed: HttpResponse, primary: HttpResponse)
      : (HttpResponse, Future[Admission]) = {
    val result = Promise[Admission]()
    val response = offer(request, target, pushed, primary, Some(result))
    (response, result.future)
  }

  private def offer(
      request: HttpRequest, target: Uri, pushed: HttpResponse, primary: HttpResponse,
      result: Option[Promise[Admission]]): HttpResponse =
    request.attribute(originKey) match {
      case Some(origin)
          if target.isRelative && target.authority.isEmpty && target.path.startsWithSlash &&
          target.fragment.isEmpty =>
        primary.addAttribute(planKey, new Plan(origin, target, pushed, result))
      case _ =>
        result.foreach(_.trySuccess(NotOffered))
        primary
    }

  def renderPrimary(renderer: ResponseRendering, response: HttpResponse): Http2SubStream = {
    val primary = renderer(response)
    response.attribute(planKey) match {
      case Some(plan) if response.status.intValue >= 200 && plan.origin.parent.streamId == primary.streamId =>
        primary.withCorrelationAttributes(primary.correlationAttributes.updated(planKey, plan))
      case Some(plan) =>
        plan.result.foreach(_.trySuccess(NotStarted))
        primary
      case _ => primary
    }
  }

  def hasPlan(primary: Http2SubStream): Boolean = primary.correlationAttributes.contains(planKey)

  def completeAdmission(primary: Http2SubStream, result: Admission): Unit =
    primary.correlationAttributes.get(planKey) match {
      case Some(plan: Plan) => plan.result.foreach(_.trySuccess(result))
      case _                =>
    }

  def withoutPlan(primary: Http2SubStream): Http2SubStream =
    if (primary.correlationAttributes.contains(planKey))
      primary.withCorrelationAttributes(primary.correlationAttributes - planKey)
    else primary

  final class Owner {
    private var receivedSettings = false
    private var peerAllowsPush = true // RFC 9113 section 6.5.2 default
    private var peerConcurrentLimit = Http2Protocol.InitialMaxConcurrentStreams.toLong
    private var lastAssignedId = 0
    private var reserved: OwnedPushStream = null
    private var active: OwnedPushStream = null

    def onPeerSettings(settings: Seq[Setting]): Unit = {
      settings.foreach {
        case Setting(SettingIdentifier.SETTINGS_ENABLE_PUSH, 0)            => peerAllowsPush = false
        case Setting(SettingIdentifier.SETTINGS_ENABLE_PUSH, 1)            => peerAllowsPush = true
        case Setting(SettingIdentifier.SETTINGS_MAX_CONCURRENT_STREAMS, n) =>
          peerConcurrentLimit = Integer.toUnsignedLong(n)
        case _ =>
      }
      receivedSettings = true
    }

    // Only the enabled server's ordinary admitted peer GET path calls this.
    def admit(streamId: Int): AdmittedParent = new AdmittedParent(this, streamId)

    // A retired local stream must remain distinguishable from a never-admitted peer stream.
    def owns(streamId: Int): Boolean = streamId > 0 && (streamId & 1) == 0 && streamId <= lastAssignedId
    def hasActive: Boolean = active != null
    def hasLiveOrReserved: Boolean = hasActive || reserved != null

    // The demux calls this after the control handoff's multiplexer transition has committed.
    def activate(owned: OwnedPushStream): Unit = {
      require(reserved eq owned)
      reserved = null
      active = owned
    }

    def retire(streamId: Int): Boolean =
      if (active != null && active.streamId == streamId) {
        active = null
        true
      } else if (reserved != null && reserved.streamId == streamId) {
        reserved = null
        true
      } else false

    // Called only while the associated peer request still awaits its ordinary response. The first internal slice can
    // offer one active local response at a time; this is not an application-facing limit.
    def prepare(primary: Http2SubStream, renderer: ResponseRendering): Option[Prepared] =
      if (!receivedSettings || !peerAllowsPush || peerConcurrentLimit == 0 || hasLiveOrReserved ||
        lastAssignedId > Int.MaxValue - 2) None
      else
        primary.correlationAttributes.get(planKey) match {
          case Some(plan: Plan)
              if (plan.origin.parent.owner eq this) && plan.origin.parent.streamId == primary.streamId =>
            lastAssignedId += 2
            val owned = new OwnedPushStream(plan.origin.parent, lastAssignedId)
            reserved = owned
            val fields = Vector(
              ":method" -> HttpMethods.GET.value,
              ":scheme" -> plan.origin.scheme,
              ":authority" -> plan.origin.authority.toString,
              ":path" -> plan.target.toHttpRequestTargetOriginForm.toString)
            Some(new Prepared(owned, OutboundPushPromise(owned, fields), renderer.forOwnedPush(owned, plan.response)))
          case _ => None
        }
  }
}

/** INTERNAL API */
@InternalApi
private[http2] class RequestRendering(
    settings: ClientConnectionSettings, val log: LoggingAdapter,
    connectionScheme: String = "", connectionAuthority: Uri.Authority = Uri.Authority.Empty)
    extends MessageRendering[HttpRequest] {

  private val streamId = new AtomicInteger(1)
  protected override def nextStreamId(r: HttpRequest): Int = streamId.getAndAdd(2)

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
    val headerPairs = prepareInitialHeaderPairs(r)
    assembleOnStream(r, nextStreamId(r), headerPairs)
  }

  protected final def prepareInitialHeaderPairs(r: R): VectorBuilder[(String, String)] = {
    val headerPairs = initialHeaderPairs(r)

    HttpMessageRendering.addContentHeaders(headerPairs, r.entity, contentLengthAllowed(r))
    HttpMessageRendering.renderHeaders(r.headers, headerPairs, peerIdHeader, log,
      isServer = r.isResponse,
      shouldRenderAutoHeaders = true, isTrailer = false, dateHeaderRendering)
    headerPairs
  }

  protected final def assembleOnStream(
      r: R,
      streamId: Int,
      headerPairs: VectorBuilder[(String, String)]): Http2SubStream = {
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
