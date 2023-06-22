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
 * Model for the `Server` header. Specification:
 * http://tools.ietf.org/html/draft-ietf-httpbis-p2-semantics-26#section-7.4.2
 */
public abstract class Server extends org.apache.pekko.http.scaladsl.model.HttpHeader {
  public abstract Iterable<ProductVersion> getProducts();

  public static Server create(ProductVersion... products) {
    return new org.apache.pekko.http.scaladsl.model.headers.Server(
        org.apache.pekko.http.impl.util.Util
            .<ProductVersion, org.apache.pekko.http.scaladsl.model.headers.ProductVersion>
                convertArray(products));
  }
}
