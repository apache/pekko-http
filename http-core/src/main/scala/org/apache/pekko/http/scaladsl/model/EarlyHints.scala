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

import scala.jdk.CollectionConverters._

import org.apache.pekko
import pekko.annotation.DoNotInherit
import pekko.http.javadsl.{ model => jm }
import pekko.stream.BoundedSourceQueue
import pekko.stream.scaladsl.Source

/**
 * The ordered header fields of one HTTP/2 103 Early Hints response.
 *
 * @since 2.1.0
 */
final class EarlyHints private[http] (val headers: Seq[HttpHeader]) extends jm.EarlyHints {
  override def getHeaders: java.util.List[jm.HttpHeader] = {
    val javaHeaders: Seq[jm.HttpHeader] = headers
    javaHeaders.asJava
  }
}

/**
 * An opt-in destination for HTTP/2 client Early Hints. Materialize the source to obtain an observer.
 *
 * @since 2.1.0
 */
@DoNotInherit
sealed abstract class EarlyHintsObserver extends jm.EarlyHintsObserver {
  private[http] def publish(headers: Seq[HttpHeader]): Unit
}

/** @since 2.1.0 */
object EarlyHintsObserver {

  /** Attach an observer to an outgoing request as a local attribute, not as an HTTP field. */
  val attributeKey: AttributeKey[jm.EarlyHintsObserver] = AttributeKey("http2-early-hints-observer")

  /**
   * Materializes a bounded observer. The client offers hints without waiting for demand and drops a new hint
   * if the buffer is full or the observer is closed. Use a separate observer per request for correlation.
   * The application owns the source and can complete or cancel it independently of the request.
   */
  def source(bufferSize: Int): Source[EarlyHints, EarlyHintsObserver] =
    Source.queue[EarlyHints](bufferSize).mapMaterializedValue(new QueueObserver(_))

  private final class QueueObserver(queue: BoundedSourceQueue[EarlyHints]) extends EarlyHintsObserver {
    override def complete(): Unit =
      if (!queue.isCompleted)
        try queue.complete()
        catch {
          // The consumer or another caller may close the queue between the check and completion.
          case _: IllegalStateException if queue.isCompleted => ()
        }

    override private[http] def publish(headers: Seq[HttpHeader]): Unit = {
      val _ = queue.offer(new EarlyHints(headers))
    }
  }

  // Do not trust a user implementation of the Java API (or Java subclasses of the Scala view).
  private[http] def materializedObserverOrNull(candidate: Any): EarlyHintsObserver = candidate match {
    case observer: QueueObserver => observer
    case _                       => null
  }
}
