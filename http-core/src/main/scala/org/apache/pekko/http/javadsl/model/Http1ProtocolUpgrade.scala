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

import scala.jdk.OptionConverters._

import org.apache.pekko
import pekko.http.impl.util.JavaMapping.Implicits._
import pekko.http.scaladsl.{ model => sm }
import pekko.japi.function.Function
import pekko.stream.javadsl.Flow
import pekko.util.ByteString

/**
 * A request-bound offer to select an explicitly configured HTTP/1.1 protocol.
 *
 * @since 2.1.0
 */
final class Http1ProtocolUpgrade private (delegate: sm.Http1ProtocolUpgrade) {

  /**
   * Accept this offer using a flow for the selected protocol. The factory receives the original
   * completed HTTP request; the selected protocol must answer it before handling later messages.
   * Return the response from the application handler. Appending ordinary response headers is allowed.
   * Decline by returning an ordinary HTTP response instead.
   */
  def accept(handler: Function[HttpRequest, Flow[ByteString, ByteString, ?]]): HttpResponse =
    delegate.accept(request => handler.apply(request).asScala)
}

object Http1ProtocolUpgrade {

  /**
   * Finds an eligible offer on a binding that explicitly enabled its protocol.
   *
   * @since 2.1.0
   */
  def fromRequest(request: HttpRequest): Optional[Http1ProtocolUpgrade] =
    sm.Http1ProtocolUpgrade.fromRequest(request.asScala).map(new Http1ProtocolUpgrade(_)).toJava
}
