/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model.headers;

import org.apache.pekko.http.javadsl.model.HttpMethod;

/**
 *  Model for the `Access-Control-Allow-Methods` header.
 *  Specification: http://www.w3.org/TR/cors/#access-control-allow-methods-response-header
 */
public abstract class AccessControlAllowMethods extends org.apache.pekko.http.scaladsl.model.HttpHeader {
    public abstract Iterable<HttpMethod> getMethods();

    public static AccessControlAllowMethods create(HttpMethod... methods) {
        return new org.apache.pekko.http.scaladsl.model.headers.Access$minusControl$minusAllow$minusMethods(org.apache.pekko.http.impl.util.Util.<HttpMethod, org.apache.pekko.http.scaladsl.model.HttpMethod>convertArray(methods));
    }
}
