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

package org.apache.pekko.http.javadsl.model.headers;

import org.apache.pekko.http.javadsl.model.DateTime;

/**
 * Model for the `If-Unmodified-Since` header. Specification:
 * http://tools.ietf.org/html/draft-ietf-httpbis-p4-conditional-26#section-3.4
 */
public abstract class IfUnmodifiedSince extends org.apache.pekko.http.scaladsl.model.HttpHeader {
  public abstract DateTime date();

  public static IfUnmodifiedSince create(DateTime date) {
    return new org.apache.pekko.http.scaladsl.model.headers.If$minusUnmodified$minusSince(
        ((org.apache.pekko.http.scaladsl.model.DateTime) date));
  }
}
