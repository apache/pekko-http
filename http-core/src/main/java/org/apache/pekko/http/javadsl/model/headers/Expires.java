/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model.headers;

import org.apache.pekko.http.javadsl.model.DateTime;

/**
 *  Model for the `Expires` header.
 *  Specification: http://tools.ietf.org/html/rfc7234#section-5.3
 */
public abstract class Expires extends org.apache.pekko.http.scaladsl.model.HttpHeader {
    public abstract DateTime date();

    public static Expires create(DateTime date) {
        return new org.apache.pekko.http.scaladsl.model.headers.Expires(((org.apache.pekko.http.scaladsl.model.DateTime) date));
    }
}
