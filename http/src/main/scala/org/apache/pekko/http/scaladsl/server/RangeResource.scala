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

package org.apache.pekko.http.scaladsl.server

import scala.concurrent.Future

import org.apache.pekko
import pekko.http.RangeResponsePolicy
import pekko.http.impl.server.RangeResourceSource
import pekko.http.impl.util.DefaultNoLogging
import pekko.http.scaladsl.model.{ ContentType, DateTime, HttpHeader, HttpMethod, HttpResponse }
import pekko.http.scaladsl.model.headers.{ EntityTag, Range }
import pekko.stream.scaladsl.Source
import pekko.util.ByteString

/**
 * Describes a selected representation whose data can be read by position. The content length and validators
 * describe the same, already selected bytes. All reads during one response must use the same representation version.
 *
 * Construct this descriptor after the application's resource lookup. It does not acquire a reader until the
 * response body is pulled. The callbacks must return without blocking; use asynchronous sources for remote or
 * blocking I/O rather than doing the I/O directly in these callbacks.
 */
final class RangeResource private (
    val contentType: ContentType,
    val contentLength: Long,
    val entityTag: Option[EntityTag],
    val lastModified: Option[DateTime],
    private[http] val openReader: () => Future[RangeResource.Reader]) {

  def withEntityTag(value: EntityTag): RangeResource = {
    require(value ne null, "entity tag must not be null")
    new RangeResource(contentType, contentLength, Some(value), lastModified, openReader)
  }

  def withLastModified(value: DateTime): RangeResource = {
    require(value ne null, "last-modified timestamp must not be null")
    new RangeResource(contentType, contentLength, entityTag, Some(value), openReader)
  }

  /**
   * Build a GET or HEAD response without a Pekko HTTP route using the default range policy. The caller selects
   * the representation and evaluates conditional request headers first. A remaining If-Range is treated as
   * unresolved and prevents applying Range. The caller need only supply Range and any remaining If-Range.
   *
   * Pass the original method (not HEAD rewritten to GET). The HEAD response model describes the full
   * representation. Use its metadata without consuming its entity. Constructing a response does not open the reader.
   */
  def responseFor(method: HttpMethod, requestHeaders: Seq[HttpHeader]): Option[HttpResponse] =
    responseFor(method, requestHeaders, RangeResponsePolicy.create())

  /** Like `responseFor` with an explicit policy. Returns None for methods other than GET and HEAD. */
  def responseFor(
      method: HttpMethod,
      requestHeaders: Seq[HttpHeader],
      policy: RangeResponsePolicy): Option[HttpResponse] = {
    val range =
      if (requestHeaders.exists(_.is("if-range"))) None
      else requestHeaders.collectFirst { case range: Range => range }
    RangeResourceSource.plan(this, method, range, policy, DefaultNoLogging).map(RangeResourceSource.toResponse)
  }
}

object RangeResource {

  /**
   * Create a known-length representation. The asynchronous factory opens at most one reader per demanded body
   * materialization. If an open succeeds after the body stage has stopped, its reader is closed without being read.
   * A failed or still-pending factory is responsible for its own acquired resources until it supplies a reader.
   */
  def apply(contentType: ContentType, contentLength: Long)(openReader: () => Future[Reader]): RangeResource = {
    require(contentType ne null, "content type must not be null")
    require(contentLength >= 0, "content length must be non-negative")
    require(openReader ne null, "reader factory must not be null")
    new RangeResource(contentType, contentLength, None, None, openReader)
  }

  /**
   * A session for one materialized response. Sources are consumed serially and the active source is cancelled if
   * the response stops. Implementations must handle cancellation and release any I/O not owned by the source.
   */
  trait Reader {

    /**
     * Return a source for exactly `length` bytes at the given zero-based `offset`. A short or oversized stream
     * fails the response instead of returning an incomplete or incorrectly framed part.
     */
    def read(offset: Long, length: Long): Source[ByteString, Any]

    /**
     * Notify the session that the response is done. This hook is invoked once for each opened reader, including
     * cancellation and failure. It must not block or wait for in-flight reads; initiate any asynchronous cleanup
     * here. An implementation with no session-level resources can use the default no-op.
     */
    def close(): Unit = ()
  }
}
