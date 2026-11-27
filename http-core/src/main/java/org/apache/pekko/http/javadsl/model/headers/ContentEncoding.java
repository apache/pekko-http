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
 * Model for the `Content-Encoding` header. Specification: <a
 * href="https://www.rfc-editor.org/rfc/rfc9110.html#section-8.4">RFC 9110, Section 8.4</a>.
 */
public abstract class ContentEncoding extends org.apache.pekko.http.scaladsl.model.HttpHeader {
  public abstract Iterable<HttpEncoding> getEncodings();

  public static ContentEncoding create(HttpEncoding... encodings) {
    return new org.apache.pekko.http.scaladsl.model.headers.Content$minusEncoding(
        org.apache.pekko.http.impl.util.Util
            .<HttpEncoding, org.apache.pekko.http.scaladsl.model.headers.HttpEncoding>convertArray(
                encodings));
  }
}
