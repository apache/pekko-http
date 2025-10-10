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
 * Model for the `Raw-Request-URI` header. Custom header we use for transporting the raw request URI
 * either to the application (server-side) or to the request rendering stage (client-side).
 */
public abstract class RawRequestURI extends org.apache.pekko.http.scaladsl.model.HttpHeader {
  public abstract String uri();

  public static RawRequestURI create(String uri) {
    return new org.apache.pekko.http.scaladsl.model.headers.Raw$minusRequest$minusURI(uri);
  }
}
