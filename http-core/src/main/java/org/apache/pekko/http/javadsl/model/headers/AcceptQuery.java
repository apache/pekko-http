/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package org.apache.pekko.http.javadsl.model.headers;

import org.apache.pekko.http.impl.util.Util;
import org.apache.pekko.http.javadsl.model.MediaRange;

/**
 * Model for the `Accept-Query` header. Specification:
 * https://www.rfc-editor.org/rfc/rfc10008.html#section-4.1
 */
public abstract class AcceptQuery extends org.apache.pekko.http.scaladsl.model.HttpHeader {
  public abstract Iterable<MediaRange> getMediaRanges();

  public static AcceptQuery create(MediaRange... mediaRanges) {
    return new org.apache.pekko.http.scaladsl.model.headers.Accept$minusQuery(
        Util.<MediaRange, org.apache.pekko.http.scaladsl.model.MediaRange>convertArray(
            mediaRanges));
  }
}
