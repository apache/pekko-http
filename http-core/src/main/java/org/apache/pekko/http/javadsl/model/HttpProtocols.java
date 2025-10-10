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

/** Contains constants of the supported Http protocols. */
public final class HttpProtocols {
  private HttpProtocols() {}

  public static final HttpProtocol HTTP_1_0 =
      org.apache.pekko.http.scaladsl.model.HttpProtocols.HTTP$div1$u002E0();
  public static final HttpProtocol HTTP_1_1 =
      org.apache.pekko.http.scaladsl.model.HttpProtocols.HTTP$div1$u002E1();
}
