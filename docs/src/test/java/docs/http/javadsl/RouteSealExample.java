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

package docs.http.javadsl;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.ServerBinding;
import org.apache.pekko.http.javadsl.model.headers.RawHeader;
import org.apache.pekko.http.javadsl.server.AllDirectives;
import org.apache.pekko.http.javadsl.server.Route;

import java.util.concurrent.CompletionStage;

// #route-seal-example
public class RouteSealExample extends AllDirectives {

  public static void main(String[] args) {
    RouteSealExample app = new RouteSealExample();
    app.runServer();
  }

  public void runServer() {
    ActorSystem system = ActorSystem.create();

    Route sealedRoute = get(() -> pathSingleSlash(() -> complete("Captain on the bridge!"))).seal();

    Route route =
        respondWithHeader(
            RawHeader.create("special-header", "you always have this even in 404"),
            () -> sealedRoute);

    final Http http = Http.get(system);
    final CompletionStage<ServerBinding> binding = http.newServerAt("localhost", 8080).bind(route);
  }
}
// #route-seal-example
