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
 * Model for the `Strict-Transport-Security` header. Specification:
 * https://tools.ietf.org/html/rfc6797
 */
public abstract class StrictTransportSecurity
    extends org.apache.pekko.http.scaladsl.model.HttpHeader {
  public abstract long maxAge();

  public abstract boolean includeSubDomains();

  public static StrictTransportSecurity create(long maxAge) {
    return new org.apache.pekko.http.scaladsl.model.headers.Strict$minusTransport$minusSecurity(
        maxAge, false);
  }

  public static StrictTransportSecurity create(long maxAge, boolean includeSubDomains) {
    return new org.apache.pekko.http.scaladsl.model.headers.Strict$minusTransport$minusSecurity(
        maxAge, includeSubDomains);
  }
}
