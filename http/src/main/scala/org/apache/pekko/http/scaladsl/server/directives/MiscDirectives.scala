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
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.model.headers._
import pekko.http.scaladsl.server.RequestEntityExpectedRejection
import pekko.http.scaladsl.server.directives.BasicDirectives._

/**
 * @groupname misc Miscellaneous directives
 * @groupprio misc 140
 */
trait MiscDirectives {
  import RouteDirectives._

  /**
   * Checks the given condition before running its inner route.
   * If the condition fails the route is rejected with a [[ValidationRejection]].
   *
   * @group misc
   */
  def validate(check: => Boolean, errorMsg: String): Directive0 =
    Directive { inner => if (check) inner(()) else reject(ValidationRejection(errorMsg)) }

  /**
   * Extracts the client's IP from either the X-Forwarded-For, Remote-Address, X-Real-IP header
   * or [[pekko.http.scaladsl.model.AttributeKeys.remoteAddress]] attribute
   * (in that order of priority).
   *
   * Note that the headers are under the control of the client unless a trusted proxy in front of this server
   * overwrites them. Use [[extractDirectClientIP]] where the address must not be chosen by the client, for example
   * for access control or rate limiting.
   *
   * @group misc
   */
  def extractClientIP: Directive1[RemoteAddress] = MiscDirectives._extractClientIP

  /**
   * Extracts the client's IP from the [[pekko.http.scaladsl.model.AttributeKeys.remoteAddress]] attribute alone, that
   * is the address of the peer of the connection the request arrived on. Forwarding headers are ignored, so the
   * address cannot be chosen by the client, but it is the address of the last proxy rather than of the client itself
   * when the request was forwarded.
   *
   * Requires the `pekko.http.server.remote-address-attribute` setting to be `on` and extracts
   * [[pekko.http.scaladsl.model.RemoteAddress.Unknown]] otherwise.
   *
   * @since 2.0.0
   * @group misc
   */
  def extractDirectClientIP: Directive1[RemoteAddress] = MiscDirectives._extractDirectClientIP

  /**
   * Rejects if the request entity is non-empty.
   *
   * @group misc
   */
  def requestEntityEmpty: Directive0 = MiscDirectives._requestEntityEmpty

  /**
   * Rejects with a [[RequestEntityExpectedRejection]] if the request entity is empty.
   * Non-empty requests are passed on unchanged to the inner route.
   *
   * @group misc
   */
  def requestEntityPresent: Directive0 = MiscDirectives._requestEntityPresent

  /**
   * Converts responses with an empty entity into (empty) rejections.
   * This way you can, for example, have the marshalling of a ''None'' option
   * be treated as if the request could not be matched.
   *
   * @group misc
   */
  def rejectEmptyResponse: Directive0 = MiscDirectives._rejectEmptyResponse

  /**
   * Inspects the request's `Accept-Language` header and determines,
   * which of the given language alternatives is preferred by the client.
   * (See http://tools.ietf.org/html/rfc7231#section-5.3.5 for more details on the
   * negotiation logic.)
   * If there are several best language alternatives that the client
   * has equal preference for (even if this preference is zero!)
   * the order of the arguments is used as a tie breaker (First one wins).
   *
   * @group misc
   */
  def selectPreferredLanguage(first: Language, more: Language*): Directive1[Language] =
    BasicDirectives.extractRequest.map { request =>
      LanguageNegotiator(request.headers).pickLanguage(first :: List(more: _*)).getOrElse(first)
    }

  /**
   * Fails the stream with [[pekko.http.scaladsl.model.EntityStreamSizeException]] if its request entity size exceeds
   * given limit. Limit given as parameter overrides limit configured with `pekko.http.parsing.max-content-length`.
   *
   * Beware that request entity size check is executed when entity is consumed.
   *
   * @group misc
   */
  def withSizeLimit(maxBytes: Long): Directive0 =
    mapRequestContext(_.mapRequest(_.mapEntity(_.withSizeLimit(maxBytes))))

  /**
   * Disables the size limit (configured by `pekko.http.parsing.max-content-length` by default) checking on the incoming
   * `HttpRequest` entity.
   * Can be useful when handling arbitrarily large data uploads in specific parts of your routes.
   *
   * @note  Usage of `withoutSizeLimit` is not recommended as it turns off the too large payload protection. Therefore,
   *        we highly encourage using `withSizeLimit` instead, providing it with a value high enough to successfully
   *        handle the route in need of big entities.
   *
   * @group misc
   */
  def withoutSizeLimit: Directive0 = MiscDirectives._withoutSizeLimit
}

object MiscDirectives extends MiscDirectives {
  import BasicDirectives._
  import HeaderDirectives._
  import RouteDirectives._
  import RouteResult._

  private val _extractDirectClientIP: Directive1[RemoteAddress] =
    extractRequest.map { request =>
      request.attribute(AttributeKeys.remoteAddress).getOrElse(RemoteAddress.Unknown)
    }

  private val _extractClientIP: Directive1[RemoteAddress] =
    headerValuePF { case `X-Forwarded-For`(Seq(address, _*)) => address } |
    headerValuePF { case `X-Real-Ip`(address) => address } |
    _extractDirectClientIP

  private val _requestEntityEmpty: Directive0 =
    extract(_.request.entity.isKnownEmpty).flatMap(if (_) pass else reject)

  private val _requestEntityPresent: Directive0 =
    extract(_.request.entity.isKnownEmpty).flatMap(if (_) reject(RequestEntityExpectedRejection) else pass)

  private val _rejectEmptyResponse: Directive0 =
    mapRouteResult {
      case Complete(response) if response.entity.isKnownEmpty => Rejected(Nil)
      case x                                                  => x
    }

  private val _withoutSizeLimit: Directive0 =
    mapRequestContext(_.mapRequest(_.mapEntity(_.withoutSizeLimit)))
}
