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

import scala.collection.immutable.VectorMap
import scala.util.control.NoStackTrace

import org.parboiled2.CharPredicate

import org.apache.pekko
import pekko.annotation.InternalApi

/**
 * INTERNAL API
 *
 * Reads the RFC 8941 Dictionary grammar. Only Integer and Boolean values are projected; callers
 * keep the supplied model field value so that unrecognized members and parameters are not lost.
 */
@InternalApi
private[http] object StructuredFieldDictionary {
  sealed trait Member
  final case class IntegerMember(value: Long) extends Member
  final case class BooleanMember(value: Boolean) extends Member
  case object OpaqueMember extends Member

  def parse(value: String): Option[VectorMap[String, Member]] =
    try Some(new Reader(value).readDictionary())
    catch {
      case ParseFailure => None
    }

  private object ParseFailure extends RuntimeException with NoStackTrace

  private val keyStart = CharacterClasses.LOWER_ALPHA ++ '*'
  private val keyChar = keyStart ++ CharacterClasses.DIGIT ++ "_-."
  private val tokenStart = CharacterClasses.ALPHA ++ '*'
  private val tokenChar = CharacterClasses.tchar ++ ":/"
  private val space = CharPredicate(' ')
  private val base64Decoder = Base64.getDecoder
  private val trueMember = BooleanMember(true)
  private val falseMember = BooleanMember(false)

  private final class Reader(input: String) {
    import CharacterClasses.{ DIGIT, WSP }

    private var index = 0
    private def nonEmpty: Boolean = index < input.length
    private def current: Char = if (nonEmpty) input.charAt(index) else '\u0000'

    // RFC 8941 sections 4.2 and 4.2.2
    def readDictionary(): VectorMap[String, Member] = {
      skip(space)
      var members = VectorMap.empty[String, Member]
      while (nonEmpty) {
        val start = index
        readKey()
        val key = input.substring(start, index)
        val member = if (consume('=')) readItemOrInnerList()
        else {
          readParameters()
          trueMember
        }
        members = members.updated(key, member)
        skip(WSP)
        if (nonEmpty) {
          expect(consume(','))
          skip(WSP)
          expect(nonEmpty)
        }
      }
      members
    }

    private def readItemOrInnerList(): Member =
      if (consume('(')) {
        skip(space)
        while (!consume(')')) {
          val _ = readItem()
          expect(current == ' ' || current == ')')
          skip(space)
        }
        readParameters()
        OpaqueMember
      } else readItem()

    private def readItem(): Member = {
      val member = readBareItem()
      readParameters()
      member
    }

    private def readKey(): Unit = {
      expect(keyStart(current))
      index += 1
      skip(keyChar)
    }

    private def readParameters(): Unit =
      while (consume(';')) {
        skip(space)
        readKey()
        if (consume('=')) {
          val _ = readBareItem()
        }
      }

    private def readBareItem(): Member =
      current match {
        case c if c == '-' || DIGIT(c) => readNumber()
        case '"'                       =>
          readString()
          OpaqueMember
        case c if tokenStart(c) =>
          skip(tokenChar)
          OpaqueMember
        case ':' =>
          readByteSequence()
          OpaqueMember
        case '?' =>
          index += 1
          if (consume('1')) trueMember
          else {
            expect(consume('0'))
            falseMember
          }
        case _ => throw ParseFailure
      }

    private def readNumber(): Member = {
      val start = index
      val _ = consume('-')
      val digits = index
      skip(DIGIT)
      val wholeLength = index - digits
      expect(wholeLength > 0)
      if (consume('.')) {
        val fraction = index
        skip(DIGIT)
        val fractionLength = index - fraction
        expect(wholeLength <= 12 && fractionLength >= 1 && fractionLength <= 3)
        OpaqueMember
      } else {
        expect(wholeLength <= 15)
        IntegerMember(java.lang.Long.parseLong(input.substring(start, index)))
      }
    }

    private def readString(): Unit = {
      index += 1
      while (!consume('"')) {
        val ch = current
        expect(ch >= ' ' && ch <= '~')
        index += 1
        if (ch == '\\') expect(consume('"') || consume('\\'))
      }
    }

    private def readByteSequence(): Unit = {
      val start = index + 1
      val end = input.indexOf(':', start)
      expect(end >= start)
      // The basic decoder accepts the RFC 8941 Base64 alphabet and can infer missing padding.
      try {
        val _ = base64Decoder.decode(input.substring(start, end))
      } catch {
        case _: IllegalArgumentException => throw ParseFailure
      }
      index = end + 1
    }

    private def consume(ch: Char): Boolean =
      if (current == ch) {
        index += 1
        true
      } else false

    private def skip(chars: CharPredicate): Unit =
      while (nonEmpty && chars(current)) index += 1

    private def expect(condition: Boolean): Unit =
      if (!condition) throw ParseFailure
  }
}
