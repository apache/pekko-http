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
 * Model for the `X-Forwarded-Proto` header. Specification:
 * https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/X-Forwarded-Proto
 */
public abstract class XForwardedProto extends org.apache.pekko.http.scaladsl.model.HttpHeader {
  public abstract String getProtocol();

  public static XForwardedProto create(String protocol) {
    return new org.apache.pekko.http.scaladsl.model.headers.X$minusForwarded$minusProto(protocol);
  }
}
