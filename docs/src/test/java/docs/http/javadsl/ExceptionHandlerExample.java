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

// #explicit-handler-example

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.ServerBinding;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.server.AllDirectives;
import org.apache.pekko.http.javadsl.server.ExceptionHandler;
import org.apache.pekko.http.javadsl.server.PathMatchers;
import org.apache.pekko.http.javadsl.server.Route;

import java.util.concurrent.CompletionStage;

import static org.apache.pekko.http.javadsl.server.PathMatchers.integerSegment;

public class ExceptionHandlerExample extends AllDirectives {
  public static void main(String[] args) {
    final ActorSystem system = ActorSystem.create();
    final Http http = Http.get(system);

    final ExceptionHandlerExample app = new ExceptionHandlerExample();

    final CompletionStage<ServerBinding> binding =
        http.newServerAt("localhost", 8080).bind(app.createRoute());
  }

  public Route createRoute() {
    final ExceptionHandler divByZeroHandler =
        ExceptionHandler.newBuilder()
            .match(
                ArithmeticException.class,
                x -> complete(StatusCodes.BAD_REQUEST, "You've got your arithmetic wrong, fool!"))
            .build();

    return path(
        PathMatchers.segment("divide").slash(integerSegment()).slash(integerSegment()),
        (a, b) -> handleExceptions(divByZeroHandler, () -> complete("The result is " + (a / b))));
  }
}
// #explicit-handler-example
