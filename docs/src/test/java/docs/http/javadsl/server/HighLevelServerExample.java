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

// #high-level-server-example

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.ServerBinding;
import org.apache.pekko.http.javadsl.model.ContentTypes;
import org.apache.pekko.http.javadsl.model.HttpEntities;
import org.apache.pekko.http.javadsl.server.AllDirectives;
import org.apache.pekko.http.javadsl.server.Route;

import java.io.IOException;
import java.util.concurrent.CompletionStage;

public class HighLevelServerExample extends AllDirectives {
  public static void main(String[] args) throws IOException {
    // boot up server using the route as defined below
    ActorSystem system = ActorSystem.create();

    final HighLevelServerExample app = new HighLevelServerExample();

    final Http http = Http.get(system);

    final CompletionStage<ServerBinding> binding =
        http.newServerAt("localhost", 8080).bind(app.createRoute());

    System.out.println("Type RETURN to exit");
    System.in.read();

    binding.thenCompose(ServerBinding::unbind).thenAccept(unbound -> system.terminate());
  }

  public Route createRoute() {
    // This handler generates responses to `/hello?name=XXX` requests
    Route helloRoute =
        parameterOptional(
            "name",
            optName -> {
              String name = optName.orElse("Mister X");
              return complete("Hello " + name + "!");
            });

    return
    // here the complete behavior for this server is defined

    // only handle GET requests
    get(
        () ->
            concat(
                // matches the empty path
                pathSingleSlash(
                    () ->
                        // return a constant string with a certain content type
                        complete(
                            HttpEntities.create(
                                ContentTypes.TEXT_HTML_UTF8,
                                "<html><body>Hello world!</body></html>"))),
                path(
                    "ping",
                    () ->
                        // return a simple `text/plain` response
                        complete("PONG!")),
                path(
                    "hello",
                    () ->
                        // uses the route defined above
                        helloRoute)));
  }
}
// #high-level-server-example
