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

import java.time.{ Duration => JDuration }
import java.util.function.{ Function => JFunction, Supplier }

import scala.jdk.DurationConverters._

import org.apache.pekko
import pekko.http.impl.util.JavaDurationConverter
import pekko.http.impl.util.JavaMapping.Implicits._
import pekko.http.javadsl.model.{ HttpRequest, HttpResponse }
import pekko.http.javadsl.server.Route
import pekko.http.scaladsl.server.{ Directives => D }

abstract class TimeoutDirectives extends WebSocketDirectives {

  /**
   * Java API
   * <p>
   * In 2.0.0, the input function changed from expecting `scala.concurrent.duration.Duration`
   * as input to `java.time.Duration`.
   * </p>
   */
  def extractRequestTimeout(inner: JFunction[java.time.Duration, Route]): RouteAdapter = RouteAdapter {
    D.extractRequestTimeout { timeout =>
      inner.apply(JavaDurationConverter.toJava(timeout)).delegate
    }
  }

  /**
   * Tries to set a new request timeout and handler (if provided) at the same time.
   *
   * Due to the inherent raciness it is not guaranteed that the update will be applied before
   * the previously set timeout has expired!
   * @deprecated As of 1.3.0, use the overloaded method taking a `java.time.Duration` instead.
   */
  @Deprecated
  @deprecated("use the overloaded method taking a `java.time.Duration` instead.", "1.3.0")
  def withRequestTimeout(timeout: scala.concurrent.duration.Duration, inner: Supplier[Route]): RouteAdapter =
    RouteAdapter {
      D.withRequestTimeout(timeout) { inner.get.delegate }
    }

  /**
   * Tries to set a new request timeout and handler (if provided) at the same time.
   *
   * Due to the inherent raciness it is not guaranteed that the update will be applied before
   * the previously set timeout has expired!
   * @since 1.3.0
   */
  def withRequestTimeout(timeout: JDuration, inner: Supplier[Route]): RouteAdapter =
    RouteAdapter {
      D.withRequestTimeout(timeout.toScala) { inner.get.delegate }
    }

  /**
   * Tries to set a new request timeout and handler (if provided) at the same time.
   *
   * Due to the inherent raciness it is not guaranteed that the update will be applied before
   * the previously set timeout has expired!
   * @deprecated As of 1.3.0, use the overloaded method taking a `java.time.Duration` instead.
   */
  @Deprecated
  @deprecated("use the overloaded method taking a `java.time.Duration` instead.", "1.3.0")
  def withRequestTimeout(timeout: scala.concurrent.duration.Duration,
      timeoutHandler: JFunction[HttpRequest, HttpResponse],
      inner: Supplier[Route]): RouteAdapter = RouteAdapter {
    D.withRequestTimeout(timeout, in => timeoutHandler(in.asJava).asScala) { inner.get.delegate }
  }

  /**
   * Tries to set a new request timeout and handler (if provided) at the same time.
   *
   * Due to the inherent raciness it is not guaranteed that the update will be applied before
   * the previously set timeout has expired!
   * @since 1.3.0
   */
  def withRequestTimeout(timeout: JDuration,
      timeoutHandler: JFunction[HttpRequest, HttpResponse],
      inner: Supplier[Route]): RouteAdapter = RouteAdapter {
    D.withRequestTimeout(timeout.toScala, in => timeoutHandler(in.asJava).asScala) { inner.get.delegate }
  }

  def withoutRequestTimeout(inner: Supplier[Route]): RouteAdapter = RouteAdapter {
    D.withoutRequestTimeout { inner.get.delegate }
  }

  /**
   * Tries to set a new request timeout handler, which produces the timeout response for a
   * given request. Note that the handler must produce the response synchronously and shouldn't block!
   *
   * Due to the inherent raciness it is not guaranteed that the update will be applied before
   * the previously set timeout has expired!
   */
  def withRequestTimeoutResponse(
      timeoutHandler: JFunction[HttpRequest, HttpResponse], inner: Supplier[Route]): RouteAdapter = RouteAdapter {
    D.withRequestTimeoutResponse(in => timeoutHandler(in.asJava).asScala) { inner.get.delegate }
  }

}
