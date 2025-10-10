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

/**
 * Model for the `Content-Range` header. Specification:
 * http://tools.ietf.org/html/draft-ietf-httpbis-p5-range-26#section-4.2
 */
public abstract class ContentRange extends org.apache.pekko.http.scaladsl.model.HttpHeader {
  public abstract RangeUnit rangeUnit();

  public abstract org.apache.pekko.http.javadsl.model.ContentRange contentRange();

  public static ContentRange create(
      RangeUnit rangeUnit, org.apache.pekko.http.javadsl.model.ContentRange contentRange) {
    return new org.apache.pekko.http.scaladsl.model.headers.Content$minusRange(
        ((org.apache.pekko.http.scaladsl.model.headers.RangeUnit) rangeUnit),
        ((org.apache.pekko.http.scaladsl.model.ContentRange) contentRange));
  }
}
