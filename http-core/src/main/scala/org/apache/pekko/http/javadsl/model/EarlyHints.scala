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

import org.apache.pekko
import pekko.annotation.DoNotInherit
import pekko.http.scaladsl.{ model => sm }
import pekko.stream.javadsl.Source

/**
 * The ordered header fields of one HTTP/2 103 Early Hints response.
 *
 * @since 2.1.0
 */
@DoNotInherit
trait EarlyHints {
  def getHeaders: java.util.List[HttpHeader]
}

/**
 * An opt-in destination for HTTP/2 client Early Hints. Obtain it by materializing
 * the source from [[EarlyHintsObserver.createSource]] rather than implementing this type.
 *
 * @since 2.1.0
 */
@DoNotInherit
trait EarlyHintsObserver {

  /**
   * Complete this observer's source and drain queued hints. Calling this after completion or
   * cancellation is a no-op. This does not end or cancel the request.
   */
  def complete(): Unit
}

/** @since 2.1.0 */
object EarlyHintsObserver {

  /** Attach an observer to an outgoing request as a local attribute, not as an HTTP field. */
  def getAttributeKey: AttributeKey[EarlyHintsObserver] = sm.EarlyHintsObserver.attributeKey

  /**
   * Materializes a bounded observer. The client offers hints without waiting for demand and drops a new hint
   * if the buffer is full or the observer is closed. Use a separate observer per request for correlation.
   * The application owns the source and can complete or cancel it independently of the request.
   */
  def createSource(bufferSize: Int): Source[EarlyHints, EarlyHintsObserver] = {
    val source: pekko.stream.scaladsl.Source[EarlyHints, EarlyHintsObserver] =
      sm.EarlyHintsObserver.source(bufferSize)
    source.asJava
  }
}
