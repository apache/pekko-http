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

package org.apache.pekko.http.impl.engine.http2.hpack

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.http.impl.engine.http2.RequestParsing
import pekko.http.impl.engine.http2.RequestParsing.parseError
import pekko.http.scaladsl.model
import pekko.http.scaladsl.model.HttpHeader.ParsingResult
import model.{ HttpHeader, HttpMethod, HttpMethods, IllegalUriException, ParsingException, StatusCode, Uri }
import pekko.http.scaladsl.settings.ParserSettings

@InternalApi
private[pekko] object Http2HeaderParsing {
  sealed abstract class HeaderParser[+T](val headerName: String) {
    def parse(name: String, value: String, parserSettings: ParserSettings): T
    def get(value: AnyRef): T = value.asInstanceOf[T]
  }
  sealed abstract class Verbatim(headerName: String) extends HeaderParser[String](headerName) {
    override def parse(name: String, value: String, parserSettings: ParserSettings): String = value
  }

  object Scheme extends Verbatim(":scheme")
  object Method extends HeaderParser[HttpMethod](":method") {
    override def parse(name: String, value: String, parserSettings: ParserSettings): HttpMethod =
      HttpMethods.getForKey(value)
        .orElse(parserSettings.customMethods(value))
        .getOrElse(RequestParsing.parseError(s"Unknown HTTP method: '$value'", ":method"))
  }
  object PathAndQuery extends HeaderParser[(Uri.Path, Option[String])](":path") {
    override def parse(name: String, value: String, parserSettings: ParserSettings): (Uri.Path, Option[String]) =
      try {
        Uri.parseHttp2PathPseudoHeader(value, mode = parserSettings.uriParsingMode)
      } catch {
        case IllegalUriException(info) => throw new ParsingException(info)
      }
  }
  object Authority extends HeaderParser[Uri.Authority](":authority") {
    override def parse(name: String, value: String, parserSettings: ParserSettings): Uri.Authority =
      try {
        Uri.parseHttp2AuthorityPseudoHeader(value /*FIXME: , mode = serverSettings.parserSettings.uriParsingMode*/ )
      } catch {
        case IllegalUriException(info) => throw new ParsingException(info)
      }
  }
  object Status extends HeaderParser[StatusCode](":status") {
    override def parse(name: String, value: String, parserSettings: ParserSettings): StatusCode =
      value.toInt
  }
  object ContentType extends HeaderParser[model.ContentType]("content-type") {
    override def parse(name: String, value: String, parserSettings: ParserSettings): model.ContentType =
      model.ContentType.parse(value) match {
        case Right(tpe) => tpe
        case Left(_)    =>
          parseError(s"Invalid content-type: '$value'", "content-type")
      }
  }
  object ContentLength extends Verbatim("content-length")
  object Cookie extends Verbatim("cookie")
  object OtherHeader extends HeaderParser[HttpHeader]("<other>") {
    override def parse(name: String, value: String, parserSettings: ParserSettings): HttpHeader =
      throw new IllegalStateException("Needs to be parsed directly")
  }

  val Parsers: Map[String, HeaderParser[AnyRef]] =
    Seq(
      Method, Scheme, Authority, PathAndQuery, ContentType, Status, ContentLength, Cookie).map(p =>
      p.headerName -> p).toMap

  def parse(name: String, value: String, parserSettings: ParserSettings): (String, AnyRef) = {
    name -> Parsers.getOrElse(name, Modeled).parse(name, value, parserSettings)
  }

  private object Modeled extends HeaderParser[HttpHeader]("<modeled>") {
    override def parse(name: String, value: String, parserSettings: ParserSettings): HttpHeader =
      HttpHeader.parse(name, value, parserSettings) match {
        case ParsingResult.Ok(header, _) => header
        case ParsingResult.Error(error)  => throw new IllegalStateException(error.detail)
      }
  }
}
