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

/** Model for the `Content-Disposition` header. Specification: http://tools.ietf.org/html/rfc6266 */
public abstract class ContentDisposition extends org.apache.pekko.http.scaladsl.model.HttpHeader {
  public abstract ContentDispositionType dispositionType();

  public abstract java.util.Map<String, String> getParams();

  public static ContentDisposition create(
      ContentDispositionType dispositionType, java.util.Map<String, String> params) {
    return new org.apache.pekko.http.scaladsl.model.headers.Content$minusDisposition(
        ((org.apache.pekko.http.scaladsl.model.headers.ContentDispositionType) dispositionType),
        org.apache.pekko.http.impl.util.Util.convertMapToScala(params));
  }
}
