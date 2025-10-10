/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model.headers;

/**
 * Model for the `Authorization` header. Specification:
 * http://tools.ietf.org/html/draft-ietf-httpbis-p7-auth-26#section-4.2
 */
public abstract class Authorization extends org.apache.pekko.http.scaladsl.model.HttpHeader {
  public abstract HttpCredentials credentials();

  public static Authorization create(HttpCredentials credentials) {
    return new org.apache.pekko.http.scaladsl.model.headers.Authorization(
        ((org.apache.pekko.http.scaladsl.model.headers.HttpCredentials) credentials));
  }

  public static Authorization basic(String username, String password) {
    return create(HttpCredentials.createBasicHttpCredentials(username, password));
  }

  public static Authorization oauth2(String token) {
    return create(HttpCredentials.createOAuth2BearerToken(token));
  }
}
