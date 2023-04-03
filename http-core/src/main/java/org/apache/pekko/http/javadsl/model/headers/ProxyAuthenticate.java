/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model.headers;

/**
 *  Model for the `Proxy-Authenticate` header.
 *  Specification: http://tools.ietf.org/html/draft-ietf-httpbis-p7-auth-26#section-4.3
 */
public abstract class ProxyAuthenticate extends org.apache.pekko.http.scaladsl.model.HttpHeader {
    public abstract Iterable<HttpChallenge> getChallenges();

    public static ProxyAuthenticate create(HttpChallenge... challenges) {
        return new org.apache.pekko.http.scaladsl.model.headers.Proxy$minusAuthenticate(org.apache.pekko.http.impl.util.Util.<HttpChallenge, org.apache.pekko.http.scaladsl.model.headers.HttpChallenge>convertArray(challenges));
    }
}
