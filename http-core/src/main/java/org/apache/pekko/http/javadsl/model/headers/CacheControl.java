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
 * Model for the `Cache-Control` header. Specification:
 * http://tools.ietf.org/html/draft-ietf-httpbis-p6-cache-26#section-5.2
 */
public abstract class CacheControl extends org.apache.pekko.http.scaladsl.model.HttpHeader {
  public abstract Iterable<CacheDirective> getDirectives();

  public static CacheControl create(CacheDirective... directives) {
    return new org.apache.pekko.http.scaladsl.model.headers.Cache$minusControl(
        org.apache.pekko.http.impl.util.Util
            .<CacheDirective, org.apache.pekko.http.scaladsl.model.headers.CacheDirective>
                convertArray(directives));
  }
}
