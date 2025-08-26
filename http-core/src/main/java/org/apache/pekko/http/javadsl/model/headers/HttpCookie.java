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
import org.apache.pekko.http.javadsl.model.DateTime;
import org.apache.pekko.http.impl.util.Util;
import org.apache.pekko.util.OptionConverters;

import java.util.Optional;
import java.util.OptionalLong;

@DoNotInherit
public abstract class HttpCookie {
  public abstract String name();

  public abstract String value();

  public abstract HttpCookiePair pair();

  public abstract Optional<DateTime> getExpires();

  public abstract OptionalLong getMaxAge();

  public abstract Optional<String> getDomain();

  public abstract Optional<String> getPath();

  public abstract boolean secure();

  public abstract boolean httpOnly();

  public abstract Optional<String> getExtension();

  public abstract Optional<SameSite> getSameSite();

  public static HttpCookie create(String name, String value) {
    return new org.apache.pekko.http.scaladsl.model.headers.HttpCookie(
        name,
        value,
        Util.<org.apache.pekko.http.scaladsl.model.DateTime>scalaNone(),
        Util.scalaNone(),
        Util.<String>scalaNone(),
        Util.<String>scalaNone(),
        false,
        false,
        Util.<String>scalaNone(),
        Util.<org.apache.pekko.http.scaladsl.model.headers.SameSite>scalaNone());
  }

  public static HttpCookie create(
      String name, String value, Optional<String> domain, Optional<String> path) {
    return new org.apache.pekko.http.scaladsl.model.headers.HttpCookie(
        name,
        value,
        Util.<org.apache.pekko.http.scaladsl.model.DateTime>scalaNone(),
        Util.scalaNone(),
        OptionConverters.toScala(domain),
        OptionConverters.toScala(path),
        false,
        false,
        Util.<String>scalaNone(),
        Util.<org.apache.pekko.http.scaladsl.model.headers.SameSite>scalaNone());
  }

  @SuppressWarnings("unchecked")
  public static HttpCookie create(
      String name,
      String value,
      Optional<DateTime> expires,
      OptionalLong maxAge,
      Optional<String> domain,
      Optional<String> path,
      boolean secure,
      boolean httpOnly,
      Optional<String> extension,
      Optional<SameSite> sameSite) {
    return new org.apache.pekko.http.scaladsl.model.headers.HttpCookie(
        name,
        value,
        Util.<DateTime, org.apache.pekko.http.scaladsl.model.DateTime>convertOptionalToScala(
            expires),
        Util.convertOptionalToScala(maxAge),
        OptionConverters.toScala(domain),
        OptionConverters.toScala(path),
        secure,
        httpOnly,
        OptionConverters.toScala(extension),
        OptionConverters.toScala(sameSite.map(SameSite::asScala)));
  }

  /** Returns a copy of this HttpCookie instance with the given expiration set. */
  public abstract HttpCookie withExpires(DateTime dateTime);

  /** Returns a copy of this HttpCookie instance with the given max age set. */
  public abstract HttpCookie withMaxAge(long maxAge);

  /** Returns a copy of this HttpCookie instance with the given domain set. */
  public abstract HttpCookie withDomain(String domain);

  /** Returns a copy of this HttpCookie instance with the given path set. */
  public abstract HttpCookie withPath(String path);

  /** Returns a copy of this HttpCookie instance with the given secure flag set. */
  public abstract HttpCookie withSecure(boolean secure);

  /** Returns a copy of this HttpCookie instance with the given http-only flag set. */
  public abstract HttpCookie withHttpOnly(boolean httpOnly);

  /** Returns a copy of this HttpCookie instance with the given {@link SameSite} set. */
  public abstract HttpCookie withSameSite(SameSite sameSite);

  /** Returns a copy of this HttpCookie instance with the given Optional {@link SameSite} set. */
  public abstract HttpCookie withSameSite(Optional<SameSite> sameSite);

  /** Returns a copy of this HttpCookie instance with the given extension set. */
  public abstract HttpCookie withExtension(String extension);
}
