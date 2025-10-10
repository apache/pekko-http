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

package org.apache.pekko.http.javadsl.server.directives

import java.lang.{ Iterable => JIterable }
import java.util.function.Supplier

import org.apache.pekko
import pekko.http.javadsl.model.HttpHeader
import pekko.http.javadsl.server.Route
import pekko.http.scaladsl.server.{ Directives => D }

abstract class RespondWithDirectives extends RangeDirectives {
  import pekko.http.impl.util.JavaMapping.Implicits._

  /**
   * Unconditionally adds the given response header to all HTTP responses of its inner Route.
   */
  def respondWithHeader(responseHeader: HttpHeader, inner: Supplier[Route]): Route = RouteAdapter {
    D.respondWithHeader(responseHeader.asScala) { inner.get.delegate }
  }

  /**
   * Adds the given response header to all HTTP responses of its inner Route,
   * if the response from the inner Route doesn't already contain a header with the same name.
   */
  def respondWithDefaultHeader(responseHeader: HttpHeader, inner: Supplier[Route]): Route = RouteAdapter {
    D.respondWithDefaultHeader(responseHeader.asScala) { inner.get.delegate }
  }

  /**
   * Unconditionally adds the given response headers to all HTTP responses of its inner Route.
   */
  def respondWithHeaders(responseHeaders: JIterable[HttpHeader], inner: Supplier[Route]): Route = RouteAdapter {
    D.respondWithHeaders(responseHeaders.asScala) { inner.get.delegate }
  }

  /**
   * Adds the given response headers to all HTTP responses of its inner Route,
   * if a header already exists it is not added again.
   */
  def respondWithDefaultHeaders(responseHeaders: JIterable[HttpHeader], inner: Supplier[Route]): Route = RouteAdapter {
    D.respondWithDefaultHeaders(responseHeaders.asScala.toVector) { inner.get.delegate }
  }

}
