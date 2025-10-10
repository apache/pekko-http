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

private[parser] trait AcceptLanguageHeader { this: Parser with CommonRules with CommonActions =>

  // http://tools.ietf.org/html/rfc7231#section-5.3.5
  def `accept-language` = rule {
    oneOrMore(`language-range-decl`).separatedBy(listSep) ~ EOI ~> (`Accept-Language`(_))
  }

  def `language-range-decl` = rule {
    `language-range` ~ optional(weight) ~> { (range, optQ) =>
      optQ match {
        case None    => range
        case Some(q) => range.withQValue(q)
      }
    }
  }

  def `language-range` = rule { ws('*') ~ push(LanguageRange.`*`) | language ~> (LanguageRange(_)) }
}
