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
 * Model for the `Referer` header. Specification: http://tools.ietf.org/html/rfc7231#section-5.5.2
 */
public abstract class Referer extends org.apache.pekko.http.scaladsl.model.HttpHeader {
  public abstract Uri getUri();

  public static Referer create(Uri uri) {
    return new org.apache.pekko.http.scaladsl.model.headers.Referer(uri.asScala());
  }
}
