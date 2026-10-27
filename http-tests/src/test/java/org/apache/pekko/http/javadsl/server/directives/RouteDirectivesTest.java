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

package org.apache.pekko.http.javadsl.server.directives;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.apache.pekko.http.javadsl.model.*;
import org.apache.pekko.http.javadsl.marshalling.Marshaller;
import org.apache.pekko.http.javadsl.model.headers.Location;
import org.apache.pekko.http.javadsl.model.headers.RawHeader;
import org.apache.pekko.http.javadsl.server.Directives;
import org.apache.pekko.http.javadsl.testkit.JUnitJupiterRouteTest;
import org.apache.pekko.http.javadsl.testkit.TestRoute;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.util.ByteString;
import org.junit.jupiter.api.Test;

public class RouteDirectivesTest extends JUnitJupiterRouteTest {

  @Test
  public void testRedirection() {
    Uri targetUri = Uri.create("http://example.com");
    TestRoute route = testRoute(redirect(targetUri, StatusCodes.FOUND));

    route
        .run(HttpRequest.create())
        .assertStatusCode(302)
        .assertHeaderExists(Location.create(targetUri));
  }

  @Test
  public void testEntitySizeNoLimit() {
    TestRoute route =
        testRoute(
            path(
                "no-limit",
                () ->
                    extractEntity(
                        entity ->
                            extractMaterializer(
                                mat ->
                                    Directives.<ByteString>onSuccess(
                                        entity // fails to infer type parameter with some older
                                            // oracle JDK versions
                                            .withoutSizeLimit()
                                            .getDataBytes()
                                            .runWith(Sink.<ByteString>head(), mat),
                                        bytes -> complete(bytes.utf8String()))))));

    route
        .run(HttpRequest.create("/no-limit").withEntity("1234567890"))
        .assertStatusCode(StatusCodes.OK)
        .assertEntity("1234567890");
  }

  private TestRoute routeWithLimit() {
    return testRoute(
        path(
            "limit-5",
            () ->
                extractEntity(
                    entity ->
                        extractMaterializer(
                            mat ->
                                Directives.<ByteString>onSuccess(
                                    entity // fails to infer type parameter with some older oracle
                                        // JDK versions
                                        .withSizeLimit(5)
                                        .getDataBytes()
                                        .runWith(Sink.head(), mat),
                                    bytes -> complete(bytes.utf8String()))))));
  }

  @Test
  public void testEntitySizeWithinLimit() {
    TestRoute route = routeWithLimit();

    route
        .run(HttpRequest.create("/limit-5").withEntity("12345"))
        .assertStatusCode(StatusCodes.OK)
        .assertEntity("12345");
  }

  @Test
  public void testEntitySizeLargerThanLimit() {
    TestRoute route = routeWithLimit();

    route
        .run(HttpRequest.create("/limit-5").withEntity("1234567890"))
        .assertStatusCode(StatusCodes.CONTENT_TOO_LARGE)
        .assertEntity(
            "EntityStreamSizeException: incoming entity size (10) exceeded size limit (5 bytes)!"
                + " This may have been a parser limit (set via"
                + " `pekko.http.[server|client].parsing.max-content-length`), a decoder limit (set"
                + " via `pekko.http.routing.decode-max-size`), or a custom limit set with"
                + " `withSizeLimit`.");
  }

  @Test
  public void testRouteFromFunction() {
    TestRoute route =
        testRoute(
            handle(
                req ->
                    // CompletableFuture.completedStage isn't available until Java 9
                    CompletableFuture.supplyAsync(
                        () ->
                            HttpResponse.create()
                                .withEntity(HttpEntities.create(req.getUri().toString())))));

    route.run(HttpRequest.create("/foo")).assertEntity("http://example.com/foo");
  }

  @Test
  public void testRouteFromFailingFunction() {
    TestRoute route =
        testRoute(
            handle(
                req ->
                    // CompletableFuture.failedStage/failedFuture aren't available until Java 9
                    CompletableFuture.supplyAsync(
                        () -> {
                          throw new IllegalStateException("x");
                        })),
            complete(StatusCodes.IM_A_TEAPOT));

    route.run(HttpRequest.create("/foo")).assertEntity("There was an internal server error.");
  }

  @Test
  public void testRouteWhenLambdaThrows() {
    TestRoute route =
        testRoute(
            handle(
                req -> {
                  throw new IllegalStateException("x");
                }),
            complete(StatusCodes.IM_A_TEAPOT));

    route.run(HttpRequest.create("/foo")).assertEntity("There was an internal server error.");
  }

  @Test
  public void testCompleteAcceptsAnyIterableOfHeaderSubtype() {
    List<RawHeader> headers = List.of(RawHeader.create("x-a", "1"), RawHeader.create("x-b", "2"));

    iterablesOf(headers)
        .forEach(
            (shape, iterable) ->
                assertDoesNotThrow(
                    () -> assertCompleteOverloadsRespondWithHeaders(iterable),
                    "complete/entityToResponse with " + shape));
  }

  @Test
  public void testCompleteWithDifferentHeaderTypes() {
    Location location = Location.create("/elsewhere");
    List<HttpHeader> headers = List.of(location, RawHeader.create("x-a", "1"));

    testRoute(complete(StatusCodes.CREATED, headers, HttpEntities.create("created")))
        .run(HttpRequest.GET("/"))
        .assertStatusCode(StatusCodes.CREATED)
        .assertHeaderExists(location)
        .assertHeaderExists("x-a", "1")
        .assertEntity("created");
  }

  private void assertCompleteOverloadsRespondWithHeaders(Iterable<? extends HttpHeader> headers) {
    ResponseEntity responseEntity = HttpEntities.create("response");
    RequestEntity requestEntity = HttpEntities.create("request");

    TestRoute route =
        testRoute(
            path(
                "status-value",
                () ->
                    complete(StatusCodes.ACCEPTED, headers, "value", Marshaller.stringToEntity())),
            path("status-response", () -> complete(StatusCodes.ACCEPTED, headers, responseEntity)),
            path("status-request", () -> complete(StatusCodes.ACCEPTED, headers, requestEntity)),
            path("value", () -> complete(headers, "value", Marshaller.stringToEntity())),
            path("response", () -> complete(headers, responseEntity)),
            path("request", () -> complete(headers, requestEntity)),
            path(
                "marshaller",
                () ->
                    complete(
                        "value",
                        Marshaller.entityToResponse(
                            StatusCodes.ACCEPTED, headers, Marshaller.stringToEntity()))),
            path(
                "ok-marshaller",
                () ->
                    complete(
                        "value",
                        Marshaller.entityToOKResponse(headers, Marshaller.stringToEntity()))));

    String[][] expectations = {
      {"status-value", "202", "value"},
      {"status-response", "202", "response"},
      {"status-request", "202", "request"},
      {"value", "200", "value"},
      {"response", "200", "response"},
      {"request", "200", "request"},
      {"marshaller", "202", "value"},
      {"ok-marshaller", "200", "value"}
    };
    for (String[] e : expectations) {
      route
          .run(HttpRequest.GET("/" + e[0]))
          .assertStatusCode(Integer.parseInt(e[1]))
          .assertHeaderExists("x-a", "1")
          .assertHeaderExists("x-b", "2")
          .assertEntity(e[2]);
    }
  }

  /** The same elements as different kinds of {@link Iterable}, keyed by a description. */
  private static <T> Map<String, Iterable<T>> iterablesOf(List<T> items) {
    Map<String, Iterable<T>> shapes = new LinkedHashMap<>();
    shapes.put("ArrayList", new ArrayList<>(items));
    shapes.put("LinkedHashSet", new LinkedHashSet<>(items));
    shapes.put("ArrayDeque", new ArrayDeque<>(items));
    shapes.put("unmodifiable Collection", Collections.unmodifiableCollection(items));
    // not a java.util.Collection at all, only an Iterable
    shapes.put("non-Collection Iterable", items::iterator);
    return shapes;
  }
}
