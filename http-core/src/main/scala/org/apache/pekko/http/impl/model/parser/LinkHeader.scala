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

import org.parboiled2.{ Parser, Rule0, Rule1 }

import org.apache.pekko
import pekko.http.scaladsl.model.headers._
import pekko.http.scaladsl.model.{ ErrorInfo, IllegalUriException, ParsingException, Uri }

private[parser] trait LinkHeader { this: Parser with CommonRules with CommonActions =>
  import CharacterClasses._

  // https://www.rfc-editor.org/rfc/rfc8288.html#section-3
  def `link` = rule {
    listStart ~ zeroOrMore(`link-value`).separatedBy(listSep) ~ listEnd ~ EOI ~> (Link(_))
  }

  def `link-value` = rule {
    ws('<') ~ UriReference('>') ~ ws('>') ~ oneOrMore(ws(';') ~ `link-param`) ~> (sanitize(_)) ~> (LinkValue(_, _))
  }

  // The title* model keeps the encoded ext-value (https://www.rfc-editor.org/rfc/rfc8187.html#section-3.2.1).
  def `link-param` = rule(
    parameterName("rel") ~ `relation-types`     ~> LinkParams.rel.apply _
    | parameterName("anchor") ~ anchorValue     ~> LinkParams.anchor.apply _
    | parameterName("rev") ~ `relation-types`   ~> LinkParams.rev.apply _
    | parameterName("hreflang") ~ hreflangValue ~> LinkParams.hreflang.apply _
    | parameterName("media") ~ word             ~> LinkParams.media.apply _
    | parameterName("title") ~ word             ~> LinkParams.title.apply _
    | parameterName("title*") ~ word            ~> LinkParams.`title*`.apply _
    | (parameterName("type") ~ (ws('"') ~ `link-media-type` ~ ws('"') | `link-media-type`) ~>
    LinkParams.`type`.apply _))
  // TODO: support `link-extension`

  // RFC 8288 §2.2: attribute names are ASCII tokens and compare case-insensitively.
  private def parameterName(name: String): Rule0 = rule {
    (capture(ignoreCase(name)) ~> ((matched: String) => test(equalsAsciiCaseInsensitive(matched, name)))) ~ OWS ~
    ws('=')
  }

  // RFC 8288 §§3 and 3.2: a token can also represent an anchor URI-reference.
  private def anchorValue: Rule1[Uri] = rule {
    ws('"') ~ UriReference('"') ~ ws('"') | token ~> ((value: String) => newUriParser(value).parseUriReference())
  }

  // Apply the existing language grammar after unquoting (RFC 8288 §§3 and 3.4.1).
  private def hreflangValue: Rule1[Language] = rule {
    language | `quoted-string` ~> ((value: String) => parseQuotedLanguage(value))
  }

  private def parseQuotedLanguage(value: String): Language = {
    import Parser.DeliveryScheme.Either
    val parser: Parser with LinkHeader = new HeaderParser(value)
    parser.linkLanguage.run() match {
      case Right(lang) => lang
      case Left(_)     => throw ParsingException(ErrorInfo("Illegal Link hreflang parameter"))
    }
  }

  private def linkLanguage: Rule1[Language] = rule { language ~ EOI }

  def `relation-types` = rule(
    ws('"') ~ oneOrMore(`relation-type`).separatedBy(oneOrMore(SP)) ~> (_.mkString(" ")) ~ ws('"')
    | `relation-type` ~ OWS)

  def `relation-type` = rule { `reg-rel-type` | `ext-rel-type` }

  def `reg-rel-type` = rule {
    capture(LOWER_ALPHA ~ zeroOrMore(`reg-rel-type-octet`)) ~ !VCHAR
  }

  def `ext-rel-type` = rule {
    URI
  }

  ////////////////////////////// helpers ///////////////////////////////////

  def UriReference(terminationChar: Char) = rule {
    capture(oneOrMore(!terminationChar ~ VCHAR)) ~> (newUriParser(_).parseUriReference())
  }

  def URI = rule {
    capture(oneOrMore(!'"' ~ !';' ~ !',' ~ VCHAR)) ~> { s =>
      try new UriParser(s).parseUriReference()
      catch {
        case IllegalUriException(info) =>
          throw ParsingException(info.withSummaryPrepended("Illegal `Link` header relation-type"))
      }
      s
    }
  }

  def `link-media-type` =
    rule { `media-type` ~> ((mt, st, pm) => getMediaType(mt, st, pm contains "charset", TreeMap(pm: _*))) }

  // filter out subsequent `rel`, `media`, `title`, `type` and `type*` params
  @tailrec private def sanitize(params: Seq[LinkParam], result: Seq[LinkParam] = Nil,
      seenRel: Boolean = false,
      seenMedia: Boolean = false, seenTitle: Boolean = false, seenTitleS: Boolean = false,
      seenType: Boolean = false): Seq[LinkParam] =
    params match {
      case Seq(x: LinkParams.rel, tail @ _*) =>
        sanitize(tail, if (seenRel) result else result :+ x, seenRel = true, seenMedia, seenTitle, seenTitleS, seenType)
      case Seq(x: LinkParams.media, tail @ _*) => sanitize(tail, if (seenMedia) result else result :+ x, seenRel,
          seenMedia = true, seenTitle, seenTitleS, seenType)
      case Seq(x: LinkParams.title, tail @ _*) => sanitize(tail, if (seenTitle) result else result :+ x, seenRel,
          seenMedia, seenTitle = true, seenTitleS, seenType)
      case Seq(x: LinkParams.`title*`, tail @ _*) => sanitize(tail, if (seenTitleS) result else result :+ x, seenRel,
          seenMedia, seenTitle, seenTitleS = true, seenType)
      case Seq(x: LinkParams.`type`, tail @ _*) => sanitize(tail, if (seenType) result else result :+ x, seenRel,
          seenMedia, seenTitle, seenTitleS, seenType = true)
      case Seq(head, tail @ _*) => sanitize(tail, result :+ head, seenRel, seenMedia, seenTitle, seenTitleS, seenType)
      case Nil                  => result
    }
}
