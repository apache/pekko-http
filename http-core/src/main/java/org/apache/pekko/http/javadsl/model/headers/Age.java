/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model.headers;

/**
 *  Model for the `Age` header.
 *  Specification: http://tools.ietf.org/html/rfc7234#section-5.1
 */
public abstract class Age extends org.apache.pekko.http.scaladsl.model.HttpHeader {
    public abstract long deltaSeconds();

    public static Age create(long deltaSeconds) {
        return new org.apache.pekko.http.scaladsl.model.headers.Age(deltaSeconds);
    }
}
