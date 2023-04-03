/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model.headers;

/**
 *  Model for the `Access-Control-Expose-Headers` header.
 *  Specification: http://www.w3.org/TR/cors/#access-control-expose-headers-response-header
 */
public abstract class AccessControlExposeHeaders extends org.apache.pekko.http.scaladsl.model.HttpHeader {
    public abstract Iterable<String> getHeaders();

    public static AccessControlExposeHeaders create(String... headers) {
        return new org.apache.pekko.http.scaladsl.model.headers.Access$minusControl$minusExpose$minusHeaders(org.apache.pekko.http.impl.util.Util.<String, String>convertArray(headers));
    }
}
