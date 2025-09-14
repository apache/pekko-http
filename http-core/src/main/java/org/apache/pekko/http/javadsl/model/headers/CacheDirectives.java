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

import java.util.Optional;
import java.util.OptionalLong;

import scala.jdk.javaapi.OptionConverters;

import org.apache.pekko.http.impl.util.Util;

public final class CacheDirectives {
  private CacheDirectives() {}

  public static CacheDirective MAX_AGE(long deltaSeconds) {
    return new org.apache.pekko.http.scaladsl.model.headers.CacheDirectives.max$minusage(
        deltaSeconds);
  }

  public static CacheDirective MAX_STALE() {
    return new org.apache.pekko.http.scaladsl.model.headers.CacheDirectives.max$minusstale(
        OptionConverters.toScala(Optional.empty()));
  }

  public static CacheDirective MAX_STALE(long deltaSeconds) {
    return new org.apache.pekko.http.scaladsl.model.headers.CacheDirectives.max$minusstale(
        new scala.Some(deltaSeconds));
  }

  public static CacheDirective MIN_FRESH(long deltaSeconds) {
    return new org.apache.pekko.http.scaladsl.model.headers.CacheDirectives.min$minusfresh(
        deltaSeconds);
  }

  public static final CacheDirective NO_CACHE =
      org.apache.pekko.http.scaladsl.model.headers.CacheDirectives.no$minuscache$.MODULE$;
  public static final CacheDirective NO_STORE =
      org.apache.pekko.http.scaladsl.model.headers.CacheDirectives.no$minusstore$.MODULE$;
  public static final CacheDirective NO_TRANSFORM =
      org.apache.pekko.http.scaladsl.model.headers.CacheDirectives.no$minustransform$.MODULE$;
  public static final CacheDirective ONLY_IF_CACHED =
      org.apache.pekko.http.scaladsl.model.headers.CacheDirectives.only$minusif$minuscached$
          .MODULE$;
  public static final CacheDirective MUST_REVALIDATE =
      org.apache.pekko.http.scaladsl.model.headers.CacheDirectives.must$minusrevalidate$.MODULE$;

  public static CacheDirective NO_CACHE(String... fieldNames) {
    return org.apache.pekko.http.scaladsl.model.headers.CacheDirectives.no$minuscache$.MODULE$
        .apply(org.apache.pekko.japi.Util.immutableSeq(fieldNames));
  }

  public static final CacheDirective PUBLIC =
      org.apache.pekko.http.scaladsl.model.headers.CacheDirectives.getPublic();

  public static CacheDirective PRIVATE(String... fieldNames) {
    return org.apache.pekko.http.scaladsl.model.headers.CacheDirectives.createPrivate(fieldNames);
  }

  public static final CacheDirective PROXY_REVALIDATE =
      org.apache.pekko.http.scaladsl.model.headers.CacheDirectives.proxy$minusrevalidate$.MODULE$;

  public static CacheDirective S_MAXAGE(long deltaSeconds) {
    return new org.apache.pekko.http.scaladsl.model.headers.CacheDirectives.s$minusmaxage(
        deltaSeconds);
  }

  public static final CacheDirective IMMUTABLE =
      org.apache.pekko.http.scaladsl.model.headers.CacheDirectives.getImmutable();
}
