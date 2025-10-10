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

package org.apache.pekko.http.javadsl.model;

import org.apache.pekko.http.impl.util.JavaAccessors;
import org.apache.pekko.http.javadsl.model.headers.HttpEncoding;

/** Represents an Http response. */
public abstract class HttpResponse
    implements HttpMessage, HttpMessage.MessageTransformations<HttpResponse> {
  /** Returns the status-code of this response. */
  public abstract StatusCode status();

  /** Returns the entity of this response. */
  public abstract ResponseEntity entity();

  /** Returns a copy of this instance with a new status-code. */
  public abstract HttpResponse withStatus(StatusCode statusCode);

  /** Returns a copy of this instance with a new status-code. */
  public abstract HttpResponse withStatus(int statusCode);

  /** Returns a copy of this instance with a new entity. */
  public abstract HttpResponse withEntity(ResponseEntity entity);

  /**
   * Returns the content encoding as specified by the Content-Encoding header. If no
   * Content-Encoding header is present the default value 'identity' is returned.
   */
  public abstract HttpEncoding encoding();

  /** Returns a default response to be changed using the `withX` methods. */
  public static HttpResponse create() {
    return JavaAccessors.HttpResponse();
  }
}
