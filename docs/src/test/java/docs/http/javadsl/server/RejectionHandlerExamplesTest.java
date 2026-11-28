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

import org.apache.pekko.http.javadsl.coding.Coder;
import org.apache.pekko.http.javadsl.model.ContentTypes;
import org.apache.pekko.http.javadsl.model.HttpEntity;
import org.apache.pekko.http.javadsl.model.HttpMethods;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.server.AuthorizationFailedRejection;
import org.apache.pekko.http.javadsl.server.MethodRejection;
import org.apache.pekko.http.javadsl.server.MissingCookieRejection;
import org.apache.pekko.http.javadsl.server.RejectionHandler;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.http.javadsl.server.ValidationRejection;
import org.apache.pekko.http.javadsl.testkit.JUnitJupiterRouteTest;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;

// #example1
import static org.apache.pekko.http.javadsl.server.Directives.complete;
import static org.apache.pekko.http.javadsl.server.Directives.decodeRequestWith;
import static org.apache.pekko.http.javadsl.server.Directives.get;
import static org.apache.pekko.http.javadsl.server.Directives.path;
import static org.apache.pekko.http.javadsl.server.Directives.post;
// #example1
// #custom-handler-example-java
import java.util.Arrays;
import org.apache.pekko.http.javadsl.model.HttpMethod;
import org.apache.pekko.http.javadsl.model.headers.Allow;

import static org.apache.pekko.http.javadsl.server.Directives.complete;
import static org.apache.pekko.http.javadsl.server.Directives.path;
import static org.apache.pekko.http.javadsl.server.Directives.handleRejections;
import static org.apache.pekko.http.javadsl.server.Directives.respondWithHeader;

// #custom-handler-example-java
// #example-json
import static org.apache.pekko.http.javadsl.server.Directives.complete;
import static org.apache.pekko.http.javadsl.server.Directives.path;
import static org.apache.pekko.http.javadsl.server.Directives.handleRejections;
import static org.apache.pekko.http.javadsl.server.Directives.validate;

// #example-json
public class RejectionHandlerExamplesTest extends JUnitJupiterRouteTest {

  @Test
  public void compileOnlySpec() throws Exception {
    // just making sure for it to be really compiled / run even if empty
  }

  void example1() {
    // #example1
    final Route route =
        path(
            "order",
            () ->
                concat(
                    get(() -> complete("Received GET")),
                    post(
                        () ->
                            decodeRequestWith(
                                Coder.Gzip, () -> complete("Received compressed POST")))));
    // #example1
  }

  Route customRejectionHandler(Route route) {
    // #custom-handler-example-java
    final RejectionHandler rejectionHandler =
        RejectionHandler.newBuilder()
            .handle(
                MissingCookieRejection.class,
                rej -> complete(StatusCodes.BAD_REQUEST, "No cookies, no service!!!"))
            .handle(
                AuthorizationFailedRejection.class,
                rej -> complete(StatusCodes.FORBIDDEN, "You're out of your depth!"))
            .handle(
                ValidationRejection.class,
                rej ->
                    complete(
                        StatusCodes.INTERNAL_SERVER_ERROR, "That wasn't valid! " + rej.message()))
            .handleAll(
                MethodRejection.class,
                rejections -> {
                  HttpMethod[] methods =
                      rejections.stream()
                          .map(MethodRejection::supported)
                          .toArray(HttpMethod[]::new);
                  String supported =
                      Arrays.stream(methods)
                          .map(HttpMethod::name)
                          .collect(Collectors.joining(" or "));
                  return respondWithHeader(
                      Allow.create(methods),
                      () ->
                          complete(
                              StatusCodes.METHOD_NOT_ALLOWED,
                              "Can't do that! Supported: " + supported + "!"));
                })
            .handleNotFound(complete(StatusCodes.NOT_FOUND, "Not here!"))
            .build();

    // Wrap the application route with this handler.
    final Route wrapped = handleRejections(rejectionHandler, () -> route);
    // #custom-handler-example-java
    return wrapped;
  }

  @Test
  public void customMethodRejectionIncludesAllow() {
    Route route =
        concat(get(() -> complete("Received GET")), post(() -> complete("Received POST")));

    testRoute(customRejectionHandler(route))
        .run(HttpRequest.PUT("/"))
        .assertStatusCode(StatusCodes.METHOD_NOT_ALLOWED)
        .assertHeaderExists(Allow.create(HttpMethods.GET, HttpMethods.POST))
        .assertContentType(ContentTypes.TEXT_PLAIN_UTF8)
        .assertEntity("Can't do that! Supported: GET or POST!");
  }

  @Test
  public void customRejectionResponse() {
    // #example-json
    final RejectionHandler rejectionHandler =
        RejectionHandler.defaultHandler()
            .mapRejectionResponse(
                response -> {
                  if (response.entity() instanceof HttpEntity.Strict entity) {
                    // since all Pekko default rejection responses are Strict this will handle all
                    // rejections
                    String message = entity.getData().utf8String().replaceAll("\"", "\\\"");
                    // we create a new copy the response in order to keep all headers and status
                    // code,
                    // replacing the original entity with a custom message as hand rolled JSON you
                    // could the
                    // entity using your favourite marshalling library (e.g. spray json or anything
                    // else)
                    return response.withEntity(
                        ContentTypes.APPLICATION_JSON, "{\"rejection\": \"" + message + "\"}");
                  } else {
                    // pass through all other types of responses
                    return response;
                  }
                });

    Route route =
        handleRejections(rejectionHandler, () -> path("hello", () -> complete("Hello there")));

    // tests:
    testRoute(route)
        .run(HttpRequest.GET("/nope"))
        .assertStatusCode(StatusCodes.NOT_FOUND)
        .assertContentType(ContentTypes.APPLICATION_JSON)
        .assertEntity("{\"rejection\": \"The requested resource could not be found.\"}");

    Route anotherOne =
        handleRejections(
            rejectionHandler,
            () -> validate(() -> false, "Whoops, bad request!", () -> complete("Hello there")));

    // tests:
    testRoute(anotherOne)
        .run(HttpRequest.GET("/hello"))
        .assertStatusCode(StatusCodes.BAD_REQUEST)
        .assertContentType(ContentTypes.APPLICATION_JSON)
        .assertEntity("{\"rejection\": \"Whoops, bad request!\"}");
    // #example-json
  }
}
