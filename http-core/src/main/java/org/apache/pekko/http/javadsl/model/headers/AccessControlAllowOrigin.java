/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model.headers;

/**
 *  Model for the `Access-Control-Allow-Origin` header.
 *  Specification: http://www.w3.org/TR/cors/#access-control-allow-origin-response-header
 */
public abstract class AccessControlAllowOrigin extends org.apache.pekko.http.scaladsl.model.HttpHeader {
    public abstract HttpOriginRange range();

    public static AccessControlAllowOrigin create(HttpOriginRange range) {
        return new org.apache.pekko.http.scaladsl.model.headers.Access$minusControl$minusAllow$minusOrigin(((org.apache.pekko.http.scaladsl.model.headers.HttpOriginRange) range));
    }
}
