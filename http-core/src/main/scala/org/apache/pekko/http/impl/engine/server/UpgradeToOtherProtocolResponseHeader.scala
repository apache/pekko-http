/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.impl.engine.server

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.http.impl.engine.ws.FrameOutHandler
import pekko.http.impl.model.parser.CharacterClasses
import pekko.http.scaladsl.model.{
  AttributeKey,
  HttpEntity,
  HttpHeader,
  HttpProtocols,
  HttpRequest,
  HttpResponse,
  StatusCodes
}
import pekko.http.scaladsl.model.headers.{ Connection, CustomHeader, Upgrade, UpgradeProtocol }
import pekko.stream.Attributes
import pekko.stream.scaladsl.{ Flow, Source }
import pekko.util.ByteString

/**
 * Internal API
 */
@InternalApi
private[http] final case class UpgradeToOtherProtocolResponseHeader(handler: Flow[ByteString, ByteString, Any])
    extends InternalCustomHeader("UpgradeToOtherProtocolResponseHeader") {
  def handlerFor(response: HttpResponse): Flow[ByteString, ByteString, Any] =
    response.attribute(FrameOutHandler.completionCloseKey).fold(handler)(close =>
      handler.addAttributes(Attributes(close)))
}

/**
 * Internal API
 */
@InternalApi
private[http] abstract class InternalCustomHeader(val name: String) extends CustomHeader {
  final def renderInRequests = false
  final def renderInResponses = false
  def value: String = ""
}

/** INTERNAL API: an HTTP/1.1 request-boundary handshake for an explicitly registered custom protocol. */
@InternalApi
private[http] object Http1Upgrade {
  final class Registration(val protocol: UpgradeProtocol) extends Attributes.Attribute {
    private def isToken(value: String): Boolean =
      value.nonEmpty && value.forall(CharacterClasses.tchar(_))

    private def isBuiltIn(name: String): Boolean =
      name.equalsIgnoreCase("websocket") || name.equalsIgnoreCase("h2c") ||
      name.equalsIgnoreCase("h2") || name.equalsIgnoreCase("http")

    require(isToken(protocol.name) && protocol.version.forall(isToken), "Upgrade protocol must consist of tokens")
    require(!isBuiltIn(protocol.name), "Built-in HTTP and WebSocket protocols use their own upgrade path")

    def matches(headers: List[HttpHeader]): Boolean =
      headers.exists {
        case connection: Connection => connection.hasUpgrade
        case _                      => false
      } && !headers.exists {
        case upgrade: Upgrade => upgrade.protocols.exists(offered => isBuiltIn(offered.name))
        case _                => false
      } && headers.exists {
        case upgrade: Upgrade =>
          upgrade.protocols.exists(offered =>
            offered.name.equalsIgnoreCase(protocol.name) && offered.version == protocol.version)
        case _ => false
      }
  }

  sealed trait ParserDecision
  final case class ContinueHttp(unread: ByteString) extends ParserDecision
  case object SwitchCommitted extends ParserDecision

  // Created by the parser, consumed only by the controller. The app's capability can refer to this identity but
  // cannot read the remainder or decide where it goes. No subsequent transport read is needed to claim these bytes.
  final class Boundary(protocol: UpgradeProtocol, unread: ByteString, resolve: ParserDecision => Unit) {
    private var pending: Option[ByteString] = Some(unread)

    private def takeUnread(): Option[ByteString] = {
      val result = pending
      pending = None
      result
    }

    def decline(): Unit =
      takeUnread().foreach(bytes => resolve(ContinueHttp(bytes)))

    private[Http1Upgrade] def switchResponse(
        applicationHeaders: Seq[HttpHeader],
        createHandler: => Flow[ByteString, ByteString, Any]): HttpResponse =
      takeUnread() match {
        case None        => HttpResponse(StatusCodes.ServiceUnavailable)
        case Some(bytes) =>
          val handler = createHandler
          val withBoundary = Flow.fromMaterializer { (_, _) =>
            // The switch stage only materializes this flow after it has emitted the 101 and closed the old HTTP side.
            resolve(SwitchCommitted)
            if (bytes.isEmpty) handler
            else Flow[ByteString].prepend(Source.single(bytes)).via(handler)
          }
          HttpResponse(StatusCodes.SwitchingProtocols,
            headers = Upgrade(Seq(protocol)) +: Connection("Upgrade") +:
              UpgradeToOtherProtocolResponseHeader(withBoundary) +: applicationHeaders)
      }
  }

  val boundaryKey: AttributeKey[Boundary] = AttributeKey("pekko.http.internal.h1-upgrade-boundary")
  private val offerKey: AttributeKey[Offer] = AttributeKey("pekko.http.internal.h1-upgrade-offer")
  private val acceptanceKey: AttributeKey[Acceptance] = AttributeKey("pekko.http.internal.h1-upgrade-acceptance")

  private final class Offer(val boundary: Boundary, val request: HttpRequest)
  private final class Acceptance(val offer: Offer, createHandler: HttpRequest => Flow[ByteString, ByteString, Any]) {
    def handler: Flow[ByteString, ByteString, Any] = createHandler(offer.request)
  }

  def attachOffer(request: HttpRequest): HttpRequest =
    request.attribute(boundaryKey) match {
      case Some(boundary) =>
        val original = request.removeAttribute(boundaryKey)
        original.addAttribute(offerKey, new Offer(boundary, original))
      case None => request
    }

  // The controller recognizes this typed response intent only for the matching completed request. 101 remains an
  // explicit selection even after application headers are added; changing the status is an ordinary HTTP decline.
  def acceptanceFor(request: HttpRequest): Option[(HttpRequest => Flow[ByteString, ByteString, Any]) => HttpResponse] =
    request.attribute(offerKey).map(offer =>
      handler =>
        HttpResponse(StatusCodes.SwitchingProtocols).addAttribute(acceptanceKey, new Acceptance(offer, handler)))

  def accept(request: HttpRequest)(handler: HttpRequest => Flow[ByteString, ByteString, Any]): Option[HttpResponse] =
    acceptanceFor(request).map(_(handler))

  private val negotiationHeaders =
    Set("connection", "upgrade", "content-length", "content-type", "transfer-encoding", "trailer",
      "upgradetootherprotocolresponseheader")

  def decide(boundary: Option[Boundary], response: HttpResponse, canSwitch: Boolean): HttpResponse = {
    val acceptance = response.attribute(acceptanceKey)
    val isSwitching = response.status == StatusCodes.SwitchingProtocols

    if (canSwitch && isSwitching && response.protocol == HttpProtocols.`HTTP/1.1` &&
      response.entity == HttpEntity.Empty && !response.headers.exists(h => negotiationHeaders(h.lowercaseName))) {
      acceptance match {
        case Some(accepted) if boundary.contains(accepted.offer.boundary) =>
          return accepted.offer.boundary.switchResponse(response.headers, accepted.handler)
        case _ =>
      }
    }

    boundary.foreach(_.decline())
    if (isSwitching && (boundary.isDefined || acceptance.isDefined))
      HttpResponse(StatusCodes.ServiceUnavailable)
    else if (acceptance.isDefined) response.removeAttribute(acceptanceKey)
    else response
  }
}
