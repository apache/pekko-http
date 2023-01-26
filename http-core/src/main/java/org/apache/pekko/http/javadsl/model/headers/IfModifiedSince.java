/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model.headers;

import org.apache.pekko.http.javadsl.model.DateTime;

/**
 *  Model for the `If-Modified-Since` header.
 *  Specification: http://tools.ietf.org/html/draft-ietf-httpbis-p4-conditional-26#section-3.3
 */
public abstract class IfModifiedSince extends org.apache.pekko.http.scaladsl.model.HttpHeader {
    public abstract DateTime date();

    public static IfModifiedSince create(DateTime date) {
        return new org.apache.pekko.http.scaladsl.model.headers.If$minusModified$minusSince(((org.apache.pekko.http.scaladsl.model.DateTime) date));
    }
}
