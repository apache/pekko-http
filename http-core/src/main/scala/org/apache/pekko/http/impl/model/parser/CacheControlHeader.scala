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

import org.apache.pekko
import pekko.http.scaladsl.model.headers._
import pekko.http.scaladsl.model.headers.CacheDirectives._

private[parser] trait CacheControlHeader { this: HeaderParser =>

  // http://tools.ietf.org/html/rfc7234#section-5.2
  def `cache-control` = rule {
    oneOrMore(`cache-directive`).separatedBy(listSep) ~ EOI ~> (`Cache-Control`(_))
  }

  def `cache-directive` = rule(
    "no-store" ~ push(`no-store`)
    | "no-transform" ~ push(`no-transform`)
    | "max-age=" ~ `delta-seconds`                      ~> (`max-age`(_))
    | "max-stale" ~ optional(ws('=') ~ `delta-seconds`) ~> (`max-stale`(_))
    | "min-fresh=" ~ `delta-seconds`                    ~> (`min-fresh`(_))
    | "only-if-cached" ~ push(`only-if-cached`)
    | "public" ~ push(`public`)
    | "private" ~ (ws('=') ~ `field-names`  ~> (`private`(_)) | push(`private`()))
    | "no-cache" ~ (ws('=') ~ `field-names` ~> (`no-cache`(_)) | push(`no-cache`))
    | "must-revalidate" ~ push(`must-revalidate`)
    | "proxy-revalidate" ~ push(`proxy-revalidate`)
    | "s-maxage=" ~ `delta-seconds` ~> (`s-maxage`(_))
    | "immutable" ~ push(immutableDirective)
    | token ~ optional(ws('=') ~ word) ~> (CacheDirective.custom(_, _)))

  def `field-names` = rule { `quoted-tokens` | token ~> (List(_)) }

  def `quoted-tokens` = rule { '"' ~ zeroOrMore(`quoted-tokens-elem`).separatedBy(listSep) ~ '"' }

  def `quoted-tokens-elem` = rule {
    clearSB() ~ zeroOrMore(!'"' ~ !',' ~ qdtext ~ appendSB() | `quoted-pair`) ~ push(sb.toString)
  }
}
