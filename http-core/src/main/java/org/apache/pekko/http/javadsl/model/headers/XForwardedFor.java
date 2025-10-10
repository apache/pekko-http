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
 * Model for the `X-Forwarded-For` header. Specification:
 * http://en.wikipedia.org/wiki/X-Forwarded-For
 */
public abstract class XForwardedFor extends org.apache.pekko.http.scaladsl.model.HttpHeader {
  public abstract Iterable<org.apache.pekko.http.javadsl.model.RemoteAddress> getAddresses();

  public static XForwardedFor create(
      org.apache.pekko.http.javadsl.model.RemoteAddress... addresses) {
    return new org.apache.pekko.http.scaladsl.model.headers.X$minusForwarded$minusFor(
        org.apache.pekko.http.impl.util.Util
            .<org.apache.pekko.http.javadsl.model.RemoteAddress,
                org.apache.pekko.http.scaladsl.model.RemoteAddress>
                convertArray(addresses));
  }
}
