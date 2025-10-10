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
 * Model for the `Access-Control-Allow-Credentials` header. Specification:
 * http://www.w3.org/TR/cors/#access-control-allow-credentials-response-header
 */
public abstract class AccessControlAllowCredentials
    extends org.apache.pekko.http.scaladsl.model.HttpHeader {
  public abstract boolean allow();

  public static AccessControlAllowCredentials create(boolean allow) {
    return new org.apache.pekko.http.scaladsl.model.headers
        .Access$minusControl$minusAllow$minusCredentials(allow);
  }
}
