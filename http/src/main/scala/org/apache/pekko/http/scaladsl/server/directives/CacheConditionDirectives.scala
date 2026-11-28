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

package org.apache.pekko.http.scaladsl.server
package directives

import org.apache.pekko
import pekko.http.impl.engine.http2.Http2
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.model.DateTime
import pekko.http.scaladsl.model.HttpMethods._
import pekko.http.scaladsl.model.StatusCodes._
import pekko.http.scaladsl.model.headers._
import pekko.http.scaladsl.model.headers.EntityTag._

/**
 * @groupname cachecondition Cache condition directives
 * @groupprio cachecondition 20
 */
trait CacheConditionDirectives {
  import BasicDirectives._
  import RouteDirectives._

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
   *
   * @group cachecondition
   */
  def conditional(eTag: EntityTag): Directive0 = conditional(Some(eTag), None)

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
   *
   * @group cachecondition
   */
  def conditional(lastModified: DateTime): Directive0 = conditional(None, Some(lastModified))

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
   *
   * @group cachecondition
   */
  def conditional(eTag: EntityTag, lastModified: DateTime): Directive0 = conditional(Some(eTag), Some(lastModified))

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
   *
   * @group cachecondition
   */
  def conditional(eTag: Option[EntityTag], lastModified: Option[DateTime]): Directive0 =
    conditionalImpl(eTag, lastModified, None)

  /**
   * Like [[conditional]], but on a 304 produced for a conditional GET received directly as HTTP/2, advertise the
   * known content length of the selected representation. The octet count must match the 200 response the same
   * request would otherwise receive, after any content coding. Use [[conditional]] if the size is unknown.
   * The 304 has no content, and HTTP/1.x behavior, including for the initial h2c upgrade request, is unchanged.
   *
   * @group cachecondition
   * @since 2.1.0
   */
  def conditionalWithRepresentationLength(
      eTag: Option[EntityTag],
      lastModified: Option[DateTime],
      representationLength: Long): Directive0 = {
    require(representationLength >= 0L, "representationLength must be non-negative")
    conditionalImpl(eTag, lastModified, Some(representationLength))
  }

  private def conditionalImpl(
      eTag: Option[EntityTag],
      lastModified: Option[DateTime],
      representationLength: Option[Long]): Directive0 = {
    def addResponseHeaders: Directive0 =
      mapResponse(_.withDefaultHeaders(eTag.map(ETag(_)).toList ++ lastModified.map(`Last-Modified`(_)).toList))

    // TODO: also handle Cache-Control and Vary
    def complete304(request: HttpRequest): Route = {
      val response = HttpResponse(NotModified)
      val withLength = representationLength match {
        case Some(length)
            if request.method == GET && request.protocol == HttpProtocols.`HTTP/2.0` &&
            request.attribute(Http2.streamId).isDefined =>
          // The HTTP/2 stream stage still has to verify the original method.
          response.addAttribute(Http2.notModifiedRepresentationLength, length)
        case _ => response
      }
      addResponseHeaders(complete(withLength))
    }
    def complete412(): Route = _.complete(PreconditionFailed)

    extractRequest.flatMap { request =>
      import request._
      mapInnerRoute { route =>
        def innerRouteWithRangeHeaderFilteredOut: Route =
          (mapRequest(_.mapHeaders(_.filterNot(_.isInstanceOf[Range]))) &
          addResponseHeaders)(route)

        def isGetOrHead = method == HEAD || method == GET
        def unmodified(ifModifiedSince: DateTime) =
          lastModified.get <= ifModifiedSince && ifModifiedSince.clicks < System.currentTimeMillis()

        def step1(): Route =
          header[`If-Match`] match {
            case Some(`If-Match`(im)) if eTag.isDefined =>
              if (matchesRange(eTag.get, im, weakComparison = false)) step3() else complete412()
            case _ => step2()
          }
        def step2(): Route =
          header[`If-Unmodified-Since`] match {
            case Some(`If-Unmodified-Since`(ius)) if lastModified.isDefined && !unmodified(ius) => complete412()
            case _                                                                              => step3()
          }
        def step3(): Route =
          header[`If-None-Match`] match {
            case Some(`If-None-Match`(inm)) if eTag.isDefined =>
              if (!matchesRange(eTag.get, inm, weakComparison = true)) step5()
              else if (isGetOrHead) complete304(request)
              else complete412()
            case _ => step4()
          }
        def step4(): Route =
          if (isGetOrHead) {
            header[`If-Modified-Since`] match {
              case Some(`If-Modified-Since`(ims)) if lastModified.isDefined && unmodified(ims) => complete304(request)
              case _                                                                           => step5()
            }
          } else step5()
        def step5(): Route =
          if (method == GET && header[Range].isDefined)
            header[`If-Range`] match {
              case Some(`If-Range`(Left(tag))) if eTag.isDefined && !matches(eTag.get, tag, weakComparison = false) =>
                innerRouteWithRangeHeaderFilteredOut
              case Some(`If-Range`(Right(ims))) if lastModified.isDefined && !unmodified(ims) =>
                innerRouteWithRangeHeaderFilteredOut
              case _ => step6()
            }
          else step6()
        def step6(): Route = addResponseHeaders(route)

        step1()
      }
    }
  }
}

object CacheConditionDirectives extends CacheConditionDirectives
