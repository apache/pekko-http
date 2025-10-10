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
 * Model for the `Access-Control-Allow-Origin` header. Specification:
 * http://www.w3.org/TR/cors/#access-control-allow-origin-response-header
 */
public abstract class AccessControlAllowOrigin
    extends org.apache.pekko.http.scaladsl.model.HttpHeader {
  public abstract HttpOriginRange range();

  public static AccessControlAllowOrigin create(HttpOriginRange range) {
    return new org.apache.pekko.http.scaladsl.model.headers
        .Access$minusControl$minusAllow$minusOrigin(
        ((org.apache.pekko.http.scaladsl.model.headers.HttpOriginRange) range));
  }
}
