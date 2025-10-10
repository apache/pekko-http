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

import org.apache.pekko.http.javadsl.model.HttpMethod;

/**
 * Model for the `Allow` header. Specification:
 * http://tools.ietf.org/html/draft-ietf-httpbis-p2-semantics-26#section-7.4.1
 */
public abstract class Allow extends org.apache.pekko.http.scaladsl.model.HttpHeader {
  public abstract Iterable<HttpMethod> getMethods();

  public static Allow create(HttpMethod... methods) {
    return new org.apache.pekko.http.scaladsl.model.headers.Allow(
        org.apache.pekko.http.impl.util.Util
            .<HttpMethod, org.apache.pekko.http.scaladsl.model.HttpMethod>convertArray(methods));
  }
}
