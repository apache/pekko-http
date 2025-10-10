/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model;

import org.apache.pekko.annotation.DoNotInherit;

@DoNotInherit
public abstract class AttributeKey<T> {
  public static <U> AttributeKey<U> create(String name, Class<U> clazz) {
    return new org.apache.pekko.http.scaladsl.model.AttributeKey<U>(name, clazz);
  }
}
