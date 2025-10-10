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
import org.apache.pekko.http.javadsl.server.AllDirectives;
import org.apache.pekko.http.javadsl.server.PathMatcher1;
import org.apache.pekko.http.javadsl.server.Route;

import static org.apache.pekko.http.javadsl.common.PartialApplication.*;
import static org.apache.pekko.http.javadsl.server.PathMatchers.*;
import static org.apache.pekko.http.javadsl.server.Directives.*;

import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;

public class ComposeDirectivesExampleTest extends AllDirectives {

  public static void main(String[] args) throws Exception {
    // boot up server using the route as defined below
    ActorSystem system = ActorSystem.create("routes");

    final Http http = Http.get(system);

    // In order to access all directives we need an instance where the routes are define.
    ComposeDirectivesExampleTest app = new ComposeDirectivesExampleTest();

    final CompletionStage<ServerBinding> binding =
        http.newServerAt("localhost", 8080).bind(app.createRoute());

    System.out.println("Server online at http://localhost:8080/\nPress RETURN to stop...");
    System.in.read(); // let it run until user presses return

    binding
        .thenCompose(ServerBinding::unbind) // trigger unbinding from the port
        .thenAccept(unbound -> system.terminate()); // and shutdown when done
  }

  private Route createRoute() {
    BiFunction<PathMatcher1<Integer>, Function<Integer, Route>, Route> pathWithInteger = this::path;

    return concat(
        // anyOf examples
        path(
            "hello",
            () -> anyOf(this::get, this::put, () -> complete("<h1>Say hello to pekko-http</h1>"))),
        path(
            "foo",
            () ->
                anyOf(
                    bindParameter(this::parameter, "foo"),
                    bindParameter(this::parameter, "bar"),
                    (String param) -> complete("param is " + param))),
        anyOf(
            bindParameter(this::path, "bar"),
            bindParameter(this::path, "baz"),
            () -> complete("bar - baz")),

        // allOf examples
        allOf(
            bindParameter(this::pathPrefix, "alice"),
            bindParameter(this::path, "bob"),
            () -> complete("Charlie!")),
        allOf(
            bindParameter(this::pathPrefix, "guess"),
            this::extractMethod,
            method -> complete("You did a " + method.name())),
        path(
            "two",
            () ->
                allOf(
                    this::extractScheme,
                    this::extractMethod,
                    (scheme, method) ->
                        complete("You did a " + method.name() + " using " + scheme))),
        allOf(
            bindParameter(this::pathPrefix, "number"),
            bindParameter(pathWithInteger, integerSegment()),
            x -> complete("Number is " + x)));
  }
}
