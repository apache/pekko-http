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
 * Represents an Http status-code and message. See {@link StatusCodes} for the set of predefined
 * status-codes.
 *
 * @see StatusCodes for convenience access to often used values.
 */
public abstract class StatusCode {
  /** Returns the numeric code of this status code. */
  public abstract int intValue();

  /** Returns the reason message for this status code. */
  public abstract String reason();

  /**
   * Returns the default message to be included as the content of an Http response with this
   * status-code.
   */
  public abstract String defaultMessage();

  /** Returns if the status-code represents success. */
  public abstract boolean isSuccess();

  /** Returns if the status-code represents failure. */
  public abstract boolean isFailure();

  /**
   * Returns if a response with this status-code is allowed to be accompanied with a non-empty
   * entity.
   */
  public abstract boolean allowsEntity();

  /** Returns if the status-code is a redirection status code. */
  public abstract boolean isRedirection();
}
