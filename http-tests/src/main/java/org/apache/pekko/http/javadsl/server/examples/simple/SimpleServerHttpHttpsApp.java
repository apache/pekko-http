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

package org.apache.pekko.http.javadsl.server.examples.simple;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.HttpsConnectionContext;
import org.apache.pekko.http.javadsl.server.Route;

import static org.apache.pekko.http.javadsl.server.Directives.*;

import java.io.IOException;

public class SimpleServerHttpHttpsApp {

  public Route createRoute() {
    return get(() -> complete("Hello World!"));
  }

  // ** STARTING THE SERVER ** //

  public static void main(String[] args) throws IOException {
    final ActorSystem system = ActorSystem.create("SimpleServerHttpHttpsApp");

    final SimpleServerApp app = new SimpleServerApp();
    final Route route = app.createRoute();

    // #both-https-and-http
    final Http http = Http.get(system);
    // Run HTTP server firstly
    http.newServerAt("localhost", 80).bind(route);

    // get configured HTTPS context
    HttpsConnectionContext httpsContext = SimpleServerApp.createHttpsContext(system);

    // Then run HTTPS server
    http.newServerAt("localhost", 443).enableHttps(httpsContext).bind(route);
    // #both-https-and-http

    System.out.println("Type RETURN to exit");
    System.in.read();
    system.terminate();
  }
}
