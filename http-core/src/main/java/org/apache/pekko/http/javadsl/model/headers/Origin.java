/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model.headers;

/**
 *  Model for the `Origin` header.
 *  Specification: http://tools.ietf.org/html/rfc6454#section-7
 */
public abstract class Origin extends org.apache.pekko.http.scaladsl.model.HttpHeader {
    public abstract Iterable<HttpOrigin> getOrigins();

    public static Origin create(HttpOrigin... origins) {
        return new org.apache.pekko.http.scaladsl.model.headers.Origin(org.apache.pekko.http.impl.util.Util.<HttpOrigin, org.apache.pekko.http.scaladsl.model.headers.HttpOrigin>convertArray(origins));
    }
}
