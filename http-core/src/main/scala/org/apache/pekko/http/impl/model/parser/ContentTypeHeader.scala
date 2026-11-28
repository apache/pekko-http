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

package org.apache.pekko.http.impl.model.parser

import scala.annotation.tailrec
import scala.collection.immutable.TreeMap

import org.parboiled2.{ Parser, Rule1 }

import org.apache.pekko
import pekko.http.scaladsl.model._

private[parser] trait ContentTypeHeader { this: Parser with CommonRules with CommonActions =>

  // RFC 9110 sections 5.6.6 and 8.3.1: parameter slots may be empty.
  def `content-type` = rule {
    `media-type` ~ EOI ~>
    ((main, sub, params) => headers.`Content-Type`(contentType(main, sub, params)))
  }

  // RFC 9110 section 5.6.4: spaces inside quoted values are data, not optional whitespace.
  // Delay building the model until the plain-metadata alternative is selected.
  def contentTypeWithPlainQuotedMetadata: Rule1[Option[() => HttpHeader]] = rule {
    `media-type` ~ EOI ~>
    ((main: String, sub: String, params: Seq[(String, String)]) =>
      plainQuotedMetadata(main, sub, params))
  }

  private def plainQuotedMetadata(
      main: String, sub: String, params: Seq[(String, String)]): Option[() => HttpHeader] = {
    val spaced = params.iterator.filter(_._2.contains("  "))
    if (!equalsAsciiCaseInsensitive(main, "multipart") && spaced.hasNext &&
      !spaced.exists(p => equalsAsciiCaseInsensitive(p._1, "charset")))
      Some(() => headers.`Content-Type`(contentType(main, sub, params)))
    else None
  }

  @tailrec private def contentType(
      main: String,
      sub: String,
      params: Seq[(String, String)],
      charset: Option[HttpCharset] = None,
      builder: StringMapBuilder = null): ContentType =
    params match {
      case Nil =>
        val parameters = if (builder eq null) Map.empty[String, String] else builder.result()
        getMediaType(main, sub, charset.isDefined, parameters) match {
          case x: MediaType.Binary                               => ContentType.Binary(x)
          case x: MediaType.WithFixedCharset                     => ContentType.WithFixedCharset(x)
          case x: MediaType.WithOpenCharset if charset.isDefined => ContentType.WithCharset(x, charset.get)
          case x: MediaType.WithOpenCharset if charset.isEmpty   => ContentType.WithMissingCharset(x)
        }

      case Seq((key, value), tail @ _*) if equalsAsciiCaseInsensitive(key, "charset") =>
        contentType(main, sub, tail, Some(getCharset(value)), builder)

      case Seq(kvp, tail @ _*) =>
        val b = if (builder eq null) TreeMap.newBuilder[String, String] else builder
        b += kvp
        contentType(main, sub, tail, charset, b)
    }
}
