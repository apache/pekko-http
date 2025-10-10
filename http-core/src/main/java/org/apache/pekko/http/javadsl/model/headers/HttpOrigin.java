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

import org.apache.pekko.http.scaladsl.model.headers.HttpOrigin$;

public abstract class HttpOrigin {
  public abstract String scheme();

  public abstract Host host();

  public static HttpOrigin create(String scheme, Host host) {
    return new org.apache.pekko.http.scaladsl.model.headers.HttpOrigin(
        scheme, (org.apache.pekko.http.scaladsl.model.headers.Host) host);
  }

  public static HttpOrigin parse(String originString) {
    return HttpOrigin$.MODULE$.apply(originString);
  }
}
