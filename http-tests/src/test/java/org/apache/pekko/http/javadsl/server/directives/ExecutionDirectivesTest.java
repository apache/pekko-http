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

import org.apache.pekko.http.javadsl.model.ContentTypes;
import org.apache.pekko.http.javadsl.model.HttpEntity;
import org.junit.Test;

import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.server.ExceptionHandler;
import org.apache.pekko.http.javadsl.server.RejectionHandler;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.http.javadsl.unmarshalling.StringUnmarshallers;
import org.apache.pekko.http.javadsl.testkit.JUnitRouteTest;
import org.apache.pekko.http.javadsl.testkit.TestRoute;
import org.apache.pekko.http.scaladsl.server.MethodRejection;
import org.apache.pekko.http.scaladsl.server.Rejection;

import static org.apache.pekko.http.javadsl.server.Directives.*;

public class ExecutionDirectivesTest extends JUnitRouteTest {
  @Test
  public void testCatchExceptionThrownFromHandler() {
    Route divide =
        path(
            "divide",
            () ->
                parameter(
                    StringUnmarshallers.INTEGER,
                    "a",
                    a ->
                        parameter(
                            StringUnmarshallers.INTEGER,
                            "b",
                            b -> complete("The result is: " + (a / b)))));

    ExceptionHandler handleDivByZero =
        ExceptionHandler.newBuilder()
            .match(
                ArithmeticException.class,
                t ->
                    complete(
                        StatusCodes.BAD_REQUEST,
                        "Congratulations you provoked a division by zero!"))
            .build();

    TestRoute route = testRoute(handleExceptions(handleDivByZero, () -> divide));

    route.run(HttpRequest.GET("/divide?a=10&b=5")).assertEntity("The result is: 2");

    route
        .run(HttpRequest.GET("/divide?a=10&b=0"))
        .assertStatusCode(StatusCodes.BAD_REQUEST)
        .assertEntity("Congratulations you provoked a division by zero!");
  }

  @Test
  public void testHandleMethodRejection() {
    RejectionHandler rejectionHandler =
        RejectionHandler.newBuilder()
            .handle(
                MethodRejection.class,
                r ->
                    complete(
                        StatusCodes.BAD_REQUEST,
                        "Whoopsie! Unsupported method. Supported would have been "
                            + r.supported().value()))
            .build();

    TestRoute route =
        testRoute(handleRejections(rejectionHandler, () -> get(() -> complete("Successful!"))));

    route.run(HttpRequest.GET("/")).assertStatusCode(StatusCodes.OK).assertEntity("Successful!");

    route
        .run(HttpRequest.POST("/"))
        .assertStatusCode(StatusCodes.BAD_REQUEST)
        .assertEntity("Whoopsie! Unsupported method. Supported would have been GET");
  }

  public static final class TooManyRequestsRejection implements Rejection {
    public final String message;

    TooManyRequestsRejection(String message) {
      this.message = message;
    }
  }

  private final Route testRoute =
      extractUri(
          uri -> {
            if (uri.path().startsWith("/test")) return complete("Successful!");
            else return reject(new TooManyRequestsRejection("Too many requests for busy path!"));
          });

  @Test
  public void testHandleCustomRejection() {
    RejectionHandler rejectionHandler =
        RejectionHandler.newBuilder()
            .handle(
                TooManyRequestsRejection.class,
                rej -> complete(StatusCodes.TOO_MANY_REQUESTS, rej.message))
            .build();

    TestRoute route = testRoute(handleRejections(rejectionHandler, () -> testRoute));

    route.run(HttpRequest.GET("/test")).assertStatusCode(StatusCodes.OK);

    route
        .run(HttpRequest.GET("/other"))
        .assertStatusCode(StatusCodes.TOO_MANY_REQUESTS)
        .assertEntity("Too many requests for busy path!");
  }

  @Test
  public void testHandleCustomRejectionResponse() {
    final RejectionHandler rejectionHandler =
        RejectionHandler.defaultHandler()
            .mapRejectionResponse(
                response -> {
                  if (response.entity() instanceof HttpEntity.Strict entity) {
                    String message =
                        entity
                            .getData()
                            .utf8String()
                            .replaceAll("\"", "\\\"");
                    return response.withEntity(
                        ContentTypes.APPLICATION_JSON, "{\"rejection\": \"" + message + "\"}");
                  } else {
                    return response;
                  }
                });

    Route route =
        handleRejections(rejectionHandler, () -> path("hello", () -> complete("Hello there")));

    testRoute(route)
        .run(HttpRequest.GET("/nope"))
        .assertStatusCode(StatusCodes.NOT_FOUND)
        .assertContentType(ContentTypes.APPLICATION_JSON)
        .assertEntity("{\"rejection\": \"The requested resource could not be found.\"}");

    Route anotherOne =
        handleRejections(
            rejectionHandler,
            () -> validate(() -> false, "Whoops, bad request!", () -> complete("Hello there")));

    testRoute(anotherOne)
        .run(HttpRequest.GET("/hello"))
        .assertStatusCode(StatusCodes.BAD_REQUEST)
        .assertContentType(ContentTypes.APPLICATION_JSON)
        .assertEntity("{\"rejection\": \"Whoops, bad request!\"}");
  }
}
