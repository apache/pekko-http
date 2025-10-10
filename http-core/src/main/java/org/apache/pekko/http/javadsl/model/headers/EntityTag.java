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

public abstract class EntityTag {
  public abstract String tag();

  public abstract boolean weak();

  public static EntityTag create(String tag, boolean weak) {
    return new org.apache.pekko.http.scaladsl.model.headers.EntityTag(tag, weak);
  }

  public static boolean matchesRange(EntityTag eTag, EntityTagRange range, boolean weak) {
    return org.apache.pekko.http.scaladsl.model.headers.EntityTag.matchesRange(eTag, range, weak);
  }

  public static boolean matches(EntityTag eTag, EntityTag other, boolean weak) {
    return org.apache.pekko.http.scaladsl.model.headers.EntityTag.matches(eTag, other, weak);
  }
}
