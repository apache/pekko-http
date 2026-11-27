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

package org.apache.pekko.http.javadsl.model

import java.util.Optional
import java.util.concurrent.CompletionStage

import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._
import scala.jdk.OptionConverters._

import org.apache.pekko.Done
import org.apache.pekko.http.impl.util.JavaMapping.Implicits._
import org.apache.pekko.http.scaladsl.model.{ EarlyHintsSender => ScalaEarlyHintsSender }

/**
 * Sends header-only 103 (Early Hints) responses to the client of one HTTP/2 request.
 *
 * @since 2.1.0
 */
final class EarlyHintsSender private[http] (delegate: ScalaEarlyHintsSender) {

  /**
   * Offers one interim response. Await successful completion before offering another or completing
   * the final response. Completion means that the HTTP/2 stack has handed the fields to the
   * connection output, not that the peer has received them.
   *
   * The returned stage fails if the stream is closed, a final response has already been submitted,
   * another offer is pending, or the connection cannot buffer the interim response.
   *
   * @since 2.1.0
   */
  def send(headers: java.lang.Iterable[? <: HttpHeader]): CompletionStage[Done] =
    delegate.send(headers.asScala.iterator.map(header => (header: HttpHeader).asScala).toVector).asJava
}

object EarlyHintsSender {

  /**
   * Returns a sender for a request received by an HTTP/2 server with Early Hints explicitly enabled.
   * An HTTP/1.1 request or a server without Early Hints enabled has no sender.
   *
   * @since 2.1.0
   */
  def get(request: HttpRequest): Optional[EarlyHintsSender] =
    ScalaEarlyHintsSender.from(request.asScala).map(new EarlyHintsSender(_)).toJava
}
