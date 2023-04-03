/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model.headers;

/**
 *  Model for the `Access-Control-Request-Headers` header.
 *  Specification: http://www.w3.org/TR/cors/#access-control-request-headers-request-header
 */
public abstract class AccessControlRequestHeaders extends org.apache.pekko.http.scaladsl.model.HttpHeader {
    public abstract Iterable<String> getHeaders();

    public static AccessControlRequestHeaders create(String... headers) {
        return new org.apache.pekko.http.scaladsl.model.headers.Access$minusControl$minusRequest$minusHeaders(org.apache.pekko.http.impl.util.Util.<String, String>convertArray(headers));
    }
}
