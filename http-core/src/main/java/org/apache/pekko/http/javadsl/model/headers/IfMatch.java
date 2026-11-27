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
 * Model for the `If-Match` header. Specification: <a
 * href="https://www.rfc-editor.org/rfc/rfc9110.html#section-13.1.1">RFC 9110, Section 13.1.1</a>.
 */
public abstract class IfMatch extends org.apache.pekko.http.scaladsl.model.HttpHeader {
  public abstract EntityTagRange m();

  public static IfMatch create(EntityTagRange m) {
    return new org.apache.pekko.http.scaladsl.model.headers.If$minusMatch(
        ((org.apache.pekko.http.scaladsl.model.headers.EntityTagRange) m));
  }
}
