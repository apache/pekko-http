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

import org.apache.pekko.http.impl.util.Util;

import java.util.Map;

public abstract class HttpCredentials {
  public abstract String scheme();

  public abstract String token();

  public abstract Map<String, String> getParams();

  public static HttpCredentials create(String scheme, String token) {
    return new org.apache.pekko.http.scaladsl.model.headers.GenericHttpCredentials(
        scheme, token, Util.emptyMap);
  }

  public static HttpCredentials create(String scheme, String token, Map<String, String> params) {
    return new org.apache.pekko.http.scaladsl.model.headers.GenericHttpCredentials(
        scheme, token, Util.convertMapToScala(params));
  }

  public static BasicHttpCredentials createBasicHttpCredentials(String username, String password) {
    return new org.apache.pekko.http.scaladsl.model.headers.BasicHttpCredentials(
        username, password);
  }

  public static OAuth2BearerToken createOAuth2BearerToken(String token) {
    return new org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken(token);
  }
}
