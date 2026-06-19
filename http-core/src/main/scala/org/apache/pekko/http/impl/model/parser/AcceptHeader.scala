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

import scala.collection.immutable.TreeMap

import org.apache.pekko
import org.parboiled2.{ CharPredicate, Parser, Rule1 }
import pekko.http.scaladsl.model.headers._
import pekko.http.scaladsl.model.{ MediaRange, MediaRanges, ParsingException }
import pekko.http.impl.util._

private[parser] trait AcceptHeader { this: Parser with CommonRules with CommonActions =>
  import CharacterClasses._

  private[this] val acceptQueryMediaRangeChar = tchar ++ '/'
  private[this] val sfStringChar = CharPredicate('\u0020' to '\u0021', '\u0023' to '\u005B', '\u005D' to '\u007E')
  private[this] val sfStringEscapedChar = CharPredicate('"', '\\')

  // http://tools.ietf.org/html/rfc7231#section-5.3.2
  def accept = rule {
    zeroOrMore(`media-range-decl`).separatedBy(listSep) ~ EOI ~> (Accept(_))
  }

  // https://www.rfc-editor.org/rfc/rfc10008.html#section-3
  // Accept-Query uses Structured Fields syntax (RFC 9651): media types may appear
  // as tokens (application/json) or quoted strings ("application/jsonpath").
  def `accept-query` = rule {
    zeroOrMore(`accept-query-media-range-decl`).separatedBy(listSep) ~ EOI ~> (`Accept-Query`(_))
  }

  def `accept-query-media-range-decl` = rule {
    `accept-query-media-range-def` ~ zeroOrMore(`accept-query-param`) ~> {
      (mediaRange: (String, String), params: Seq[(String, String)]) =>
        val (main, sub) = mediaRange
        val mediaRangeParams = TreeMap(params: _*)
        if (sub == "*") {
          val mainLower = main.toRootLowerCase
          MediaRanges.getForKey(mainLower) match {
            case Some(registered) =>
              if (mediaRangeParams.isEmpty) registered else MediaRange.customWithParams(mainLower, mediaRangeParams)
            case None => MediaRange.customWithParams(mainLower, mediaRangeParams)
          }
        } else {
          MediaRange(getMediaType(main, sub, mediaRangeParams contains "charset", mediaRangeParams))
        }
    }
  }

  def `accept-query-media-range-def` = rule {
    `sf-token`  ~> (parseAcceptQueryMediaRange _) |
    `sf-string` ~> (parseAcceptQueryMediaRange _)
  }

  def `accept-query-param` = rule {
    ';' ~ OWS ~ `sf-key` ~ '=' ~ `sf-param-value` ~> ((_, _))
  }

  def `sf-param-value`: Rule1[String] = rule {
    `sf-string` | `sf-token`
  }

  def `sf-key`: Rule1[String] = rule {
    capture((LOWER_ALPHA | '*') ~ zeroOrMore(LOWER_ALPHA | DIGIT | '_' | '-' | '.' | '*'))
  }

  def `sf-token`: Rule1[String] = rule {
    capture((ALPHA | '*') ~ zeroOrMore(tchar | ':' | '/')) ~ OWS
  }

  def `sf-string`: Rule1[String] = rule {
    DQUOTE ~ clearSB() ~ zeroOrMore(`sf-string-char` ~ appendSB() | '\\' ~ `sf-string-escaped-char` ~ appendSB()) ~
    push(sb.toString) ~ DQUOTE ~ OWS
  }

  def `sf-string-char` = rule { sfStringChar }

  def `sf-string-escaped-char` = rule { sfStringEscapedChar }

  private def parseAcceptQueryMediaRange(value: String): (String, String) = {
    var slashIdx = -1
    var ix = 0
    while (ix < value.length) {
      val ch = value.charAt(ix)
      if (ch == '/') {
        if (slashIdx >= 0) invalidAcceptQueryMediaRange(value)
        slashIdx = ix
      } else if (!acceptQueryMediaRangeChar(ch)) invalidAcceptQueryMediaRange(value)
      ix += 1
    }

    if (slashIdx <= 0 || slashIdx == value.length - 1) invalidAcceptQueryMediaRange(value)

    val main = value.substring(0, slashIdx)
    val sub = value.substring(slashIdx + 1)
    if (main.indexOf('*') >= 0 && main != "*") invalidAcceptQueryMediaRange(value)
    if (sub.indexOf('*') >= 0 && sub != "*") invalidAcceptQueryMediaRange(value)
    if (main == "*" && sub != "*") invalidAcceptQueryMediaRange(value)

    (main, sub)
  }

  private def invalidAcceptQueryMediaRange(value: String): Nothing =
    throw ParsingException(s"Illegal Accept-Query media range '$value'")

  def `media-range-decl` = rule {
    `media-range-def` ~ OWS ~ zeroOrMore(ws(';') ~ parameter) ~> { (main, sub, params) =>
      if (sub == "*") {
        val mainLower = main.toRootLowerCase
        MediaRanges.getForKey(mainLower) match {
          case Some(registered) => if (params.isEmpty) registered else registered.withParams(TreeMap(params: _*))
          case None             => MediaRange.custom(mainLower, TreeMap(params: _*))
        }
      } else {
        val (p, q) = MediaRange.splitOffQValue(TreeMap(params: _*))
        MediaRange(getMediaType(main, sub, p contains "charset", p), q)
      }
    }
  }

  // this specific ordering PREVENTS that next rule is allowed to parse `*/xyz` as a valid media range
  def `media-range-def` = rule {
    "*/*" ~ push("*") ~ push("*") |
    '*' ~ push("*") ~ push("*") |
    `type` ~ '/' ~
    ('*' ~ !tchar ~ push("*") | subtype)
  }
}
