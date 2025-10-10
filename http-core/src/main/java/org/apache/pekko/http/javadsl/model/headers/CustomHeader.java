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

/**
 * The model of an HTTP header. In its most basic form headers are simple name-value pairs. Header
 * names are compared in a case-insensitive way.
 *
 * <p>Implement {@link ModeledCustomHeader} and {@link ModeledCustomHeaderFactory} instead of {@link
 * CustomHeader} to be able to use the convenience methods that allow parsing the custom
 * user-defined header from {@link org.apache.pekko.http.javadsl.model.HttpHeader}.
 */
public abstract class CustomHeader extends org.apache.pekko.http.scaladsl.model.HttpHeader {
  public abstract String name();

  public abstract String value();
}
