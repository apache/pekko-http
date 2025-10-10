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

package org.apache.pekko.http.javadsl.model;

import org.apache.pekko.http.impl.util.Util;
import org.apache.pekko.http.scaladsl.model.HttpMethods$;

import java.util.Optional;

/** Contains static constants for predefined method types. */
public final class HttpMethods {
  private HttpMethods() {}

  public static final HttpMethod CONNECT =
      org.apache.pekko.http.scaladsl.model.HttpMethods.CONNECT();
  public static final HttpMethod DELETE = org.apache.pekko.http.scaladsl.model.HttpMethods.DELETE();
  public static final HttpMethod GET = org.apache.pekko.http.scaladsl.model.HttpMethods.GET();
  public static final HttpMethod HEAD = org.apache.pekko.http.scaladsl.model.HttpMethods.HEAD();
  public static final HttpMethod OPTIONS =
      org.apache.pekko.http.scaladsl.model.HttpMethods.OPTIONS();
  public static final HttpMethod PATCH = org.apache.pekko.http.scaladsl.model.HttpMethods.PATCH();
  public static final HttpMethod POST = org.apache.pekko.http.scaladsl.model.HttpMethods.POST();
  public static final HttpMethod PUT = org.apache.pekko.http.scaladsl.model.HttpMethods.PUT();
  public static final HttpMethod TRACE = org.apache.pekko.http.scaladsl.model.HttpMethods.TRACE();

  /** Create a custom method type. */
  public static HttpMethod custom(
      String value,
      boolean safe,
      boolean idempotent,
      org.apache.pekko.http.javadsl.model.RequestEntityAcceptance requestEntityAcceptance) {
    // This cast is safe as implementation of RequestEntityAcceptance only exists in Scala
    org.apache.pekko.http.scaladsl.model.RequestEntityAcceptance scalaRequestEntityAcceptance =
        (org.apache.pekko.http.scaladsl.model.RequestEntityAcceptance) requestEntityAcceptance;
    return org.apache.pekko.http.scaladsl.model.HttpMethod.custom(
        value, safe, idempotent, scalaRequestEntityAcceptance);
  }

  /** Looks up a predefined HTTP method with the given name. */
  public static Optional<HttpMethod> lookup(String name) {
    return Util.<HttpMethod, org.apache.pekko.http.scaladsl.model.HttpMethod>lookupInRegistry(
        HttpMethods$.MODULE$, name);
  }
}
