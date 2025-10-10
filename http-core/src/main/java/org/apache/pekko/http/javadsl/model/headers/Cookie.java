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

/** Model for the `Cookie` header. Specification: https://tools.ietf.org/html/rfc6265#section-4.2 */
public abstract class Cookie extends org.apache.pekko.http.scaladsl.model.HttpHeader {
  public abstract Iterable<HttpCookiePair> getCookies();

  public static Cookie create(HttpCookiePair... cookies) {
    return new org.apache.pekko.http.scaladsl.model.headers.Cookie(
        org.apache.pekko.http.impl.util.Util
            .<HttpCookiePair, org.apache.pekko.http.scaladsl.model.headers.HttpCookiePair>
                convertArray(cookies));
  }

  public static Cookie create(String name, String value) {
    return create(HttpCookiePair.create(name, value));
  }
}
