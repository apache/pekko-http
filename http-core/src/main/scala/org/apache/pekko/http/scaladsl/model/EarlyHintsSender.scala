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

import scala.concurrent.Future

import org.apache.pekko.Done

/**
 * Sends header-only 103 (Early Hints) responses to the client of one HTTP/2 request.
 *
 * @since 2.1.0
 */
final class EarlyHintsSender private[http] (offer: Seq[HttpHeader] => Future[Done]) {

  /**
   * Offers one interim response. Await successful completion before offering another or completing
   * the final response. Completion means that the HTTP/2 stack has handed the fields to the
   * connection output, not that the peer has received them.
   *
   * The returned future fails if the stream is closed, a final response has already been submitted,
   * another offer is pending, or the connection cannot buffer the interim response.
   *
   * @since 2.1.0
   */
  def send(headers: Seq[HttpHeader]): Future[Done] = offer(headers)
}

object EarlyHintsSender {
  private[http] val key: AttributeKey[EarlyHintsSender] =
    AttributeKey[EarlyHintsSender]("pekko.http.http2.early-hints-sender")

  /**
   * Returns a sender for a request received by an HTTP/2 server with Early Hints explicitly enabled.
   * An HTTP/1.1 request or a server without Early Hints enabled has no sender.
   *
   * @since 2.1.0
   */
  def from(request: HttpRequest): Option[EarlyHintsSender] = request.attribute(key)
}
