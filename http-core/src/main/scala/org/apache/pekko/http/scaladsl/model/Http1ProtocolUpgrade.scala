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

package org.apache.pekko.http.scaladsl.model

import org.apache.pekko
import pekko.http.impl.engine.server.Http1Upgrade
import pekko.stream.scaladsl.Flow
import pekko.util.ByteString

/**
 * A request-bound offer to select an explicitly configured HTTP/1.1 protocol.
 *
 * @since 2.1.0
 */
final class Http1ProtocolUpgrade private (
    createAcceptance: (HttpRequest => Flow[ByteString, ByteString, Any]) => HttpResponse) {

  /**
   * Accept this offer using a flow for the selected protocol. The factory receives the original
   * completed HTTP request; the selected protocol must answer it before handling later messages.
   * Return the response from the application handler. Appending ordinary response headers is allowed.
   * Decline by returning an ordinary HTTP response instead.
   */
  def accept(handler: HttpRequest => Flow[ByteString, ByteString, Any]): HttpResponse =
    createAcceptance(handler)
}

object Http1ProtocolUpgrade {

  /**
   * Finds an eligible offer on a binding that explicitly enabled its protocol.
   *
   * @since 2.1.0
   */
  def fromRequest(request: HttpRequest): Option[Http1ProtocolUpgrade] =
    Http1Upgrade.acceptanceFor(request).map(new Http1ProtocolUpgrade(_))
}
