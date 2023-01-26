/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model.headers;

/**
 *  Model for the `Access-Control-Allow-Headers` header.
 *  Specification: http://www.w3.org/TR/cors/#access-control-allow-headers-response-header
 */
public abstract class AccessControlAllowHeaders extends org.apache.pekko.http.scaladsl.model.HttpHeader {
    public abstract Iterable<String> getHeaders();

    public static AccessControlAllowHeaders create(String... headers) {
        return new org.apache.pekko.http.scaladsl.model.headers.Access$minusControl$minusAllow$minusHeaders(org.apache.pekko.http.impl.util.Util.<String, String>convertArray(headers));
    }
}
