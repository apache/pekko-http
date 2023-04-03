/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model.headers;

/**
 *  Model for the `If-Match` header.
 *  Specification: http://tools.ietf.org/html/draft-ietf-httpbis-p4-conditional-26#section-3.1
 */
public abstract class IfMatch extends org.apache.pekko.http.scaladsl.model.HttpHeader {
    public abstract EntityTagRange m();

    public static IfMatch create(EntityTagRange m) {
        return new org.apache.pekko.http.scaladsl.model.headers.If$minusMatch(((org.apache.pekko.http.scaladsl.model.headers.EntityTagRange) m));
    }
}
