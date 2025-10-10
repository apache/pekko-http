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

import java.util.Map;

/**
 * Represents an Http media-range. A media-range either matches a single media-type or it matches
 * all media-types of a given main-type. Each range can specify a qValue or other parameters.
 */
public abstract class MediaRange {
  /** Returns the main-type this media-range matches. */
  public abstract String mainType();

  /** Returns the qValue of this media-range. */
  public abstract float qValue();

  /** Checks if this range matches a given media-type. */
  public abstract boolean matches(MediaType mediaType);

  /** Returns a Map of the parameters of this media-range. */
  public abstract Map<String, String> getParams();

  /** Returns a copy of this instance with a changed qValue. */
  public abstract MediaRange withQValue(float qValue);
}
