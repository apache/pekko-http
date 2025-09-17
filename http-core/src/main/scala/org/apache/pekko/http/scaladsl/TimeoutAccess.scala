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

package org.apache.pekko.http.scaladsl

import org.apache.pekko
import pekko.annotation.DoNotInherit
import pekko.http.impl.util.JavaDurationConverter
import pekko.http.scaladsl.model.{ HttpRequest, HttpResponse }

import scala.concurrent.duration.Duration

/**
 * Enables programmatic access to the server-side request timeout logic.
 *
 * Not for user extension.
 */
@DoNotInherit
trait TimeoutAccess extends pekko.http.javadsl.TimeoutAccess {

  /**
   * Returns the currently set timeout.
   * The timeout period is measured as of the point in time that the end of the request has been received,
   * which may be in the past or in the future!
   *
   * Due to the inherent raciness it is not guaranteed that the returned timeout was applied before
   * the previously set timeout has expired!
   */
  def timeout: Duration

  override def getTimeout: java.time.Duration = JavaDurationConverter.toJava(timeout)

  /**
   * Tries to set a new timeout.
   * The timeout period is measured as of the point in time that the end of the request has been received,
   * which may be in the past or in the future!
   * Use `Duration.Inf` to completely disable request timeout checking for this request.
   *
   * Due to the inherent raciness it is not guaranteed that the update will be applied before
   * the previously set timeout has expired!
   */
  def updateTimeout(timeout: Duration): Unit

  /**
   * Tries to set a new timeout handler, which produces the timeout response for a
   * given request. Note that the handler must produce the response synchronously and shouldn't block!
   *
   * Due to the inherent raciness it is not guaranteed that the update will be applied before
   * the previously set timeout has expired!
   */
  def updateHandler(handler: HttpRequest => HttpResponse): Unit

  /**
   * Tries to set a new timeout and handler at the same time.
   *
   * Due to the inherent raciness it is not guaranteed that the update will be applied before
   * the previously set timeout has expired!
   */
  def update(timeout: Duration, handler: HttpRequest => HttpResponse): Unit
}
