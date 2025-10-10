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
 * Represents an Http charset range. This can either be `*` which matches all charsets or a specific
 * charset. {@link HttpCharsetRanges} contains static constructors for HttpCharsetRanges.
 *
 * @see HttpCharsetRanges for convenience access to often used values.
 */
public abstract class HttpCharsetRange {

  /** The qValue for this range. */
  public abstract float qValue();

  /** Returns if the given charset matches this range. */
  public abstract boolean matches(HttpCharset charset);

  /** Returns a copy of this range with the given qValue. */
  public abstract HttpCharsetRange withQValue(float qValue);
}
