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
 * Represents a cookie pair as used in the `Cookie` header as specified in
 * http://tools.ietf.org/search/rfc6265#section-4.2.1
 */
public abstract class HttpCookiePair {
  public abstract String name();

  public abstract String value();

  /** Converts this cookie pair into an HttpCookie to be used with the `Set-Cookie` header. */
  public abstract HttpCookie toCookie();

  public static HttpCookiePair create(String name, String value) {
    return org.apache.pekko.http.scaladsl.model.headers.HttpCookiePair.apply(name, value);
  }

  public static HttpCookiePair createRaw(String name, String value) {
    return org.apache.pekko.http.scaladsl.model.headers.HttpCookiePair.raw(name, value);
  }
}
