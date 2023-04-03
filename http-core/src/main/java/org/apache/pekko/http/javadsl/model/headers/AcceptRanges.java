/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model.headers;

/**
 *  Model for the `Accept-Ranges` header.
 *  Specification: http://tools.ietf.org/html/draft-ietf-httpbis-p5-range-26#section-2.3
 */
public abstract class AcceptRanges extends org.apache.pekko.http.scaladsl.model.HttpHeader {
    public abstract Iterable<RangeUnit> getRangeUnits();

    public static AcceptRanges create(RangeUnit... rangeUnits) {
        return new org.apache.pekko.http.scaladsl.model.headers.Accept$minusRanges(org.apache.pekko.http.impl.util.Util.<RangeUnit, org.apache.pekko.http.scaladsl.model.headers.RangeUnit>convertArray(rangeUnits));
    }
}
