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

package org.apache.pekko.http.impl.engine.http2

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.http.scaladsl.model.{ StatusCode, StatusCodes }

/** INTERNAL API */
@InternalApi
private[http2] object Http2ResponseStatus {
  def isInformational(code: Int): Boolean = code >= 100 && code < 200

  // Classify only a complete three-digit status; HTTP-layer parsing still validates the field section.
  def isInformational(code: String): Boolean = isInformational(parseValidCode(code))

  def isContentFreeFinal(code: String, customStatusCodes: Int => Option[StatusCode]): Boolean = {
    val parsed = parseValidCode(code)
    parsed >= 200 && lookupRegistered(parsed, customStatusCodes).exists(!_.allowsEntity)
  }

  def resolve(code: String, customStatusCodes: Int => Option[StatusCode]): Option[StatusCode] = {
    val parsed = parseValidCode(code)
    if (parsed < 0) None
    else lookupRegistered(parsed, customStatusCodes).orElse(Some(StatusCodes.custom(parsed, "")))
  }

  private def lookupRegistered(code: Int, customStatusCodes: Int => Option[StatusCode]): Option[StatusCode] =
    StatusCodes.getForKey(code).orElse(customStatusCodes(code))

  // RFC 9110 section 15: only three ASCII digits representing a code in 100–599 form a valid HTTP status.
  private def parseValidCode(code: String): Int =
    if (code.length == 3 && code.charAt(0) >= '1' && code.charAt(0) <= '5' &&
      code.charAt(1) >= '0' && code.charAt(1) <= '9' && code.charAt(2) >= '0' && code.charAt(2) <= '9')
      (code.charAt(0) - '0') * 100 + (code.charAt(1) - '0') * 10 + code.charAt(2) - '0'
    else -1
}
