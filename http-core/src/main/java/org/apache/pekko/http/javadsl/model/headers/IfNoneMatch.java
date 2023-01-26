/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model.headers;

/**
 *  Model for the `If-None-Match` header.
 *  Specification: http://tools.ietf.org/html/draft-ietf-httpbis-p4-conditional-26#section-3.2
 */
public abstract class IfNoneMatch extends org.apache.pekko.http.scaladsl.model.HttpHeader {
    public abstract EntityTagRange m();

    public static IfNoneMatch create(EntityTagRange m) {
        return new org.apache.pekko.http.scaladsl.model.headers.If$minusNone$minusMatch(((org.apache.pekko.http.scaladsl.model.headers.EntityTagRange) m));
    }
}
