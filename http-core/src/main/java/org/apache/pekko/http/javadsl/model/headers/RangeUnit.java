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

import org.apache.pekko.http.impl.util.CharUtils;

public abstract class RangeUnit {
  private static final String BYTES_NAME = "bytes";

  public abstract String name();

  /**
   * Returns the built-in byte range unit for an ASCII case-insensitive match of {@code bytes}.
   * Other names are used as given for extension units.
   */
  public static RangeUnit create(String name) {
    return isBytes(name)
        ? RangeUnits.BYTES
        : new org.apache.pekko.http.scaladsl.model.headers.RangeUnits.Other(name);
  }

  private static boolean isBytes(String name) {
    if (name == null || name.length() != BYTES_NAME.length()) {
      return false;
    }
    for (int i = 0; i < BYTES_NAME.length(); i++) {
      if (CharUtils.toLowerCase(name.charAt(i)) != BYTES_NAME.charAt(i)) {
        return false;
      }
    }
    return true;
  }
}
