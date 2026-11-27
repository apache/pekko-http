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

import org.apache.pekko.http.impl.util.Util;
import org.apache.pekko.http.javadsl.model.MediaRange;

/**
 * Model for the `Accept` header. Specification: <a
 * href="https://www.rfc-editor.org/rfc/rfc9110.html#section-12.5.1">RFC 9110, Section 12.5.1</a>.
 */
public abstract class Accept extends org.apache.pekko.http.scaladsl.model.HttpHeader {
  public abstract Iterable<MediaRange> getMediaRanges();

  public abstract boolean acceptsAll();

  public static Accept create(MediaRange... mediaRanges) {
    return new org.apache.pekko.http.scaladsl.model.headers.Accept(
        Util.<MediaRange, org.apache.pekko.http.scaladsl.model.MediaRange>convertArray(
            mediaRanges));
  }
}
