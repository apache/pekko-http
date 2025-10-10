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
import org.apache.pekko.http.javadsl.model.HttpCharsetRange;

/**
 * Model for the `Accept-Charset` header. Specification:
 * http://tools.ietf.org/html/draft-ietf-httpbis-p2-semantics-26#section-5.3.3
 */
public abstract class AcceptCharset extends org.apache.pekko.http.scaladsl.model.HttpHeader {
  public abstract Iterable<HttpCharsetRange> getCharsetRanges();

  public static AcceptCharset create(HttpCharsetRange... charsetRanges) {
    return new org.apache.pekko.http.scaladsl.model.headers.Accept$minusCharset(
        Util.<HttpCharsetRange, org.apache.pekko.http.scaladsl.model.HttpCharsetRange>convertArray(
            charsetRanges));
  }
}
