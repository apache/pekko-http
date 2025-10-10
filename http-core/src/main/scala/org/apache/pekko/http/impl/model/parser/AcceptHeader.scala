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
    `type` ~ '/' ~ ('*' ~ !tchar ~ push("*") | subtype)
  }
}
