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
 * Model for the `Connection` header. Specification:
 * https://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.10
 */
public abstract class Connection extends org.apache.pekko.http.scaladsl.model.HttpHeader {
  public abstract Iterable<String> getTokens();

  public static Connection create(String... directives) {
    return new org.apache.pekko.http.scaladsl.model.headers.Connection(
        org.apache.pekko.http.impl.util.Util.convertArray(directives));
  }
}
