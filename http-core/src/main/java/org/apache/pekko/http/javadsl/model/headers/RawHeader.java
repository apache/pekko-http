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

import org.apache.pekko.annotation.DoNotInherit;

/**
 * A header in its 'raw' name/value form, not parsed into a modelled header class. To add a custom
 * header type, implement {@link ModeledCustomHeader} and {@link ModeledCustomHeaderFactory} rather
 * than extending {@link RawHeader}
 */
@DoNotInherit
public abstract class RawHeader extends org.apache.pekko.http.scaladsl.model.HttpHeader {
  public abstract String name();

  public abstract String value();

  public static RawHeader create(String name, String value) {
    return new org.apache.pekko.http.scaladsl.model.headers.RawHeader(name, value);
  }
}
