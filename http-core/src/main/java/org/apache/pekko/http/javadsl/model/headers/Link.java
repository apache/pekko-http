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

/** Model for the `Link` header. Specification: http://tools.ietf.org/html/rfc5988#section-5 */
public abstract class Link extends org.apache.pekko.http.scaladsl.model.HttpHeader {
  public abstract Iterable<LinkValue> getValues();

  public static Link create(LinkValue... values) {
    return new org.apache.pekko.http.scaladsl.model.headers.Link(
        org.apache.pekko.http.impl.util.Util
            .<LinkValue, org.apache.pekko.http.scaladsl.model.headers.LinkValue>convertArray(
                values));
  }
}
