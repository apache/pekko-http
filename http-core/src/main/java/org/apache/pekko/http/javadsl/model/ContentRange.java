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

import org.apache.pekko.http.scaladsl.model.ContentRange$;

import java.util.Optional;
import java.util.OptionalLong;
import org.apache.pekko.util.OptionalUtil;

public abstract class ContentRange {
  public abstract boolean isByteContentRange();

  public abstract boolean isSatisfiable();

  public abstract boolean isOther();

  public abstract OptionalLong getSatisfiableFirst();

  public abstract OptionalLong getSatisfiableLast();

  public abstract Optional<String> getOtherValue();

  public abstract OptionalLong getInstanceLength();

  public static ContentRange create(long first, long last) {
    return ContentRange$.MODULE$.apply(first, last);
  }

  public static ContentRange create(long first, long last, long instanceLength) {
    return ContentRange$.MODULE$.apply(first, last, instanceLength);
  }

  @SuppressWarnings("unchecked")
  public static ContentRange create(long first, long last, OptionalLong instanceLength) {
    return ContentRange$.MODULE$.apply(
        first, last, OptionalUtil.convertOptionalToScala(instanceLength));
  }

  public static ContentRange createUnsatisfiable(long length) {
    return new org.apache.pekko.http.scaladsl.model.ContentRange.Unsatisfiable(length);
  }

  public static ContentRange createOther(String value) {
    return new org.apache.pekko.http.scaladsl.model.ContentRange.Other(value);
  }
}
