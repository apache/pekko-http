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

import javax.net.ssl.SSLSession;

/**
 * Model for the synthetic `Tls-Session-Info` header which carries the SSLSession of the connection
 * the message carrying this header was received with.
 *
 * <p>This header will only be added if it enabled in the configuration by setting <code>
 * pekko.http.[client|server].parsing.tls-session-info-header = on</code>.
 */
public abstract class TlsSessionInfo extends CustomHeader {
  /** @return the SSLSession this message was received over. */
  public abstract SSLSession getSession();

  public static TlsSessionInfo create(SSLSession session) {
    return org.apache.pekko.http.scaladsl.model.headers.Tls$minusSession$minusInfo$.MODULE$.apply(
        session);
  }
}
