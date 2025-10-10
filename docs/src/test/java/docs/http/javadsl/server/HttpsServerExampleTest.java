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

package docs.http.javadsl.server;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.ConnectionContext;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

/* COMPILE ONLY TEST */
public class HttpsServerExampleTest extends JUnitSuite {

  @Test
  public void compileOnlySpec() throws Exception {
    // just making sure for it to be really compiled / run even if empty
  }

  void requireClientAuth() {
    final ActorSystem system = ActorSystem.create();
    SSLContext sslContext = null;
    // #require-client-auth
    ConnectionContext.httpsServer(
        () -> {
          SSLEngine engine = sslContext.createSSLEngine();
          engine.setUseClientMode(false);

          engine.setNeedClientAuth(true);
          // or: engine.setWantClientAuth(true);

          return engine;
        });
    // #require-client-auth
  }
}
