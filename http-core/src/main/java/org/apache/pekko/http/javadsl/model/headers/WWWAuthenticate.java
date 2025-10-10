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
 * Model for the `WWW-Authenticate` header. Specification:
 * http://tools.ietf.org/html/draft-ietf-httpbis-p7-auth-26#section-4.1
 */
public abstract class WWWAuthenticate extends org.apache.pekko.http.scaladsl.model.HttpHeader {
  public abstract Iterable<HttpChallenge> getChallenges();

  public static WWWAuthenticate create(HttpChallenge... challenges) {
    return new org.apache.pekko.http.scaladsl.model.headers.WWW$minusAuthenticate(
        org.apache.pekko.http.impl.util.Util
            .<HttpChallenge, org.apache.pekko.http.scaladsl.model.headers.HttpChallenge>
                convertArray(challenges));
  }
}
