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
import org.parboiled2.Parser
import pekko.http.scaladsl.model.headers._

private[parser] trait AcceptEncodingHeader { this: Parser with CommonRules with CommonActions =>

  // http://tools.ietf.org/html/rfc7231#section-5.3.4
  def `accept-encoding` = rule {
    zeroOrMore(`encoding-range-decl`).separatedBy(listSep) ~ EOI ~> (`Accept-Encoding`(_))
  }

  def `encoding-range-decl` = rule {
    codings ~ optional(weight) ~> { (range, optQ) =>
      optQ match {
        case None    => range
        case Some(q) => range.withQValue(q)
      }
    }
  }

  def codings = rule { ws('*') ~ push(HttpEncodingRange.`*`) | token ~> getEncoding _ }

  private def getEncoding(name: String): HttpEncodingRange =
    HttpEncodingRange(HttpEncodings.getForKeyCaseInsensitive(name).getOrElse(HttpEncoding.custom(name)))
}
