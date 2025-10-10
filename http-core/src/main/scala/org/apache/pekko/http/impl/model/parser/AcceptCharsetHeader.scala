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
import pekko.http.scaladsl.model.headers.`Accept-Charset`
import pekko.http.scaladsl.model.HttpCharsetRange

private[parser] trait AcceptCharsetHeader { this: Parser with CommonRules with CommonActions =>

  // http://tools.ietf.org/html/rfc7231#section-5.3.3
  def `accept-charset` = rule {
    oneOrMore(`charset-range-decl`).separatedBy(listSep) ~ EOI ~> (`Accept-Charset`(_))
  }

  def `charset-range-decl` = rule {
    `charset-range-def` ~ optional(weight) ~> { (range, optQ) =>
      optQ match {
        case None    => range
        case Some(q) => range.withQValue(q)
      }
    }
  }

  def `charset-range-def` = rule {
    ws('*') ~ push(HttpCharsetRange.`*`) | token ~> (s => HttpCharsetRange(getCharset(s)))
  }
}
