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

import org.apache.pekko.http.impl.util.Util;

import java.util.Map;

/** Contains a set of predefined media-ranges and static methods to create custom ones. */
public final class MediaRanges {
  private MediaRanges() {}

  public static final MediaRange ALL =
      org.apache.pekko.http.scaladsl.model.MediaRanges.$times$div$times();
  public static final MediaRange ALL_APPLICATION =
      org.apache.pekko.http.scaladsl.model.MediaRanges.application$div$times();
  public static final MediaRange ALL_AUDIO =
      org.apache.pekko.http.scaladsl.model.MediaRanges.audio$div$times();
  public static final MediaRange ALL_IMAGE =
      org.apache.pekko.http.scaladsl.model.MediaRanges.image$div$times();
  public static final MediaRange ALL_MESSAGE =
      org.apache.pekko.http.scaladsl.model.MediaRanges.message$div$times();
  public static final MediaRange ALL_MULTIPART =
      org.apache.pekko.http.scaladsl.model.MediaRanges.multipart$div$times();
  public static final MediaRange ALL_TEXT =
      org.apache.pekko.http.scaladsl.model.MediaRanges.text$div$times();
  public static final MediaRange ALL_VIDEO =
      org.apache.pekko.http.scaladsl.model.MediaRanges.video$div$times();

  /** Creates a custom universal media-range for a given main-type. */
  public static MediaRange create(MediaType mediaType) {
    return org.apache.pekko.http.scaladsl.model.MediaRange.apply(
        (org.apache.pekko.http.scaladsl.model.MediaType) mediaType);
  }

  /** Creates a custom universal media-range for a given main-type and a Map of parameters. */
  public static MediaRange custom(String mainType, Map<String, String> parameters) {
    return org.apache.pekko.http.scaladsl.model.MediaRange.custom(
        mainType, Util.convertMapToScala(parameters), 1.0f);
  }

  /** Creates a custom universal media-range for a given main-type and qValue. */
  public static MediaRange create(MediaType mediaType, float qValue) {
    return org.apache.pekko.http.scaladsl.model.MediaRange.apply(
        (org.apache.pekko.http.scaladsl.model.MediaType) mediaType, qValue);
  }
}
