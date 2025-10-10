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
 * Model for the `Access-Control-Max-Age` header. Specification:
 * http://www.w3.org/TR/cors/#access-control-max-age-response-header
 */
public abstract class AccessControlMaxAge extends org.apache.pekko.http.scaladsl.model.HttpHeader {
  public abstract long deltaSeconds();

  public static AccessControlMaxAge create(long deltaSeconds) {
    return new org.apache.pekko.http.scaladsl.model.headers.Access$minusControl$minusMax$minusAge(
        deltaSeconds);
  }
}
