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
 * A data structure that combines an acceptable media range and an acceptable charset range into one
 * structure to be used with unmarshalling.
 */
public abstract class ContentTypeRange {
  public abstract MediaRange mediaRange();

  public abstract HttpCharsetRange charsetRange();

  /** Returns true if this range includes the given content type. */
  public abstract boolean matches(ContentType contentType);
}
