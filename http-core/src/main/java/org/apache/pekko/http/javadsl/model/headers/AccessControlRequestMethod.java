/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model.headers;

import org.apache.pekko.http.javadsl.model.HttpMethod;

/**
 *  Model for the `Access-Control-Request-Method` header.
 *  Specification: http://www.w3.org/TR/cors/#access-control-request-method-request-header
 */
public abstract class AccessControlRequestMethod extends org.apache.pekko.http.scaladsl.model.HttpHeader {
    public abstract HttpMethod method();

    public static AccessControlRequestMethod create(HttpMethod method) {
        return new org.apache.pekko.http.scaladsl.model.headers.Access$minusControl$minusRequest$minusMethod(((org.apache.pekko.http.scaladsl.model.HttpMethod) method));
    }
}
