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
 * Model for the `Access-Control-Request-Method` header. Specification:
 * http://www.w3.org/TR/cors/#access-control-request-method-request-header
 */
public abstract class AccessControlRequestMethod
    extends org.apache.pekko.http.scaladsl.model.HttpHeader {
  public abstract HttpMethod method();

  public static AccessControlRequestMethod create(HttpMethod method) {
    return new org.apache.pekko.http.scaladsl.model.headers
        .Access$minusControl$minusRequest$minusMethod(
        ((org.apache.pekko.http.scaladsl.model.HttpMethod) method));
  }
}
