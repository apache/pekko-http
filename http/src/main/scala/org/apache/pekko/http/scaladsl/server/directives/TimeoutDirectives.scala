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

package org.apache.pekko.http.scaladsl.server.directives

import scala.concurrent.duration.Duration

import org.apache.pekko
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.model.headers.`Timeout-Access`
import pekko.http.scaladsl.server.{ Directive, Directive0, Directive1 }

/**
 * @groupname timeout Timeout directives
 * @groupprio timeout 160
 */
trait TimeoutDirectives {

  /**
   * Return the currently set request timeout.
   *
   * Note that this may be changed in inner directives.
   *
   * @group timeout
   */
  def extractRequestTimeout: Directive1[Duration] = Directive { inner => ctx =>
    val timeout = ctx.request.header[`Timeout-Access`] match {
      case Some(t) => t.timeoutAccess.timeout
      case _ =>
        ctx.log.warning("extractRequestTimeout was used in route however no request-timeout is set!")
        Duration.Inf
    }
    inner(Tuple1(timeout))(ctx)
  }

  /**
   * @group timeout
   */
  def withoutRequestTimeout: Directive0 =
    withRequestTimeout(Duration.Inf)

  /**
   * Tries to set a new request timeout and handler (if provided) at the same time.
   *
   * Due to the inherent raciness it is not guaranteed that the update will be applied before
   * the previously set timeout has expired!
   *
   * @group timeout
   */
  def withRequestTimeout(timeout: Duration): Directive0 =
    withRequestTimeout(timeout, None)

  /**
   * Tries to set a new request timeout and handler (if provided) at the same time.
   *
   * Due to the inherent raciness it is not guaranteed that the update will be applied before
   * the previously set timeout has expired!
   *
   * @param handler optional custom "timeout response" function. If left None, the default timeout HttpResponse will be used.
   *
   * @group timeout
   */
  def withRequestTimeout(timeout: Duration, handler: HttpRequest => HttpResponse): Directive0 =
    withRequestTimeout(timeout, Some(handler))

  /**
   * Tries to set a new request timeout and handler (if provided) at the same time.
   *
   * Due to the inherent raciness it is not guaranteed that the update will be applied before
   * the previously set timeout has expired!
   *
   * @param handler optional custom "timeout response" function. If left None, the default timeout HttpResponse will be used.
   *
   * @group timeout
   */
  def withRequestTimeout(timeout: Duration, handler: Option[HttpRequest => HttpResponse]): Directive0 =
    Directive { inner => ctx =>
      ctx.request.header[`Timeout-Access`] match {
        case Some(t) =>
          handler match {
            case Some(h) => t.timeoutAccess.update(timeout, h)
            case _       => t.timeoutAccess.updateTimeout(timeout)
          }
        case _ => ctx.log.warning("withRequestTimeout was used in route however no request-timeout is set!")
      }
      inner(())(ctx)
    }

  /**
   * Tries to set a new request timeout handler, which produces the timeout response for a
   * given request. Note that the handler must produce the response synchronously and shouldn't block!
   *
   * Due to the inherent raciness it is not guaranteed that the update will be applied before
   * the previously set timeout has expired!
   *
   * @group timeout
   */
  def withRequestTimeoutResponse(handler: HttpRequest => HttpResponse): Directive0 =
    Directive { inner => ctx =>
      ctx.request.header[`Timeout-Access`] match {
        case Some(t) => t.timeoutAccess.updateHandler(handler)
        case _       => ctx.log.warning("withRequestTimeoutResponse was used in route however no request-timeout is set!")
      }
      inner(())(ctx)
    }

}

object TimeoutDirectives extends TimeoutDirectives
