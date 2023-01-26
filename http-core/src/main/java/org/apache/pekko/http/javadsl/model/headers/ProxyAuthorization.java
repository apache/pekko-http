/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model.headers;

/**
 *  Model for the `Proxy-Authorization` header.
 *  Specification: http://tools.ietf.org/html/draft-ietf-httpbis-p7-auth-26#section-4.4
 */
public abstract class ProxyAuthorization extends org.apache.pekko.http.scaladsl.model.HttpHeader {
    public abstract HttpCredentials credentials();

    public static ProxyAuthorization create(HttpCredentials credentials) {
        return new org.apache.pekko.http.scaladsl.model.headers.Proxy$minusAuthorization(((org.apache.pekko.http.scaladsl.model.headers.HttpCredentials) credentials));
    }
}
