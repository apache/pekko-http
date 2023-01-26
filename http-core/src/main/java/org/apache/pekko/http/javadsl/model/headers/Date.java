/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model.headers;

import org.apache.pekko.http.javadsl.model.DateTime;

/**
 *  Model for the `Date` header.
 *  Specification: http://tools.ietf.org/html/draft-ietf-httpbis-p2-semantics-26#section-7.1.1.2
 */
public abstract class Date extends org.apache.pekko.http.scaladsl.model.HttpHeader {
    public abstract DateTime date();

    public static Date create(DateTime date) {
        return new org.apache.pekko.http.scaladsl.model.headers.Date(((org.apache.pekko.http.scaladsl.model.DateTime) date));
    }
}
