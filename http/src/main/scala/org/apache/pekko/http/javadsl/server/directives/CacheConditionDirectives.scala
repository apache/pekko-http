/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.server
package directives

import java.util.Optional
import java.util.function.Supplier

import org.apache.pekko
import pekko.http.javadsl.model.DateTime
import pekko.http.javadsl.model.headers.EntityTag
import pekko.http.scaladsl.server.{ Directives => D }

abstract class CacheConditionDirectives extends BasicDirectives {
  import pekko.http.impl.util.JavaMapping.Implicits._

  /**
   * Wraps its inner route with support for Conditional Requests as defined
   * by http://tools.ietf.org/html/rfc7232
   *
   * In particular the algorithm defined by http://tools.ietf.org/html/rfc7232#section-6
   * is implemented by this directive.
   *
   * Note: if you want to combine this directive with `withRangeSupport(...)` you need to put
   * it on the *outside* of the `withRangeSupport(...)` directive, i.e. `withRangeSupport(...)`
   * must be on a deeper level in your route structure in order to function correctly.
   */
  def conditional(eTag: EntityTag, inner: Supplier[Route]): Route = RouteAdapter {
    D.conditional(eTag.asScala) { inner.get.delegate }
  }

  /**
   * Wraps its inner route with support for Conditional Requests as defined
   * by http://tools.ietf.org/html/rfc7232
   *
   * In particular the algorithm defined by http://tools.ietf.org/html/rfc7232#section-6
   * is implemented by this directive.
   *
   * Note: if you want to combine this directive with `withRangeSupport(...)` you need to put
   * it on the *outside* of the `withRangeSupport(...)` directive, i.e. `withRangeSupport(...)`
   * must be on a deeper level in your route structure in order to function correctly.
   */
  def conditional(lastModified: DateTime, inner: Supplier[Route]): Route = RouteAdapter {
    D.conditional(lastModified.asScala) { inner.get.delegate }
  }

  /**
   * Wraps its inner route with support for Conditional Requests as defined
   * by http://tools.ietf.org/html/rfc7232
   *
   * In particular the algorithm defined by http://tools.ietf.org/html/rfc7232#section-6
   * is implemented by this directive.
   *
   * Note: if you want to combine this directive with `withRangeSupport(...)` you need to put
   * it on the *outside* of the `withRangeSupport(...)` directive, i.e. `withRangeSupport(...)`
   * must be on a deeper level in your route structure in order to function correctly.
   */
  def conditional(eTag: EntityTag, lastModified: DateTime, inner: Supplier[Route]): Route = RouteAdapter {
    D.conditional(eTag.asScala, lastModified.asScala) { inner.get.delegate }
  }

  /**
   * Wraps its inner route with support for Conditional Requests as defined
   * by http://tools.ietf.org/html/rfc7232
   *
   * In particular the algorithm defined by http://tools.ietf.org/html/rfc7232#section-6
   * is implemented by this directive.
   *
   * Note: if you want to combine this directive with `withRangeSupport(...)` you need to put
   * it on the *outside* of the `withRangeSupport(...)` directive, i.e. `withRangeSupport(...)`
   * must be on a deeper level in your route structure in order to function correctly.
   */
  def conditional(eTag: Optional[EntityTag], lastModified: Optional[DateTime], inner: Supplier[Route]): Route =
    RouteAdapter {
      D.conditional(eTag.asScala, lastModified.asScala) { inner.get.delegate }
    }

  /**
   * Like `conditional`, but on a 304 produced for a conditional GET received directly as HTTP/2, advertise the
   * known content length of the selected representation. The octet count must match the 200 response the same
   * request would otherwise receive, after any content coding. Use `conditional` if the size is unknown.
   * The 304 has no content, and HTTP/1.x behavior, including for the initial h2c upgrade request, is unchanged.
   *
   * @since 2.1.0
   */
  def conditionalWithRepresentationLength(
      eTag: Optional[EntityTag],
      lastModified: Optional[DateTime],
      representationLength: Long,
      inner: Supplier[Route]): Route =
    RouteAdapter {
      D.conditionalWithRepresentationLength(eTag.asScala, lastModified.asScala, representationLength) {
        inner.get.delegate
      }
    }

}
