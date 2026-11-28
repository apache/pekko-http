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

import org.parboiled2.Rule0

import org.apache.pekko
import pekko.http.scaladsl.model.headers.CacheDirectives._
import pekko.http.scaladsl.model.headers._

private[parser] trait CacheControlHeader { this: HeaderParser =>
  import CharacterClasses.tchar

  // https://www.rfc-editor.org/rfc/rfc9111.html#section-5.2
  def `cache-control` = rule {
    listStart ~ oneOrMore(`cache-directive`).separatedBy(listSep) ~ listEnd ~ EOI ~> (`Cache-Control`(_))
  }

  def `cache-directive` = rule(
    directiveName("no-store") ~ push(`no-store`)
    | directiveName("no-transform") ~ push(`no-transform`)
    | directiveName("max-age") ~ '=' ~ `delta-seconds` ~>
    (`max-age`(_))
    | directiveName("max-stale") ~ optional(ws('=') ~ `delta-seconds`) ~>
    (`max-stale`(_))
    | directiveName("min-fresh") ~ '=' ~ `delta-seconds` ~>
    (`min-fresh`(_))
    | directiveName("only-if-cached") ~ push(`only-if-cached`)
    | directiveName("public") ~ push(`public`)
    | directiveName("private") ~
    (ws('=') ~ `field-names` ~> (`private`(_)) | push(`private`()))
    | directiveName("no-cache") ~
    (ws('=') ~ `field-names` ~> (`no-cache`(_)) | push(`no-cache`))
    | directiveName("must-revalidate") ~ push(`must-revalidate`)
    | directiveName("proxy-revalidate") ~ push(`proxy-revalidate`)
    | directiveName("s-maxage") ~ '=' ~ `delta-seconds` ~>
    (`s-maxage`(_))
    | directiveName("immutable") ~ push(immutableDirective)
    | token ~ optional(ws('=') ~ word) ~>
    (CacheDirective.custom(_, _)))

  // Keep custom names intact and only treat ASCII case variants of a complete known token as modeled.
  private def directiveName(name: String): Rule0 = rule {
    (str(name) | ignoreCase(name) ~ test(matchedNameIsAscii(name.length))) ~ !tchar
  }

  private def matchedNameIsAscii(length: Int): Boolean = {
    var offset = -length
    while (offset < 0) {
      if (charAt(offset) >= 0x80) return false
      offset += 1
    }
    true
  }

  def `field-names` = rule { `quoted-tokens` | token ~> (List(_)) }

  def `quoted-tokens` = rule { '"' ~ zeroOrMore(`quoted-tokens-elem`).separatedBy(listSep) ~ '"' }

  def `quoted-tokens-elem` = rule {
    clearSB() ~ zeroOrMore(!'"' ~ !',' ~ qdtext ~ appendSB() | `quoted-pair`) ~ push(sb.toString.trim)
  }
}
