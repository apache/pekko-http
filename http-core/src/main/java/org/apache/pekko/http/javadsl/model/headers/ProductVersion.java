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

public abstract class ProductVersion {
  public abstract String product();

  public abstract String version();

  public abstract String comment();

  public static ProductVersion create(String product, String version, String comment) {
    return new org.apache.pekko.http.scaladsl.model.headers.ProductVersion(
        product, version, comment);
  }

  public static ProductVersion create(String product, String version) {
    return create(product, version, "");
  }
}
