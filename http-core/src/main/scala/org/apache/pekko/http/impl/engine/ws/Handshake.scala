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

package org.apache.pekko.http.impl.engine.ws

import java.util.Random

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.event.LoggingAdapter
import pekko.http.impl.engine.server.UpgradeToOtherProtocolResponseHeader
import pekko.http.impl.settings.WebSocketSettingsImpl
import pekko.http.impl.util._
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.model.headers._
import pekko.http.scaladsl.model.ws.Message
import pekko.http.scaladsl.settings.WebSocketSettings
import pekko.stream.{ FlowShape, Graph }
import pekko.util.OptionVal

/**
 * Server-side implementation of the WebSocket handshake
 *
 * INTERNAL API
 */
@InternalApi
private[http] object Handshake {
  val CurrentWebSocketVersion = 13

  object Server {

    private val CompressEveryMessage: Message => Boolean = _ => true
    private val CompressEveryFrame: FrameStart => Boolean = _ => true

    /**
     * Checks the WebSocket opening handshake headers and returns an upgrade handler if they are accepted.
     *
     * WebSocket handshake requirements: RFC 6455 section 4.2.1
     * (https://www.rfc-editor.org/rfc/rfc6455.html#section-4.2.1).
     * HTTP/1.1 request-line and target syntax: RFC 9112 section 3
     * (https://www.rfc-editor.org/rfc/rfc9112.html#section-3).
     * Base64 encoding of the key: RFC 4648 section 4
     * (https://www.rfc-editor.org/rfc/rfc4648.html#section-4).
     */
    def websocketUpgrade(headers: List[HttpHeader], hostHeaderPresent: Boolean, settings: WebSocketSettings,
        log: LoggingAdapter): OptionVal[UpgradeToWebSocketLowLevel] = {

      // notes on Headers that re REQUIRE to be present here:
      // - Host header is validated in general HTTP logic
      // - Origin header is optional and, if required, should be validated
      //   on higher levels (routing, application logic)
      //
      // WebSocket extension negotiation is optional. Currently only permessage-deflate is supported.
      //
      // these are not needed directly, we verify their presence and correctness only:
      // - Upgrade
      // - Connection
      // - `Sec-WebSocket-Version`
      def hasAllRequiredWebsocketUpgradeHeaders: Boolean = {
        // single-pass through the headers list while collecting all needed requirements
        // this way we avoid scanning the requirements list 3 times (as we would with collect/find)
        val it = headers.iterator
        var requirementsMet = 0
        val targetRequirements = 3
        while (it.hasNext && (requirementsMet != targetRequirements)) it.next() match {
          case u: Upgrade                 => if (u.hasWebSocket) requirementsMet += 1
          case c: Connection              => if (c.hasUpgrade) requirementsMet += 1
          case v: `Sec-WebSocket-Version` => if (v.hasVersion(CurrentWebSocketVersion)) requirementsMet += 1
          case _                          => // continue...
        }
        requirementsMet == targetRequirements
      }

      if (hasAllRequiredWebsocketUpgradeHeaders) {
        val key = HttpHeader.fastFind(classOf[`Sec-WebSocket-Key`], headers)
        if (key.isDefined && key.get.isValid) {
          // Request offers can span multiple field lines (RFC 6455 section 11.3.4).
          val clientSupportedSubprotocols = headers.flatMap {
            case protocol: `Sec-WebSocket-Protocol` => protocol.protocols
            case _                                  => Nil
          }
          val clientRequestedExtensions = headers.flatMap {
            case extensions: `Sec-WebSocket-Extensions` => extensions.extensions
            case _                                      => Nil
          }
          val perMessageDeflate =
            settings match {
              case impl: WebSocketSettingsImpl =>
                PerMessageDeflate.negotiate(clientRequestedExtensions, impl.compression)
              case _ => None
            }

          val header = new UpgradeToWebSocketLowLevel {
            def requestedProtocols: Seq[String] = clientSupportedSubprotocols

            def handle(
                handler: Either[Graph[FlowShape[FrameEvent, FrameEvent], Any], Graph[FlowShape[Message, Message], Any]],
                subprotocol: Option[String],
                compressionEnabled: Boolean,
                shouldCompressMessage: Message => Boolean = CompressEveryMessage,
                shouldCompressFrame: FrameStart => Boolean = CompressEveryFrame): HttpResponse = {
              require(
                subprotocol.forall(chosen => clientSupportedSubprotocols.contains(chosen)),
                s"Tried to choose invalid subprotocol '$subprotocol' which wasn't offered by the client: [${requestedProtocols.mkString(", ")}]")
              val acceptedPerMessageDeflate = if (compressionEnabled) perMessageDeflate else None
              buildResponse(
                key.get,
                handler,
                subprotocol,
                acceptedPerMessageDeflate,
                settings,
                log,
                shouldCompressMessage,
                shouldCompressFrame)
            }

            def handleFrames(
                handlerFlow: Graph[FlowShape[FrameEvent, FrameEvent], Any], subprotocol: Option[String]): HttpResponse =
              handle(Left(handlerFlow), subprotocol, compressionEnabled = true)

            override private[http] def handleFrames(
                handlerFlow: Graph[FlowShape[FrameEvent, FrameEvent], Any],
                subprotocol: Option[String],
                compressionEnabled: Boolean): HttpResponse =
              handle(Left(handlerFlow), subprotocol, compressionEnabled)

            override private[http] def handleFrames(
                handlerFlow: Graph[FlowShape[FrameEvent, FrameEvent], Any],
                subprotocol: Option[String],
                compressionEnabled: Boolean,
                shouldCompress: FrameStart => Boolean): HttpResponse =
              handle(Left(handlerFlow), subprotocol, compressionEnabled, shouldCompressFrame = shouldCompress)

            override def handleMessages(handlerFlow: Graph[FlowShape[Message, Message], Any],
                subprotocol: Option[String] = None): HttpResponse =
              handle(Right(handlerFlow), subprotocol, compressionEnabled = true)

            override def handleMessages(
                handlerFlow: Graph[FlowShape[Message, Message], Any],
                subprotocol: Option[String],
                compressionEnabled: Boolean): HttpResponse =
              handle(Right(handlerFlow), subprotocol, compressionEnabled)

            override def handleMessages(
                handlerFlow: Graph[FlowShape[Message, Message], Any],
                subprotocol: Option[String],
                shouldCompress: Message => Boolean): HttpResponse =
              handle(Right(handlerFlow), subprotocol, compressionEnabled = true, shouldCompressMessage = shouldCompress)
          }
          OptionVal.Some(header)
        } else OptionVal.None
      } else OptionVal.None
    }

    // WebSocket response headers: RFC 6455 section 4.2.2 (https://www.rfc-editor.org/rfc/rfc6455.html#section-4.2.2).
    // HTTP Upgrade: RFC 9110 section 7.8 (https://www.rfc-editor.org/rfc/rfc9110.html#section-7.8).
    // HTTP/1.1 status line: RFC 9112 section 4 (https://www.rfc-editor.org/rfc/rfc9112.html#section-4).
    def buildResponse(key: `Sec-WebSocket-Key`,
        handler: Either[Graph[FlowShape[FrameEvent, FrameEvent], Any], Graph[FlowShape[Message, Message], Any]],
        subprotocol: Option[String],
        perMessageDeflate: Option[PerMessageDeflate.Negotiated],
        settings: WebSocketSettings,
        log: LoggingAdapter): HttpResponse =
      buildResponse(
        key,
        handler,
        subprotocol,
        perMessageDeflate,
        settings,
        log,
        CompressEveryMessage,
        CompressEveryFrame)

    private def buildResponse(
        key: `Sec-WebSocket-Key`,
        handler: Either[Graph[FlowShape[FrameEvent, FrameEvent], Any], Graph[FlowShape[Message, Message], Any]],
        subprotocol: Option[String],
        perMessageDeflate: Option[PerMessageDeflate.Negotiated],
        settings: WebSocketSettings,
        log: LoggingAdapter,
        shouldCompressMessage: Message => Boolean,
        shouldCompressFrame: FrameStart => Boolean): HttpResponse = {
      val frameHandler = handler match {
        case Left(frameHandler) =>
          perMessageDeflate
            .map(_.frameEventBidiFlow(settings.randomFactory, shouldCompressFrame).join(frameHandler))
            .getOrElse(frameHandler)
        case Right(messageHandler) =>
          WebSocket.stack(true, settings, perMessageDeflate, log, shouldCompressMessage)
            .join(messageHandler)
      }

      val extensionHeaders = perMessageDeflate.map(p => `Sec-WebSocket-Extensions`(Seq(p.responseExtension))).toList
      val responseHeaders =
        subprotocol.map(p => `Sec-WebSocket-Protocol`(Seq(p))).toList :::
        List(UpgradeHeader, ConnectionUpgradeHeader, `Sec-WebSocket-Accept`.forKey(key)) :::
        extensionHeaders :::
        List(UpgradeToOtherProtocolResponseHeader(WebSocket.framing.join(frameHandler)))

      HttpResponse(StatusCodes.SwitchingProtocols, responseHeaders)
    }
  }

  object Client {
    case class NegotiatedWebSocketSettings(subprotocol: Option[String])

    /**
     * Builds a WebSocket handshake request.
     */
    def buildRequest(uri: Uri, extraHeaders: Seq[HttpHeader], subprotocols: Seq[String], random: Random)
        : (HttpRequest, `Sec-WebSocket-Key`) = {
      val keyBytes = new Array[Byte](16)
      random.nextBytes(keyBytes)
      val key = `Sec-WebSocket-Key`(keyBytes)
      val protocol =
        if (subprotocols.nonEmpty) `Sec-WebSocket-Protocol`(subprotocols) :: Nil
        else Nil

      // These singleton fields are generated for the handshake (RFC 6455 sections 11.3.1 and 11.3.5).
      val additionalHeaders = extraHeaders.filterNot { header =>
        val name = header.lowercaseName
        name == `Sec-WebSocket-Key`.lowercaseName || name == `Sec-WebSocket-Version`.lowercaseName
      }

      val headers = Seq(
        UpgradeHeader,
        ConnectionUpgradeHeader,
        key,
        SecWebSocketVersionHeader) ++ protocol ++ additionalHeaders

      // The WebSocket resource name includes '?' only for a nonempty query (RFC 6455 section 3).
      val relativeUri = uri.toRelative
      val requestUri =
        if (relativeUri.rawQueryString.contains("")) relativeUri.copy(rawQueryString = None) else relativeUri
      (HttpRequest(HttpMethods.GET, requestUri, headers), key)
    }

    /** Lower-cased names of the extensions listed in `Sec-WebSocket-Extensions` headers; empty values list none. */
    def extensionNames(headers: Seq[HttpHeader]): Set[String] =
      headers.iterator
        .filter(_.is("sec-websocket-extensions"))
        .flatMap(_.value.split(','))
        .map(_.takeWhile(_ != ';').trim.toRootLowerCase)
        .filter(_.nonEmpty)
        .toSet

    /**
     * Checks the server's HTTP/1.1 WebSocket handshake response and returns negotiated settings or an error message.
     * See RFC 6455 section 4.1 (https://www.rfc-editor.org/rfc/rfc6455.html#section-4.1).
     *
     * @param offeredExtensions lower-cased names of the extensions the request offered (see `extensionNames`); the
     *                          response may only select these
     */
    def validateResponse(response: HttpResponse, subprotocols: Seq[String], key: `Sec-WebSocket-Key`,
        offeredExtensions: Set[String])
        : Either[String, NegotiatedWebSocketSettings] = {

      trait Expectation extends (HttpResponse => Option[String]) { outer =>
        def &&(other: HttpResponse => Option[String]): Expectation =
          new Expectation {
            def apply(v1: HttpResponse): Option[String] =
              outer(v1).orElse(other(v1))
          }
      }

      def check[T](value: HttpResponse => T)(condition: T => Boolean, msg: T => String): Expectation =
        new Expectation {
          def apply(resp: HttpResponse): Option[String] = {
            val v = value(resp)
            if (condition(v)) None
            else Some(msg(v))
          }
        }

      def compare(candidate: HttpHeader, caseInsensitive: Boolean): Option[HttpHeader] => Boolean = {
        case Some(`candidate`) if !caseInsensitive                                                              => true
        case Some(header) if caseInsensitive && candidate.value.toRootLowerCase == header.value.toRootLowerCase => true
        case _                                                                                                  => false
      }

      def headerExists(
          candidate: HttpHeader, showExactOther: Boolean = true, caseInsensitive: Boolean = false): Expectation =
        check(_.headers.find(_.name == candidate.name))(compare(candidate, caseInsensitive),
          {
            case Some(other) if showExactOther =>
              s"response that was missing required `$candidate` header. Found `$other` with the wrong value."
            case Some(_) => s"response with invalid `${candidate.name}` header."
            case None    => s"response that was missing required `${candidate.name}` header."
          })

      val expectations: Expectation =
        check(_.status)(_ == StatusCodes.SwitchingProtocols, "unexpected status code: " + _) &&
        headerExists(UpgradeHeader, caseInsensitive = true) &&
        check(_.header[Connection])(_.exists(_.hasUpgrade),
          {
            case Some(other) =>
              s"response that was missing required `$ConnectionUpgradeHeader` header. Found `$other` with the wrong value."
            case None => "response that was missing required `Connection` header."
          }) &&
        headerExists(`Sec-WebSocket-Accept`.forKey(key), showExactOther = false) &&
        // RFC 6455 section 4.1 item 5: fail if the server selects an extension the client didn't offer.
        // The client implements no extensions itself; one the caller offered through `extraHeaders` is accepted.
        check(r => extensionNames(r.headers).forall(offeredExtensions.contains))(identity,
          _ => "response with an unsupported WebSocket extension.")

      expectations(response) match {
        case None =>
          val subs = response.header[`Sec-WebSocket-Protocol`].flatMap(_.protocols.headOption)

          if (subprotocols.isEmpty && subs.isEmpty) Right(NegotiatedWebSocketSettings(None)) // no specific one selected
          else if (subs.nonEmpty && subprotocols.contains(subs.get)) Right(NegotiatedWebSocketSettings(Some(subs.get)))
          else Left(
            s"response that indicated that the given subprotocol was not supported. (client supported: ${subprotocols.mkString(
                ", ")}, server supported: $subs)")
        case Some(problem) => Left(problem)
      }
    }
  }

  val UpgradeHeader = Upgrade(List(UpgradeProtocol("websocket")))
  val ConnectionUpgradeHeader = Connection(List("upgrade"))
  val SecWebSocketVersionHeader = `Sec-WebSocket-Version`(Seq(CurrentWebSocketVersion))
}
