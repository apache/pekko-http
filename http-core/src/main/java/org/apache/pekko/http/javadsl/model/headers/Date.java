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

import org.apache.pekko.http.javadsl.model.DateTime;

/**
 * Model for the `Date` header. Specification:
 * http://tools.ietf.org/html/draft-ietf-httpbis-p2-semantics-26#section-7.1.1.2
 */
public abstract class Date extends org.apache.pekko.http.scaladsl.model.HttpHeader {
  public abstract DateTime date();

  public static Date create(DateTime date) {
    return new org.apache.pekko.http.scaladsl.model.headers.Date(
        ((org.apache.pekko.http.scaladsl.model.DateTime) date));
  }
}
