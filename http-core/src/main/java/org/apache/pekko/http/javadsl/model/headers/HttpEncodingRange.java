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

import org.apache.pekko.http.scaladsl.model.headers.HttpEncodingRange$;

/** @see HttpEncodingRanges for convenience access to often used values. */
public abstract class HttpEncodingRange {
  public abstract float qValue();

  public abstract boolean matches(HttpEncoding encoding);

  public abstract HttpEncodingRange withQValue(float qValue);

  public static HttpEncodingRange create(HttpEncoding encoding) {
    return HttpEncodingRange$.MODULE$.apply(
        (org.apache.pekko.http.scaladsl.model.headers.HttpEncoding) encoding);
  }
}
