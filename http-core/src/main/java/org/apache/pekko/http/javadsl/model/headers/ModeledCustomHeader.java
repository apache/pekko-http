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

import org.apache.pekko.http.impl.util.Rendering;
import org.apache.pekko.util.Helpers;

/**
 * Support class for building user-defined custom headers defined by implementing `name` and
 * `value`. Implement {@link ModeledCustomHeader} and {@link ModeledCustomHeaderFactory} instead of
 * {@link CustomHeader} to be able to use the convenience methods that allow parsing the custom
 * user-defined header from {@link org.apache.pekko.http.javadsl.model.HttpHeader}.
 */
public abstract class ModeledCustomHeader extends CustomHeader {

  private final String name;
  private final String value;

  protected ModeledCustomHeader(final String name, final String value) {
    super();
    this.name = name;
    this.value = value;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public String lowercaseName() {
    return Helpers.toRootLowerCase(name);
  }

  @Override
  public String value() {
    return value;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <R extends Rendering> R render(R r) {
    return (R) r.$tilde$tilde(name).$tilde$tilde(':').$tilde$tilde(' ').$tilde$tilde(value);
  }
}
