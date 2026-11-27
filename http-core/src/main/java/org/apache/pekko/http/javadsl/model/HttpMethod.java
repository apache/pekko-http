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

/**
 * Represents an HTTP request method. See {@link HttpMethods} for a set of predefined methods and
 * static constructors to create custom ones.
 *
 * @see HttpMethods for convenience access to often used values.
 */
public abstract class HttpMethod {

  /** Returns the name of the method, always equal to {@link #value()}. */
  public final String name() {
    return value();
  }

  /** Returns the name of the method. */
  public abstract String value();

  /**
   * Returns if this method is "safe" as defined in <a
   * href="https://www.rfc-editor.org/rfc/rfc9110.html#section-9.2.1">RFC 9110, Section 9.2.1</a>.
   */
  public abstract boolean isSafe();

  /**
   * Returns if this method is "idempotent" as defined in <a
   * href="https://www.rfc-editor.org/rfc/rfc9110.html#section-9.2.2">RFC 9110, Section 9.2.2</a>.
   */
  public abstract boolean isIdempotent();

  /** Returns if requests with this method may contain an entity. */
  public abstract boolean isEntityAccepted();

  /** Returns the entity acceptance level for this method. */
  public abstract org.apache.pekko.http.javadsl.model.RequestEntityAcceptance
      requestEntityAcceptance();
}
