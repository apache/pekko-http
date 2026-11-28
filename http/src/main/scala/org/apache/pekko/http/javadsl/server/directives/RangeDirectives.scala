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

import java.util.function.Supplier

import org.apache.pekko.http.javadsl.server.RangeResource
import org.apache.pekko.http.scaladsl.server.{ Directives => D }

abstract class RangeDirectives extends PathDirectives {

  /**
   * Serves a known-length representation through its position-based reader. Pekko HTTP selects the ranges and
   * supplies the response headers and multipart format. The reader is acquired only on response-body demand.
   * To apply Range only to GET, keep `pekko.http.server.transparent-head-requests` off (the default); otherwise
   * the server can present HEAD to this route as GET.
   */
  def getFromRangeResource(resource: RangeResource): Route =
    RouteAdapter(D.getFromRangeResource(resource.delegate))

  /**
   * Answers GET requests with an `Accept-Ranges: bytes` header and converts HttpResponses coming back from its inner
   * route into partial responses if the initial request contained a valid `Range` request header. The requested
   * byte-ranges may be coalesced.
   * This directive is transparent to non-GET requests
   * Rejects requests with unsatisfiable ranges `UnsatisfiableRangeRejection`.
   * Rejects requests with too many expected ranges.
   *
   * Note: if you want to combine this directive with `conditional(...)` you need to put
   * it on the *inside* of the `conditional(...)` directive, i.e. `conditional(...)` must be
   * on a higher level in your route structure in order to function correctly.
   *
   * For more information, see: https://tools.ietf.org/html/rfc7233
   */
  def withRangeSupport(inner: Supplier[Route]): Route = RouteAdapter {
    D.withRangeSupport { inner.get.delegate }
  }
}
