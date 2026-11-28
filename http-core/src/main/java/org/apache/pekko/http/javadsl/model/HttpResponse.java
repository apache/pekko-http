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

  /**
   * A complete strict 200 representation for a same-origin HTTP/2 GET. The application must be
   * authoritative for the original request's origin and supply an appropriate representation.
   *
   * @since 2.1.0
   */
  public static final class Http2ServerPushPlan {
    private final org.apache.pekko.http.scaladsl.model.Http2ServerPushPlan delegate;

    private Http2ServerPushPlan(org.apache.pekko.http.scaladsl.model.Http2ServerPushPlan delegate) {
      this.delegate = delegate;
    }

    /** Creates a plan for a rooted relative URI with no authority or fragment. */
    public static Http2ServerPushPlan create(Uri target, HttpResponse response) {
      return new Http2ServerPushPlan(JavaAccessors.Http2ServerPushPlan(target, response));
    }
  }

  /**
   * Offer one optional same-origin HTTP/2 push alongside this final response. Use the original
   * request from the bound handler. The primary response is still sent if push cannot immediately
   * start or is disabled. This is not a delivery acknowledgement.
   *
   * @since 2.1.0
   */
  public final HttpResponse withHttp2ServerPush(HttpRequest parent, Http2ServerPushPlan plan) {
    return JavaAccessors.withHttp2ServerPush(this, parent, plan.delegate);
  }

  /** Returns a default response to be changed using the `withX` methods. */
  public static HttpResponse create() {
    return JavaAccessors.HttpResponse();
  }
}
