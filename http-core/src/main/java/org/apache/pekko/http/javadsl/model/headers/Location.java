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
 * Model for the `Location` header. Specification: <a
 * href="https://www.rfc-editor.org/rfc/rfc9110.html#section-10.2.2">RFC 9110, Section 10.2.2</a>.
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
