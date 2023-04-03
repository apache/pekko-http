/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model;

/**
 * Contains constructors to create a HttpCharsetRange.
 */
public final class HttpCharsetRanges {
    private HttpCharsetRanges() {}

    /**
     * A constant representing the range that matches all charsets.
     */
    public static final HttpCharsetRange ALL = org.apache.pekko.http.scaladsl.model.HttpCharsetRange.$times$.MODULE$;
}
