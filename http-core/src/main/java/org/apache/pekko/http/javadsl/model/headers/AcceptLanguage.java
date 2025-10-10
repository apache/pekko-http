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
 * Model for the `Accept-Language` header. Specification:
 * http://tools.ietf.org/html/draft-ietf-httpbis-p2-semantics-26#section-5.3.5
 */
public abstract class AcceptLanguage extends org.apache.pekko.http.scaladsl.model.HttpHeader {
  public abstract Iterable<LanguageRange> getLanguages();

  public static AcceptLanguage create(LanguageRange... languages) {
    return new org.apache.pekko.http.scaladsl.model.headers.Accept$minusLanguage(
        org.apache.pekko.http.impl.util.Util
            .<LanguageRange, org.apache.pekko.http.scaladsl.model.headers.LanguageRange>
                convertArray(languages));
  }
}
