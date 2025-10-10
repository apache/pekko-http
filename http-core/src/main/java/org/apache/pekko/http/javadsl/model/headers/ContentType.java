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
 * Model for the `Content-Type` header. Specification:
 * http://tools.ietf.org/html/draft-ietf-httpbis-p2-semantics-26#section-3.1.1.5
 */
public abstract class ContentType extends org.apache.pekko.http.scaladsl.model.HttpHeader {
  public abstract org.apache.pekko.http.javadsl.model.ContentType contentType();

  public static ContentType create(org.apache.pekko.http.javadsl.model.ContentType contentType) {
    return new org.apache.pekko.http.scaladsl.model.headers.Content$minusType(
        ((org.apache.pekko.http.scaladsl.model.ContentType) contentType));
  }
}
