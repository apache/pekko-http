/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model.headers;

/**
 *  Model for the `Set-Cookie` header.
 *  Specification: https://tools.ietf.org/html/rfc6265
 */
public abstract class SetCookie extends org.apache.pekko.http.scaladsl.model.HttpHeader {
    public abstract HttpCookie cookie();

    public static SetCookie create(HttpCookie cookie) {
        return new org.apache.pekko.http.scaladsl.model.headers.Set$minusCookie(((org.apache.pekko.http.scaladsl.model.headers.HttpCookie) cookie));
    }
}
