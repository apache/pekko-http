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
 * Model for the `User-Agent` header. Specification: <a
 * href="https://www.rfc-editor.org/rfc/rfc9110.html#section-10.1.5">RFC 9110, Section 10.1.5</a>.
 */
public abstract class UserAgent extends org.apache.pekko.http.scaladsl.model.HttpHeader {
  public abstract Iterable<ProductVersion> getProducts();

  public static UserAgent create(ProductVersion... products) {
    return new org.apache.pekko.http.scaladsl.model.headers.User$minusAgent(
        org.apache.pekko.http.impl.util.Util
            .<ProductVersion, org.apache.pekko.http.scaladsl.model.headers.ProductVersion>
                convertArray(products));
  }

  public static UserAgent create(String products) {
    return org.apache.pekko.http.scaladsl.model.headers.User$minusAgent$.MODULE$.apply(products);
  }
}
