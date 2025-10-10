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

/** Contains constructors to create a HttpCharsetRange. */
public final class HttpCharsetRanges {
  private HttpCharsetRanges() {}

  /** A constant representing the range that matches all charsets. */
  public static final HttpCharsetRange ALL =
      org.apache.pekko.http.scaladsl.model.HttpCharsetRange.$times$.MODULE$;
}
