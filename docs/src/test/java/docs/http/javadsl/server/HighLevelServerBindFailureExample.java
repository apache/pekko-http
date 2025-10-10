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

// #binding-failure-high-level-example

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.ServerBinding;
import org.apache.pekko.http.javadsl.server.Route;

import java.io.IOException;
import java.util.concurrent.CompletionStage;

public class HighLevelServerBindFailureExample {
  public static void main(String[] args) throws IOException {
    // boot up server using the route as defined below
    final ActorSystem system = ActorSystem.create();

    final HighLevelServerExample app = new HighLevelServerExample();
    final Route route = app.createRoute();

    final CompletionStage<ServerBinding> binding =
        Http.get(system).newServerAt("127.0.0.1", 8080).bind(route);

    binding.exceptionally(
        failure -> {
          System.err.println("Something very bad happened! " + failure.getMessage());
          system.terminate();
          return null;
        });

    system.terminate();
  }
}
// #binding-failure-high-level-example
