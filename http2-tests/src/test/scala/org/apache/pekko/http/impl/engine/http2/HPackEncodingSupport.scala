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

package org.apache.pekko.http.impl.engine.http2

import java.io.ByteArrayOutputStream

import org.apache.pekko
import pekko.http.impl.util.StringRendering
import pekko.http.scaladsl.model.{ HttpHeader, HttpRequest, HttpResponse }
import pekko.http.scaladsl.model.headers.RawHeader
import pekko.http.shaded.com.twitter.hpack.Encoder
import pekko.util.ByteString

/** Helps with a encoding headers to HPACK from Pekko HTTP model */
trait HPackEncodingSupport {
  lazy val encoder = new Encoder(Http2Protocol.InitialMaxHeaderTableSize)

  def encodeRequestHeaders(request: HttpRequest): ByteString =
    encodeHeaderPairs(headerPairsForRequest(request))

  def encodeHeaders(headers: Seq[HttpHeader]): ByteString =
    encodeHeaderPairs(headerPairsForHeaders(headers))

  def headersForRequest(request: HttpRequest): Seq[HttpHeader] =
    headerPairsForRequest(request).map {
      case (name, value) =>
        val header: HttpHeader = RawHeader(name, value)
        header
    }

  def headersForResponse(response: HttpResponse): Seq[HttpHeader] =
    Seq(
      RawHeader(":status", response.status.intValue.toString),
      RawHeader("content-type",
        response.entity.contentType.render(new StringRendering).get)) ++ response.headers.filter(_.renderInResponses)

  def headerPairsForRequest(request: HttpRequest): Seq[(String, String)] =
    Seq(
      ":method" -> request.method.value,
      ":scheme" -> request.uri.scheme.toString,
      ":path" -> request.uri.path.toString,
      ":authority" -> request.uri.authority.toString.drop(2),
      "content-type" -> request.entity.contentType.render(new StringRendering).get) ++
    request.entity.contentLengthOption.flatMap {
      case len if len != 0 => Some("content-length" -> len.toString)
      case _               => None
    }.toSeq ++
    headerPairsForHeaders(request.headers.filter(_.renderInRequests))

  def headerPairsForHeaders(headers: Seq[HttpHeader]): Seq[(String, String)] =
    headers.map(h => h.lowercaseName -> h.value)

  def encodeHeaderPairs(headerPairs: Seq[(String, String)]): ByteString = {
    val bos = new ByteArrayOutputStream()

    def encode(name: String, value: String): Unit = encoder.encodeHeader(bos, name, value, false)

    headerPairs.foreach((encode _).tupled)

    ByteString(bos.toByteArray)
  }
}
