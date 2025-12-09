/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/** Copyright 2009-2020 Lightbend Inc. <http://www.lightbend.com> */
package org.apache.pekko.http.javadsl.model.headers;

import java.util.Optional;
import org.apache.pekko.http.javadsl.model.DateTime;
import org.apache.pekko.http.scaladsl.model.headers.Retry$minusAfter;
import org.apache.pekko.http.scaladsl.model.headers.RetryAfterDuration;
import org.apache.pekko.http.scaladsl.model.headers.RetryAfterDateTime;
import org.apache.pekko.util.OptionalUtil;
/**
 * Model for the `Retry-After` header. Specification:
 * //https://tools.ietf.org/html/rfc7231#section-7.1.3
 */
public abstract class RetryAfter extends org.apache.pekko.http.scaladsl.model.HttpHeader {

  protected abstract scala.Option<Long> delaySeconds();

  protected abstract scala.Option<org.apache.pekko.http.scaladsl.model.DateTime> dateTime();

  /** number of seconds for the retry attempt, if available */
  public Optional<Long> getDelaySeconds() {
    return OptionalUtil.convertOption(delaySeconds());
  }

  /** the date for the retry attempt, if available */
  public Optional<DateTime> getDateTime() {
    return OptionalUtil.convertOption(dateTime());
  }

  public static RetryAfter create(Long delaySeconds) {
    return new Retry$minusAfter(new RetryAfterDuration(delaySeconds));
  }

  public static RetryAfter create(DateTime dateTime) {
    return new Retry$minusAfter(
        new RetryAfterDateTime((org.apache.pekko.http.scaladsl.model.DateTime) dateTime));
  }
}
