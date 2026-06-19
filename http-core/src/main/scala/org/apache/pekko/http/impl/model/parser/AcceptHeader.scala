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
import org.parboiled2.Parser
import pekko.http.scaladsl.model.headers._
import pekko.http.scaladsl.model.{ MediaRange, MediaRanges }
import pekko.http.impl.util._

private[parser] trait AcceptHeader { this: Parser with CommonRules with CommonActions =>
  import CharacterClasses._

  // http://tools.ietf.org/html/rfc7231#section-5.3.2
  def accept = rule {
    zeroOrMore(`media-range-decl`).separatedBy(listSep) ~ EOI ~> (Accept(_))
  }

  // https://www.rfc-editor.org/rfc/rfc10008.html#section-4.1
  // Accept-Query uses Structured Fields syntax (RFC 9651): media types may appear
  // as tokens (application/json) or quoted strings ("application/jsonpath").
  // Unlike Accept, q-values are not meaningful and are stripped if present.
  def `accept-query` = rule {
    zeroOrMore(`accept-query-media-range-decl`).separatedBy(listSep) ~ EOI ~> (`Accept-Query`(_))
  }

  def `accept-query-media-range-decl` = rule {
    `accept-query-media-range-def` ~ OWS ~ zeroOrMore(ws(';') ~ parameter) ~> { (main, sub, params) =>
      val cleanParams = TreeMap(params.filterNot(_._1 == "q"): _*)
      if (sub == "*") {
        val mainLower = main.toRootLowerCase
        MediaRanges.getForKey(mainLower) match {
          case Some(registered) => if (cleanParams.isEmpty) registered else registered.withParams(cleanParams)
          case None             => MediaRange.custom(mainLower, cleanParams)
        }
      } else {
        MediaRange(getMediaType(main, sub, cleanParams contains "charset", cleanParams))
      }
    }
  }

  def `accept-query-media-range-def` = rule {
    "*/*" ~ push("*") ~ push("*") |
    '*' ~ push("*") ~ push("*") |
    `type` ~ '/' ~
    ('*' ~ !tchar ~ push("*") | subtype) |
    `quoted-string` ~>
    ((s: String) => {
      val slashIdx = s.indexOf('/')
      if (slashIdx > 0) push(s.substring(0, slashIdx)) ~ push(s.substring(slashIdx + 1))
      else push(s) ~ push("*")
    })
  }

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
