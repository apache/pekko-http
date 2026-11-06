/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.http.impl.model.parser

import java.util.Base64

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.http.scaladsl.model.headers._
import pekko.util.ByteString

import org.parboiled2._

/**
 * INTERNAL API
 *
 * The headers of RFC 9842, which are structured fields (RFC 9651). Only the parts of structured fields that these
 * headers need are modelled, others are accepted so that a member that is not known yet can be ignored.
 */
@InternalApi
private[parser] trait CompressionDictionaryHeaders {
  this: Parser with CommonRules with AcceptHeader =>
  import CompressionDictionaryHeaders._

  // https://www.rfc-editor.org/rfc/rfc9842#section-2.2
  def `available-dictionary` = rule {
    `sf-binary` ~ EOI ~> (hash => `Available-Dictionary`(ByteString(Base64.getDecoder.decode(hash))))
  }

  // https://www.rfc-editor.org/rfc/rfc9842#section-2.3
  def `dictionary-id` = rule { `sf-string` ~ EOI ~> (`Dictionary-ID`(_)) }

  // https://www.rfc-editor.org/rfc/rfc9842#section-2.1
  def `use-as-dictionary` = rule {
    oneOrMore(`sf-dictionary-member`).separatedBy(listSep) ~ EOI ~> (members => useAsDictionary(members.toMap))
  }

  def `sf-dictionary-member`: Rule1[(String, SfValue)] = rule {
    `sf-key` ~ ('=' ~ (`sf-inner-list` | `sf-bare-item`) | push(SfOther: SfValue)) ~ `sf-parameters` ~>
    ((key: String, value: SfValue) => key -> value)
  }

  def `sf-inner-list`: Rule1[SfValue] = rule {
    '(' ~ OWS ~ zeroOrMore(`sf-bare-item` ~ `sf-parameters` ~ OWS) ~ ')' ~ OWS ~>
    ((items: Seq[SfValue]) => SfInnerList(items))
  }

  def `sf-bare-item`: Rule1[SfValue] = rule {
    `sf-string` ~> ((value: String) => SfString(value): SfValue) |
    `sf-token` ~> ((value: String) => SfToken(value): SfValue) |
    oneOrMore(`sf-other-char`) ~ OWS ~ push(SfOther: SfValue)
  }

  def `sf-parameters` = rule {
    zeroOrMore(';' ~ zeroOrMore(' ') ~ `sf-key` ~ optional('=' ~ `sf-bare-item`) ~>
    ((_: String, _: Option[SfValue]) => ()))
  }

  def `sf-binary`: Rule1[String] = rule { ':' ~ capture(zeroOrMore(`sf-base64-char`)) ~ ':' }
}

/** INTERNAL API */
@InternalApi
private[parser] object CompressionDictionaryHeaders {
  sealed trait SfValue
  final case class SfString(value: String) extends SfValue
  final case class SfToken(value: String) extends SfValue
  final case class SfInnerList(items: Seq[SfValue]) extends SfValue
  case object SfOther extends SfValue

  val `sf-base64-char` = CharPredicate.AlphaNum ++ "+/="
  val `sf-other-char` = CharPredicate('!' to '~') -- "\"(),;"

  /** Throws for an invalid header, which makes parsing fail. A member with a wrong type makes the dictionary invalid. */
  def useAsDictionary(members: Map[String, SfValue]): `Use-As-Dictionary` = {
    def invalid(member: String) = throw new IllegalArgumentException(s"Invalid '$member' member in Use-As-Dictionary")
    def string(member: String): Option[String] = members.get(member).map {
      case SfString(value) => value
      case _               => invalid(member)
    }

    `Use-As-Dictionary`(
      matchPattern = string("match").getOrElse(invalid("match")),
      matchDest = members.get("match-dest") match {
        case None                     => Nil
        case Some(SfInnerList(items)) =>
          items.map {
            case SfString(value) => value
            case _               => invalid("match-dest")
          }
        case _ => invalid("match-dest")
      },
      id = string("id").getOrElse(""),
      dictionaryType = members.get("type") match {
        case None                 => `Use-As-Dictionary`.RawType
        case Some(SfToken(value)) => value
        case _                    => invalid("type")
      })
  }
}
