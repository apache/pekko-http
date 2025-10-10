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

package org.apache.pekko.http.javadsl.server;

import static org.apache.pekko.http.javadsl.unmarshalling.StringUnmarshallers.INTEGER;

import org.junit.Test;

import org.apache.pekko.http.javadsl.testkit.JUnitRouteTest;
import org.apache.pekko.http.javadsl.testkit.TestRouteResult;
import org.apache.pekko.http.scaladsl.model.HttpRequest;

import static org.apache.pekko.http.javadsl.server.Directives.*;

public class HandlerBindingTest extends JUnitRouteTest {

  @Test
  public void testHandlerWithoutExtractions() {
    Route route = complete("Ok");
    TestRouteResult response = runRoute(route, HttpRequest.GET("/"));
    response.assertEntity("Ok");
  }

  @Test
  public void testHandler1() {
    Route route = parameter("a", a -> complete("Ok " + a));
    TestRouteResult response = runRoute(route, HttpRequest.GET("?a=23"));
    response.assertStatusCode(200);
    response.assertEntity("Ok 23");
  }

  @Test
  public void testHandler2() {
    Route route =
        parameter(INTEGER, "a", a -> parameter(INTEGER, "b", b -> complete("Sum: " + (a + b))));
    TestRouteResult response = runRoute(route, HttpRequest.GET("?a=23&b=42"));
    response.assertStatusCode(200);
    response.assertEntity("Sum: 65");
  }

  public Route sum(int a, int b, int c, int d) {
    return complete("Sum: " + (a + b + c + d));
  }

  @Test
  public void testHandlerMethod() {
    Route route =
        parameter(
            INTEGER,
            "a",
            a ->
                parameter(
                    INTEGER,
                    "b",
                    b ->
                        parameter(
                            INTEGER, "c", c -> parameter(INTEGER, "d", d -> sum(a, b, c, d)))));
    TestRouteResult response = runRoute(route, HttpRequest.GET("?a=23&b=42&c=30&d=45"));
    response.assertStatusCode(200);
    response.assertEntity("Sum: 140");
  }
}
