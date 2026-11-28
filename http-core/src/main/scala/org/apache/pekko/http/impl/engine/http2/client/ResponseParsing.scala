/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.impl.engine.http2
package client

import javax.net.ssl.SSLSession

import scala.annotation.tailrec
import scala.collection.immutable.VectorBuilder

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.http.impl.engine.http2.Http2Compliance.Http2ProtocolException
import pekko.http.impl.engine.http2.Http2ResponseStatus
import pekko.http.impl.engine.http2.RequestParsing._
import pekko.http.impl.engine.parsing.HttpHeaderParser
import pekko.http.impl.engine.server.HttpAttributes
import pekko.http.impl.util._
import pekko.http.scaladsl.model
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.model.headers.{ `Content-Type`, `Tls-Session-Info` }
import pekko.http.scaladsl.settings.ParserSettings
import pekko.stream.Attributes
import pekko.util.OptionVal

@InternalApi
private[http2] object ResponseParsing {
  def parseResponse(httpHeaderParser: HttpHeaderParser, settings: ParserSettings, attributes: Attributes)
      : Http2SubStream => HttpResponse = { subStream =>
    val tlsSessionInfoHeader: Option[`Tls-Session-Info`] =
      if (settings.includeTlsSessionInfoHeader) {
        attributes.get[HttpAttributes.TLSSessionInfo].map(sslSessionInfo =>
          model.headers.`Tls-Session-Info`(sslSessionInfo.session))
      } else None

    val sslSessionAttribute: Option[SSLSession] =
      if (settings.includeSslSessionAttribute)
        attributes.get[HttpAttributes.TLSSessionInfo].map(_.session)
      else
        None

    // Only library-materialized observers are offered hints. Never invoke a user implementation on the connection.
    val observer = subStream.correlationAttributes.get(EarlyHintsObserver.attributeKey) match {
      case Some(value) => EarlyHintsObserver.materializedObserverOrNull(value)
      case None        => null
    }

    @tailrec
    def rec(
        remainingHeaders: Seq[(String, AnyRef)],
        status: StatusCode = null,
        contentType: OptionVal[ContentType] = OptionVal.None,
        contentLength: Long = -1,
        seenRegularHeader: Boolean = false,
        headers: VectorBuilder[HttpHeader] = new VectorBuilder[HttpHeader]): HttpResponse =
      if (remainingHeaders.isEmpty) {
        // Required response status: https://www.rfc-editor.org/rfc/rfc9113.html#section-8.3.2
        checkRequiredPseudoHeader(":status", status)

        if (status == StatusCodes.SwitchingProtocols)
          malformedResponse("HTTP/2 does not support the 101 status code")
        if (Http2ResponseStatus.isInformational(status.intValue) && subStream.initialHeaders.endStream)
          malformedResponse("Informational responses must not end the stream")

        val entity = subStream.createEntity(contentLength, contentType)

        val responseHeaders =
          if ((observer ne null) && status == StatusCodes.EarlyHints) {
            val receivedHeaders = headers.result()
            observer.publish(receivedHeaders)
            // TLS session metadata is local, not a field from the 103 response.
            if (tlsSessionInfoHeader.isDefined) receivedHeaders :+ tlsSessionInfoHeader.get
            else receivedHeaders
          } else {
            if (tlsSessionInfoHeader.isDefined) headers += tlsSessionInfoHeader.get
            headers.result()
          }
        val responseAttributes = if (observer eq null) subStream.correlationAttributes
        else subStream.correlationAttributes - EarlyHintsObserver.attributeKey

        val response = HttpResponse(
          status = status,
          headers = responseHeaders,
          entity = entity,
          HttpProtocols.`HTTP/2.0`).withAttributes(responseAttributes)
        sslSessionAttribute match {
          case Some(sslSession) => response.addAttribute(AttributeKeys.sslSession, SslSessionInfo(sslSession))
          case None             => response
        }

      } else remainingHeaders.head match {
        case (":status", statusCodeValue: String) =>
          checkUniquePseudoHeader(":status", status)
          checkNoRegularHeadersBeforePseudoHeader(":status", seenRegularHeader)
          rec(remainingHeaders.tail, statusCodeValue.toInt, contentType, contentLength, seenRegularHeader, headers)

        case ("content-type", contentTypeValue: ContentType) =>
          if (contentType.isEmpty) {
            if ((observer ne null) && status == StatusCodes.EarlyHints) headers += `Content-Type`(contentTypeValue)
            rec(remainingHeaders.tail, status, OptionVal.Some(contentTypeValue), contentLength, seenRegularHeader,
              headers)
          } else
            malformedResponse("HTTP message must not contain more than one content-type header")

        case ("content-type", ct: String) =>
          if (contentType.isEmpty) {
            val contentTypeValue =
              ContentType.parse(ct).getOrElse(malformedResponse(s"Invalid content-type: '$ct'"))
            if ((observer ne null) && status == StatusCodes.EarlyHints) headers += `Content-Type`(contentTypeValue)
            rec(remainingHeaders.tail, status, OptionVal.Some(contentTypeValue), contentLength, seenRegularHeader,
              headers)
          } else malformedResponse("HTTP message must not contain more than one content-type header")

        case ("content-length", length: String) =>
          if (contentLength == -1) {
            val contentLengthValue = length.toLong
            if (contentLengthValue < 0)
              malformedResponse("HTTP message must not contain a negative content-length header")
            rec(remainingHeaders.tail, status, contentType, contentLengthValue, seenRegularHeader, headers)
          } else malformedResponse("HTTP message must not contain more than one content-length header")

        case (name, _) if name.startsWith(':') =>
          malformedResponse(s"Unexpected pseudo-header '$name' in response")

        case (_, httpHeader: HttpHeader) =>
          rec(remainingHeaders.tail, status, contentType, contentLength, seenRegularHeader = true,
            headers += httpHeader)

        case (name, value: String) =>
          val httpHeader = parseHeaderPair(httpHeaderParser, name, value)
          validateHeader(httpHeader)
          rec(remainingHeaders.tail, status, contentType, contentLength, seenRegularHeader = true,
            headers += httpHeader)
      }

    subStream.initialHeaders.headerParseErrorDetails match {
      // a field HeaderDecompression rejected leaves no headers behind, which would otherwise be reported as a
      // missing ':status' rather than as what was wrong
      case Some(info) => malformedResponse(info.summary)
      case None       => rec(subStream.initialHeaders.keyValuePairs)
    }
  }

  private def malformedResponse(msg: String): Nothing =
    throw new Http2ProtocolException(s"Malformed response: $msg")
}
