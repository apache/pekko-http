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

import org.apache.pekko.http.javadsl.model.Uri;

/**
 * Model for the `Location` header. Specification:
 * http://tools.ietf.org/html/draft-ietf-httpbis-p2-semantics-26#section-7.1.2
 */
public abstract class Location extends org.apache.pekko.http.scaladsl.model.HttpHeader {
  public abstract Uri getUri();

  public static Location create(Uri uri) {
    return new org.apache.pekko.http.scaladsl.model.headers.Location(uri.asScala());
  }

  public static Location create(String uri) {
    return create(Uri.create(uri));
  }
}
