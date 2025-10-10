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

import java.nio.charset.Charset;

/**
 * Represents a charset in Http. See {@link HttpCharsets} for a set of predefined charsets and
 * static constructors to create custom charsets.
 *
 * @see HttpCharsets for convenience access to often used values.
 */
public abstract class HttpCharset {
  /** Returns the name of this charset. */
  public abstract String value();

  /** Creates a range from this charset with qValue = 1. */
  public HttpCharsetRange toRange() {
    return withQValue(1f);
  }

  /** Creates a range from this charset with the given qValue. */
  public HttpCharsetRange toRange(float qValue) {
    return withQValue(qValue);
  }

  /** An alias for toRange(float). */
  public abstract HttpCharsetRange withQValue(float qValue);

  /** Returns the predefined alias names for this charset. */
  public abstract Iterable<String> getAliases();

  /** Returns the Charset for this charset if available or throws an exception otherwise. */
  public abstract Charset nioCharset();
}
