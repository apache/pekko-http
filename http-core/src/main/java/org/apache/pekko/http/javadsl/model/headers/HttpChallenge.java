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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import scala.jdk.javaapi.OptionConverters;

public abstract class HttpChallenge {
  public abstract String scheme();

  public abstract String realm();

  public abstract Map<String, String> getParams();

  public static HttpChallenge create(String scheme, String realm) {
    return create(scheme, Optional.of(realm));
  }

  public static HttpChallenge create(String scheme, String realm, Map<String, String> params) {
    return create(scheme, Optional.of(realm), params);
  }

  /** @since 1.3.0 */
  public static HttpChallenge create(String scheme, Optional<String> realm) {
    return org.apache.pekko.http.scaladsl.model.headers.HttpChallenge.apply(
        scheme, OptionConverters.toScala(realm), Util.emptyMap);
  }

  /** @since 1.3.0 */
  public static HttpChallenge create(
      String scheme, Optional<String> realm, Map<String, String> params) {
    return org.apache.pekko.http.scaladsl.model.headers.HttpChallenge.apply(
        scheme, OptionConverters.toScala(realm), Util.convertMapToScala(params));
  }

  public static HttpChallenge createBasic(String realm) {
    Map<String, String> params = new HashMap<String, String>();
    params.put("charset", "UTF-8");
    return create("Basic", Optional.of(realm), params);
  }

  public static HttpChallenge createOAuth2(String realm) {
    return create("Bearer", Optional.of(realm));
  }
}
