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

import scala.collection.BufferedIterator

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.http.impl.util.enhanceString_
import pekko.http.scaladsl.model.headers.Language

/**
 * INTERNAL API
 * Checks RFC 5646 section 2.1 syntax, not the registration of individual subtags.
 */
@InternalApi
private[http] object LanguageTagSyntax {
  private val alpha: Char => Boolean = CharacterClasses.ALPHA.apply
  private val alphanum: Char => Boolean = CharacterClasses.ALPHANUM.apply
  private val digit: Char => Boolean = CharacterClasses.DIGIT.apply

  // The regular grandfathered tags already match the langtag production.
  private val irregularGrandfathered =
    Set("en-gb-oed", "i-ami", "i-bnn", "i-default", "i-enochian", "i-hak", "i-klingon", "i-lux", "i-mingo",
      "i-navajo", "i-pwn", "i-tao", "i-tay", "i-tsu", "sgn-be-fr", "sgn-be-nl", "sgn-ch-de")

  def isWellFormed(tag: Language): Boolean = {
    val primary = tag.primaryTag
    val subTags = tag.subTags
    val length = primary.length
    if (length < 1 || length > 8 || !primary.forall(alpha)) false
    else if (subTags.isEmpty) length >= 2
    else if (!subTags.forall(s => s.nonEmpty && s.length <= 8 && s.forall(alphanum))) false
    else if (primary.equalsIgnoreCase("x")) true
    else
      (length >= 2 && ordinarySubTags(length, subTags.iterator.buffered)) ||
      (subTags.size <= 2 && irregularGrandfathered.contains((primary +: subTags).mkString("-").toRootLowerCase))
  }

  // Each subtag has already been checked to be nonempty, ASCII alphanumeric, and at most eight characters.
  private def ordinarySubTags(primaryLength: Int, remaining: BufferedIterator[String]): Boolean = {
    def nextIf(predicate: String => Boolean): Boolean =
      if (remaining.hasNext && predicate(remaining.head)) {
        remaining.next()
        true
      } else false

    if (primaryLength <= 3) {
      var extlangs = 0
      while (extlangs < 3 && nextIf(s => s.length == 3 && s.forall(alpha))) extlangs += 1
    }
    nextIf(s => s.length == 4 && s.forall(alpha)) // script
    nextIf(s => (s.length == 2 && s.forall(alpha)) || (s.length == 3 && s.forall(digit))) // region
    while (nextIf(s => s.length >= 5 || (s.length == 4 && digit(s.head)))) () // variants

    while (nextIf(s => s.length == 1 && !s.equalsIgnoreCase("x"))) {
      if (!nextIf(_.length >= 2)) return false
      while (nextIf(_.length >= 2)) ()
    }
    if (nextIf(_.equalsIgnoreCase("x"))) remaining.hasNext // privateuse
    else !remaining.hasNext
  }
}
