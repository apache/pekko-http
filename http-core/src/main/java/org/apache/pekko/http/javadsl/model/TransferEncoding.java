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
import org.apache.pekko.http.javadsl.model.headers.EntityTagRanges;

import java.util.Map;

/** @see TransferEncodings for convenience access to often used values. */
public abstract class TransferEncoding {
  public abstract String name();

  public abstract Map<String, String> getParams();

  public static TransferEncoding createExtension(String name) {
    return new org.apache.pekko.http.scaladsl.model.TransferEncodings.Extension(
        name, Util.emptyMap);
  }

  public static TransferEncoding createExtension(String name, Map<String, String> params) {
    return new org.apache.pekko.http.scaladsl.model.TransferEncodings.Extension(
        name, Util.convertMapToScala(params));
  }
}
