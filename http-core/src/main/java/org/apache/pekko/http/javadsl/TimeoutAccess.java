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

package org.apache.pekko.http.javadsl;

import org.apache.pekko.annotation.DoNotInherit;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.japi.Function;

/**
 * Enables programmatic access to the server-side request timeout logic.
 *
 * <p>Not for user extension.
 */
@DoNotInherit
public interface TimeoutAccess {

  /**
   * Returns the currently set timeout. The timeout period is measured as of the point in time that
   * the end of the request has been received, which may be in the past or in the future!
   *
   * <p>Due to the inherent raciness it is not guaranteed that the returned timeout was applied
   * before the previously set timeout has expired!
   */
  scala.concurrent.duration.Duration getTimeout();

  /**
   * Tries to set a new timeout. The timeout period is measured as of the point in time that the end
   * of the request has been received, which may be in the past or in the future! Use
   * `scala.concurrent.duration.Duration.Inf` to completely disable request timeout checking for
   * this request.
   *
   * <p>Due to the inherent raciness it is not guaranteed that the update will be applied before the
   * previously set timeout has expired!
   */
  void updateTimeout(scala.concurrent.duration.Duration timeout);

  /**
   * Tries to set a new timeout. The timeout period is measured as of the point in time that the end
   * of the request has been received, which may be in the past or in the future! Use a
   * `java.time.Duration` with a long duration to disable request timeout checking for this request
   * (e.g. `java.time.temporal.ChronoUnit.FOREVER.getDuration()`).
   *
   * <p>Due to the inherent raciness it is not guaranteed that the update will be applied before the
   * previously set timeout has expired!
   *
   * @since 1.3.0
   */
  void updateTimeout(java.time.Duration timeout);

  /**
   * Tries to set a new timeout handler, which produces the timeout response for a given request.
   * Note that the handler must produce the response synchronously and shouldn't block!
   *
   * <p>Due to the inherent raciness it is not guaranteed that the update will be applied before the
   * previously set timeout has expired!
   */
  void updateHandler(Function<HttpRequest, HttpResponse> handler);

  /**
   * Tries to set a new timeout and handler at the same time.
   *
   * <p>Due to the inherent raciness it is not guaranteed that the update will be applied before the
   * previously set timeout has expired!
   */
  void update(
      scala.concurrent.duration.Duration timeout, Function<HttpRequest, HttpResponse> handler);

  /**
   * Tries to set a new timeout and handler at the same time.
   *
   * <p>Due to the inherent raciness it is not guaranteed that the update will be applied before the
   * previously set timeout has expired!
   *
   * @since 1.3.0
   */
  void update(java.time.Duration timeout, Function<HttpRequest, HttpResponse> handler);
}
