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

package org.apache.pekko.http.javadsl.testkit;

import java.util.function.Function;
import java.util.concurrent.CompletableFuture;

import org.junit.Test;
import org.apache.pekko.http.javadsl.testkit.*;

import org.apache.pekko.http.javadsl.model.*;
import org.apache.pekko.http.javadsl.model.headers.*;
import org.apache.pekko.http.javadsl.server.Rejections;

public class JUnitRouteTestTest extends JUnitRouteTest {

  @Test
  public void testTheMostSimpleAndDirectRouteTest() {
    TestRoute route = testRoute(complete(HttpResponse.create()));

    route.run(HttpRequest.GET("/")).assertStatusCode(StatusCodes.OK);
  }

  @Test
  public void testUsingADirectiveAndSomeChecks() {
    RawHeader pinkHeader = RawHeader.create("Fancy", "pink");

    TestRoute route = testRoute(respondWithHeader(pinkHeader, () -> complete("abc")));

    route
        .run(HttpRequest.GET("/").addHeader(pinkHeader))
        .assertStatusCode(StatusCodes.OK)
        .assertContentType("text/plain; charset=UTF-8")
        .assertEntity("abc")
        .assertHeaderExists("Fancy", "pink");
  }

  @Test
  public void testUsingADirectiveAndSomeChecksAndRunClientServer() {
    RawHeader extraHeader = RawHeader.create("X-Forwarded-Proto", "abc");

    TestRoute route = testRoute(respondWithHeader(extraHeader, () -> complete("abc")));

    route
        .runClientServer(HttpRequest.GET("/").addHeader(extraHeader))
        .assertStatusCode(StatusCodes.OK)
        .assertContentType("text/plain; charset=UTF-8")
        .assertEntity("abc")
        .assertHeaderExists(XForwardedProto.create("abc"));
  }

  @Test
  public void testProperRejectionCollection() {
    TestRoute route = testRoute(get(() -> complete("naah")).orElse(put(() -> complete("naah"))));

    route
        .runWithRejections(HttpRequest.POST("/abc").withEntity("content"))
        .assertRejections(Rejections.method(HttpMethods.GET), Rejections.method(HttpMethods.PUT));
  }

  @Test
  public void testSeparationOfRouteExecutionFromChecking() {
    RawHeader pinkHeader = RawHeader.create("Fancy", "pink");
    CompletableFuture<String> promise = new CompletableFuture<>();
    TestRoute route =
        testRoute(
            respondWithHeader(pinkHeader, () -> onSuccess(promise, result -> complete(result))));

    TestRouteResult result = route.run(HttpRequest.GET("/").addHeader(pinkHeader));

    promise.complete("abc");

    result
        .assertStatusCode(StatusCodes.OK)
        .assertContentType("text/plain; charset=UTF-8")
        .assertEntity("abc")
        .assertHeaderExists("Fancy", "pink");
  }
}
