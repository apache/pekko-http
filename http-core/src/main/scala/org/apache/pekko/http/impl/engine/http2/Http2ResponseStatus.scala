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
import pekko.http.scaladsl.model.StatusCodes

/** INTERNAL API */
@InternalApi
private[http2] object Http2ResponseStatus {
  def isInformational(code: Int): Boolean = code >= 100 && code < 200

  // Classify only a complete three-digit status; HTTP-layer parsing still validates the field section.
  def isInformational(code: String): Boolean = isInformational(parseCode(code))

  // Match response materialization: only the built-in status registry is consulted in HTTP/2.
  def isContentFreeFinal(code: String): Boolean = {
    val parsed = parseCode(code)
    parsed >= 200 && StatusCodes.getForKey(parsed).exists(!_.allowsEntity)
  }

  private def parseCode(code: String): Int =
    if (code.length == 3 && code.charAt(0) >= '0' && code.charAt(0) <= '9' &&
      code.charAt(1) >= '0' && code.charAt(1) <= '9' && code.charAt(2) >= '0' && code.charAt(2) <= '9')
      (code.charAt(0) - '0') * 100 + (code.charAt(1) - '0') * 10 + code.charAt(2) - '0'
    else -1
}
