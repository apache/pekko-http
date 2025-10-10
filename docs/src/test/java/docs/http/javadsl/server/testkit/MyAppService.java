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

package docs.http.javadsl.server.testkit;

// #simple-app

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.server.AllDirectives;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.http.javadsl.server.examples.simple.SimpleServerApp;
import org.apache.pekko.http.javadsl.unmarshalling.StringUnmarshallers;

import java.io.IOException;

public class MyAppService extends AllDirectives {

  public String add(double x, double y) {
    return "x + y = " + (x + y);
  }

  public Route createRoute() {
    return get(
        () ->
            pathPrefix(
                "calculator",
                () ->
                    path(
                        "add",
                        () ->
                            parameter(
                                StringUnmarshallers.DOUBLE,
                                "x",
                                x ->
                                    parameter(
                                        StringUnmarshallers.DOUBLE,
                                        "y",
                                        y -> complete(add(x, y)))))));
  }

  public static void main(String[] args) throws IOException {
    final ActorSystem system = ActorSystem.create();

    final SimpleServerApp app = new SimpleServerApp();

    Http.get(system).newServerAt("127.0.0.1", 8080).bind(app.createRoute());

    System.console().readLine("Type RETURN to exit...");
    system.terminate();
  }
}
// #simple-app
