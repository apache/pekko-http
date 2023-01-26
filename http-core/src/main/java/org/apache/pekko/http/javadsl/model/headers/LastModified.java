/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model.headers;

import org.apache.pekko.http.javadsl.model.DateTime;

/**
 *  Model for the `Last-Modified` header.
 *  Specification: http://tools.ietf.org/html/draft-ietf-httpbis-p4-conditional-26#section-2.2
 */
public abstract class LastModified extends org.apache.pekko.http.scaladsl.model.HttpHeader {
    public abstract DateTime date();

    public static LastModified create(DateTime date) {
        return new org.apache.pekko.http.scaladsl.model.headers.Last$minusModified(((org.apache.pekko.http.scaladsl.model.DateTime) date));
    }
}
