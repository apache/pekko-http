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

import org.apache.pekko.http.impl.util.Util;

/**
 * Model for the `Accept-Encoding` header. Specification: <a
 * href="https://www.rfc-editor.org/rfc/rfc9110.html#section-12.5.3">RFC 9110, Section 12.5.3</a>.
 */
public abstract class AcceptEncoding extends org.apache.pekko.http.scaladsl.model.HttpHeader {
  public abstract Iterable<HttpEncodingRange> getEncodings();

  public static AcceptEncoding create(HttpEncoding encoding) {
    return new org.apache.pekko.http.scaladsl.model.headers.Accept$minusEncoding(
        Util
            .<HttpEncodingRange, org.apache.pekko.http.scaladsl.model.headers.HttpEncodingRange>
                convertArray(new HttpEncodingRange[] {encoding.toRange()}));
  }

  public static AcceptEncoding create(HttpEncodingRange... encodings) {
    return new org.apache.pekko.http.scaladsl.model.headers.Accept$minusEncoding(
        Util
            .<HttpEncodingRange, org.apache.pekko.http.scaladsl.model.headers.HttpEncodingRange>
                convertArray(encodings));
  }
}
