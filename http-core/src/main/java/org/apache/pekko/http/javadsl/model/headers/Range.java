/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model.headers;

/**
 *  Model for the `Range` header.
 *  Specification: http://tools.ietf.org/html/draft-ietf-httpbis-p5-range-26#section-3.1
 */
public abstract class Range extends org.apache.pekko.http.scaladsl.model.HttpHeader {
    public abstract RangeUnit rangeUnit();
    public abstract Iterable<ByteRange> getRanges();

    public static Range create(RangeUnit rangeUnit, ByteRange... ranges) {
        return new org.apache.pekko.http.scaladsl.model.headers.Range(((org.apache.pekko.http.scaladsl.model.headers.RangeUnit) rangeUnit), org.apache.pekko.http.impl.util.Util.<ByteRange, org.apache.pekko.http.scaladsl.model.headers.ByteRange>convertArray(ranges));
    }
}
