/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model.headers;

public abstract class OAuth2BearerToken extends org.apache.pekko.http.scaladsl.model.headers.HttpCredentials {
    public abstract String token();
}
