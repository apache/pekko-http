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

package org.apache.pekko.http.impl.util

import java.nio.ByteBuffer
import java.nio.charset.{ CharacterCodingException, StandardCharsets }

import org.parboiled2.CharUtils

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.http.impl.model.parser.CharacterClasses.HEXDIG

/**
 * INTERNAL API
 *
 * Percent-decodes a multipart/form-data `filename` parameter as UTF-8 (RFC 7578, section 4.2).
 * Returns the input unchanged if it is not valid percent-encoded UTF-8.
 */
@InternalApi
private[http] object MultipartFilenameDecoding {

  def decode(filename: String): String =
    if (filename.indexOf('%') < 0) filename
    else {
      val decoded = tryDecode(filename)
      if (decoded eq null) filename else decoded
    }

  /** Returns `null` if `s` contains an invalid percent-escape or the escaped bytes are not valid UTF-8. */
  private def tryDecode(s: String): String = {
    val sb = new java.lang.StringBuilder(s.length)
    val bytes = new Array[Byte](s.length / 3)
    var i = 0
    var ok = true
    while (ok && i < s.length) {
      if (s.charAt(i) == '%') {
        var n = 0
        while (ok && i < s.length && s.charAt(i) == '%') {
          if (i + 2 < s.length && HEXDIG(s.charAt(i + 1)) && HEXDIG(s.charAt(i + 2))) {
            bytes(n) = (CharUtils.hexValue(s.charAt(i + 1)) * 16 + CharUtils.hexValue(s.charAt(i + 2))).toByte
            n += 1
            i += 3
          } else ok = false
        }
        if (ok) {
          // a new decoder reports malformed input instead of replacing it
          try sb.append(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes, 0, n)))
          catch { case _: CharacterCodingException => ok = false }
        }
      } else {
        sb.append(s.charAt(i))
        i += 1
      }
    }
    if (ok) sb.toString else null
  }
}
