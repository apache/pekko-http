/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl.model.headers;

/**
 * The Cookie SameSite attribute as defined by <a
 * href="https://tools.ietf.org/html/draft-ietf-httpbis-cookie-same-site-00">RFC6265bis</a> and <a
 * href="https://tools.ietf.org/html/draft-west-cookie-incrementalism-00">Incrementally Better
 * Cookies</a>.
 */
public enum SameSite {
  Strict,
  Lax,
  // SameSite.None is different from not adding the SameSite attribute in a cookie.
  // - Cookies without a SameSite attribute will be treated as SameSite=Lax.
  // - Cookies for cross-site usage must specify `SameSite=None; Secure` to enable inclusion in
  // third party
  //   context. We are not enforcing `; Secure` when `SameSite=None`, but users should.
  None;

  public org.apache.pekko.http.scaladsl.model.headers.SameSite asScala() {
    if (this == Strict)
      return org.apache.pekko.http.scaladsl.model.headers.SameSite.Strict$.MODULE$;
    if (this == Lax) return org.apache.pekko.http.scaladsl.model.headers.SameSite.Lax$.MODULE$;
    return org.apache.pekko.http.scaladsl.model.headers.SameSite.None$.MODULE$;
  }
}
