/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.scaladsl.server
package directives

import org.apache.pekko
import pekko.http.scaladsl.model._
import pekko.http.ccompat._
import scala.collection.immutable

/**
 * @groupname response Response directives
 * @groupprio response 190
 */
trait RespondWithDirectives {
  import BasicDirectives._

  /**
   * Unconditionally adds the given response header to all HTTP responses of its inner Route.
   *
   * @group response
   */
  def respondWithHeader(responseHeader: HttpHeader): Directive0 =
    respondWithHeaders(immutable.Seq(responseHeader))

  /**
   * Adds the given response header to all HTTP responses of its inner Route,
   * if the response from the inner Route doesn't already contain a header with the same name.
   *
   * @group response
   */
  def respondWithDefaultHeader(responseHeader: HttpHeader): Directive0 = respondWithDefaultHeaders(responseHeader)

  /**
   * Unconditionally adds the given response headers to all HTTP responses of its inner Route.
   *
   * @group response
   */
  def respondWithHeaders(responseHeaders: HttpHeader*): Directive0 =
    respondWithHeaders(responseHeaders.toList)

  @since213
  def respondWithHeaders(firstHeader: HttpHeader, otherHeaders: HttpHeader*): Directive0 =
    respondWithHeaders(firstHeader +: otherHeaders.toList)

  /**
   * Unconditionally adds the given response headers to all HTTP responses of its inner Route.
   *
   * @group response
   */
  def respondWithHeaders(responseHeaders: immutable.Seq[HttpHeader]): Directive0 =
    mapResponseHeaders(responseHeaders.toList ++ _)

  /**
   * Adds the given response headers to all HTTP responses of its inner Route,
   * if a header already exists it is not added again.
   *
   * @group response
   */
  def respondWithDefaultHeaders(responseHeaders: HttpHeader*): Directive0 =
    respondWithDefaultHeaders(responseHeaders.toList)

  /**
   * Adds the given response headers to all HTTP responses of its inner Route,
   * if a header already exists it is not added again.
   *
   * @group response
   */
  @since213
  def respondWithDefaultHeaders(firstHeader: HttpHeader, otherHeaders: HttpHeader*): Directive0 =
    respondWithDefaultHeaders(firstHeader +: otherHeaders.toList)

  /**
   * Adds the given response headers to all HTTP responses of its inner Route,
   * if a header already exists it is not added again.
   *
   * @group response
   */
  def respondWithDefaultHeaders(responseHeaders: immutable.Seq[HttpHeader]): Directive0 =
    mapResponse(_.withDefaultHeaders(responseHeaders))
}

object RespondWithDirectives extends RespondWithDirectives
