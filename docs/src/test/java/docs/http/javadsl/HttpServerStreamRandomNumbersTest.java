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

// #stream-random-numbers
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.ServerBinding;
import org.apache.pekko.http.javadsl.model.*;
import org.apache.pekko.http.javadsl.server.AllDirectives;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.util.ByteString;

import java.util.Random;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

public class HttpServerStreamRandomNumbersTest extends AllDirectives {

  public static void main(String[] args) throws Exception {
    // boot up server using the route as defined below
    ActorSystem<Void> system = ActorSystem.create(Behaviors.empty(), "routes");

    final Http http = Http.get(system);

    // In order to access all directives we need an instance where the routes are define.
    HttpServerStreamRandomNumbersTest app = new HttpServerStreamRandomNumbersTest();

    final CompletionStage<ServerBinding> binding =
        http.newServerAt("localhost", 8080).bind(app.createRoute());

    System.out.println("Server online at http://localhost:8080/\nPress RETURN to stop...");
    System.in.read(); // let it run until user presses return

    binding
        .thenCompose(ServerBinding::unbind) // trigger unbinding from the port
        .thenAccept(unbound -> system.terminate()); // and shutdown when done
  }

  private Route createRoute() {
    final Random rnd = new Random();
    // streams are re-usable so we can define it here
    // and use it for every request
    Source<Integer, NotUsed> numbers =
        Source.fromIterator(() -> Stream.generate(rnd::nextInt).iterator());

    return concat(
        path(
            "random",
            () ->
                get(
                    () ->
                        complete(
                            HttpEntities.create(
                                ContentTypes.TEXT_PLAIN_UTF8,
                                // transform each number to a chunk of bytes
                                numbers.map(x -> ByteString.fromString(x + "\n")))))));
  }
}
// #stream-random-numbers
