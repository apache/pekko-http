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
 * Model for the `Range` header. Specification:
 * http://tools.ietf.org/html/draft-ietf-httpbis-p5-range-26#section-3.1
 */
public abstract class Range extends org.apache.pekko.http.scaladsl.model.HttpHeader {
  public abstract RangeUnit rangeUnit();

  public abstract Iterable<ByteRange> getRanges();

  public static Range create(RangeUnit rangeUnit, ByteRange... ranges) {
    return new org.apache.pekko.http.scaladsl.model.headers.Range(
        ((org.apache.pekko.http.scaladsl.model.headers.RangeUnit) rangeUnit),
        org.apache.pekko.http.impl.util.Util
            .<ByteRange, org.apache.pekko.http.scaladsl.model.headers.ByteRange>convertArray(
                ranges));
  }
}
