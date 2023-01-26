/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model.headers;

/**
 * Model for the synthetic `Timeout-Access` header.
 */
public abstract class TimeoutAccess extends org.apache.pekko.http.scaladsl.model.HttpHeader {
    public abstract org.apache.pekko.http.javadsl.TimeoutAccess timeoutAccess();

    public static TimeoutAccess create(org.apache.pekko.http.javadsl.TimeoutAccess timeoutAccess) {
        return new org.apache.pekko.http.scaladsl.model.headers.Timeout$minusAccess((org.apache.pekko.http.scaladsl.TimeoutAccess) timeoutAccess);
    }
}
