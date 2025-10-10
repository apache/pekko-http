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
 * Model for the `X-Forwarded-Host` header. Specification:
 * https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/X-Forwarded-Host
 */
public abstract class XForwardedHost extends org.apache.pekko.http.scaladsl.model.HttpHeader {
  public abstract org.apache.pekko.http.javadsl.model.Host getHost();

  public static XForwardedHost create(org.apache.pekko.http.javadsl.model.Host host) {
    return new org.apache.pekko.http.scaladsl.model.headers.X$minusForwarded$minusHost(
        ((org.apache.pekko.http.scaladsl.model.Uri.Host) host));
  }
}
