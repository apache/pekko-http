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

package org.apache.pekko.http.javadsl.server

import java.util.Optional
import java.util.concurrent.CompletionStage
import java.util.function.Supplier

import scala.concurrent.ExecutionContext.parasitic
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import scala.jdk.javaapi.FutureConverters

import org.apache.pekko
import pekko.http.RangeResponsePolicy
import pekko.http.impl.util.JavaMapping.Implicits._
import pekko.http.javadsl.model.{ ContentType, DateTime, HttpHeader, HttpMethod, HttpResponse }
import pekko.http.javadsl.model.headers.EntityTag
import pekko.http.scaladsl.{ server => sd }
import pekko.stream.javadsl.Source
import pekko.util.ByteString

/**
 * A known-length representation with position-based reads. The declared length and validators must describe the
 * same bytes returned by every read of a session. Reader factories and callbacks must return without blocking.
 */
final class RangeResource private (private[http] val delegate: sd.RangeResource) {
  def getContentType: ContentType = delegate.contentType
  def getContentLength: Long = delegate.contentLength
  def getEntityTag: Optional[EntityTag] = delegate.entityTag.map(tag => tag: EntityTag).toJava
  def getLastModified: Optional[DateTime] = delegate.lastModified.map(date => date: DateTime).toJava

  def withEntityTag(value: EntityTag): RangeResource =
    new RangeResource(delegate.withEntityTag(value.asScala))

  def withLastModified(value: DateTime): RangeResource =
    new RangeResource(delegate.withLastModified(value.asScala))

  /**
   * Build a GET or HEAD response without a route and with the default range policy. The caller selects the
   * representation and evaluates preconditions; a remaining If-Range prevents applying Range. The caller need
   * only supply Range and any remaining If-Range. Pass the original method (not HEAD rewritten to GET) and do not
   * consume a HEAD response's entity.
   */
  def responseFor(method: HttpMethod, requestHeaders: java.lang.Iterable[HttpHeader]): Optional[HttpResponse] =
    responseFor(method, requestHeaders, RangeResponsePolicy.create())

  /** Like `responseFor` with an explicit policy. Returns an empty Optional for other methods. */
  def responseFor(
      method: HttpMethod,
      requestHeaders: java.lang.Iterable[HttpHeader],
      policy: RangeResponsePolicy): Optional[HttpResponse] =
    delegate.responseFor(method.asScala, requestHeaders.asScala.iterator.map(_.asScala).toList, policy)
      .map(response => response: HttpResponse).toJava
}

object RangeResource {

  /**
   * The factory is called only when the response body is pulled. If an asynchronous open succeeds after the body
   * stage has stopped, its reader is closed without being read. The factory owns acquired resources until then.
   */
  def create(
      contentType: ContentType,
      contentLength: Long,
      openReader: Supplier[? <: CompletionStage[? <: Reader]]): RangeResource = {
    require(openReader ne null, "reader factory must not be null")
    new RangeResource(sd.RangeResource(contentType.asScala, contentLength) { () =>
      val pending: CompletionStage[? <: Reader] = openReader.get()
      FutureConverters.asScala(pending).map { reader =>
        require(reader ne null, "reader factory must not return a null reader")
        new sd.RangeResource.Reader {
          override def read(offset: Long, length: Long): pekko.stream.scaladsl.Source[ByteString, Any] =
            reader.read(offset, length).asScala
          override def close(): Unit = reader.close()
        }
      }(parasitic)
    })
  }

  /**
   * An asynchronous, backpressured reader for one response. Reads are consumed serially; if the response stops,
   * the current stream is cancelled. Use `close()` to initiate release of any resources owned by the whole session.
   */
  trait Reader {

    /** Return a source that supplies exactly `length` bytes at the given zero-based offset. */
    def read(offset: Long, length: Long): Source[ByteString, ?]

    /**
     * Called once after completion, failure or cancellation. Initiate cleanup without blocking or waiting for
     * in-flight reads. A provider with no session-level resources can use the default no-op.
     */
    def close(): Unit = ()
  }
}
