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

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.pekko.http.javadsl.marshalling.Marshaller;
import org.apache.pekko.http.javadsl.model.*;
import org.apache.pekko.http.javadsl.model.headers.*;
import org.apache.pekko.http.javadsl.unmarshalling.StringUnmarshallers;
import org.junit.Test;

import org.apache.pekko.http.javadsl.testkit.JUnitRouteTest;
import org.apache.pekko.http.javadsl.testkit.TestRoute;
import org.apache.pekko.util.ByteString;

import static org.apache.pekko.http.javadsl.server.Directives.*;

public class MarshallerTest extends JUnitRouteTest {

  @Test
  public void testCustomToStringMarshaller() {
    final Marshaller<Integer, RequestEntity> numberAsNameMarshaller =
        Marshaller.wrapEntity(
            (Integer param) -> {
              switch (param) {
                case 0:
                  return "null";
                case 1:
                  return "eins";
                case 2:
                  return "zwei";
                case 3:
                  return "drei";
                case 4:
                  return "vier";
                case 5:
                  return "fünf";
                default:
                  return "wat?";
              }
            },
            Marshaller.stringToEntity(),
            MediaTypes.TEXT_X_SPEECH);

    final Function<Integer, Route> nummerHandler =
        integer -> completeOK(integer, numberAsNameMarshaller);

    TestRoute route =
        testRoute(
            get(
                () ->
                    path(
                        "nummer",
                        () -> parameter(StringUnmarshallers.INTEGER, "n", nummerHandler))));

    route
        .run(HttpRequest.GET("/nummer?n=1"))
        .assertStatusCode(StatusCodes.OK)
        .assertMediaType(MediaTypes.TEXT_X_SPEECH)
        .assertEntity("eins");

    route
        .run(HttpRequest.GET("/nummer?n=6"))
        .assertStatusCode(StatusCodes.OK)
        .assertMediaType(MediaTypes.TEXT_X_SPEECH)
        .assertEntity("wat?");

    route
        .run(HttpRequest.GET("/nummer?n=5"))
        .assertStatusCode(StatusCodes.OK)
        .assertEntityBytes(ByteString.fromString("fünf", "utf8"));

    route
        .run(
            HttpRequest.GET("/nummer?n=5")
                .addHeader(AcceptCharset.create(HttpCharsets.ISO_8859_1.toRange())))
        .assertStatusCode(StatusCodes.OK)
        .assertEntityBytes(ByteString.fromString("fünf", "ISO-8859-1"));
  }

  @Test
  public void testCustomToByteStringMarshaller() {
    final Marshaller<Integer, RequestEntity> numberAsJsonListMarshaller =
        Marshaller.wrapEntity(
            (Integer param) -> {
              switch (param) {
                case 1:
                  return ByteString.fromString("[1]");
                case 5:
                  return ByteString.fromString("[1,2,3,4,5]");
                default:
                  return ByteString.fromString("[]");
              }
            },
            Marshaller.byteStringToEntity(),
            MediaTypes.APPLICATION_JSON);

    final Function<Integer, Route> nummerHandler =
        integer -> completeOK(integer, numberAsJsonListMarshaller);

    TestRoute route =
        testRoute(
            get(
                () ->
                    path(
                        "nummer",
                        () -> parameter(StringUnmarshallers.INTEGER, "n", nummerHandler))));

    route
        .run(HttpRequest.GET("/nummer?n=1"))
        .assertStatusCode(StatusCodes.OK)
        .assertMediaType(MediaTypes.APPLICATION_JSON)
        .assertEntity("[1]");

    route
        .run(HttpRequest.GET("/nummer?n=5"))
        .assertStatusCode(StatusCodes.OK)
        .assertEntity("[1,2,3,4,5]");

    route
        .run(
            HttpRequest.GET("/nummer?n=5")
                .addHeader(Accept.create(MediaTypes.TEXT_PLAIN.toRange())))
        .assertStatusCode(StatusCodes.NOT_ACCEPTABLE);
  }

  @Test
  public void testCustomToEntityMarshaller() {
    final Marshaller<Integer, RequestEntity> numberAsJsonListMarshaller =
        Marshaller.withFixedContentType(
            MediaTypes.APPLICATION_JSON.toContentType(),
            (Integer param) -> {
              switch (param) {
                case 1:
                  return HttpEntities.create(MediaTypes.APPLICATION_JSON.toContentType(), "[1]");
                case 5:
                  return HttpEntities.create(
                      MediaTypes.APPLICATION_JSON.toContentType(), "[1,2,3,4,5]");
                default:
                  return HttpEntities.create(MediaTypes.APPLICATION_JSON.toContentType(), "[]");
              }
            });

    final Function<Integer, Route> nummerHandler =
        integer -> completeOK(integer, numberAsJsonListMarshaller);

    TestRoute route =
        testRoute(
            get(() ->
                    path(
                        "nummer", () -> parameter(StringUnmarshallers.INTEGER, "n", nummerHandler)))
                .seal() // needed to get the content negotiation, maybe
            );

    route
        .run(HttpRequest.GET("/nummer?n=1"))
        .assertStatusCode(StatusCodes.OK)
        .assertMediaType(MediaTypes.APPLICATION_JSON)
        .assertEntity("[1]");

    route
        .run(HttpRequest.GET("/nummer?n=5"))
        .assertStatusCode(StatusCodes.OK)
        .assertEntity("[1,2,3,4,5]");

    route
        .run(
            HttpRequest.GET("/nummer?n=5")
                .addHeader(Accept.create(MediaTypes.TEXT_PLAIN.toRange())))
        .assertStatusCode(StatusCodes.NOT_ACCEPTABLE);
  }

  @Test
  public void testCustomToResponseMarshaller() {
    final Marshaller<Integer, HttpResponse> numberAsJsonListMarshaller =
        Marshaller.withFixedContentType(
            MediaTypes.APPLICATION_JSON.toContentType(),
            (Integer param) -> {
              switch (param) {
                case 1:
                  return HttpResponse.create()
                      .withEntity(MediaTypes.APPLICATION_JSON.toContentType(), "[1]");
                case 5:
                  return HttpResponse.create()
                      .withEntity(MediaTypes.APPLICATION_JSON.toContentType(), "[1,2,3,4,5]");
                default:
                  return HttpResponse.create().withStatus(StatusCodes.NOT_FOUND);
              }
            });

    final Function<Integer, Route> nummerHandler =
        integer -> complete(integer, numberAsJsonListMarshaller);

    TestRoute route =
        testRoute(
            get(
                () ->
                    path(
                        "nummer",
                        () -> parameter(StringUnmarshallers.INTEGER, "n", nummerHandler))));

    route
        .run(HttpRequest.GET("/nummer?n=1"))
        .assertStatusCode(StatusCodes.OK)
        .assertMediaType(MediaTypes.APPLICATION_JSON)
        .assertEntity("[1]");

    route
        .run(HttpRequest.GET("/nummer?n=5"))
        .assertStatusCode(StatusCodes.OK)
        .assertEntity("[1,2,3,4,5]");

    route.run(HttpRequest.GET("/nummer?n=6")).assertStatusCode(StatusCodes.NOT_FOUND);

    route
        .run(
            HttpRequest.GET("/nummer?n=5")
                .addHeader(Accept.create(MediaTypes.TEXT_PLAIN.toRange())))
        .assertStatusCode(StatusCodes.NOT_ACCEPTABLE);
  }

  @Test
  public void testOptionMarshaller() {
    Marshaller<Optional<String>, RequestEntity> marshaller =
        Marshaller.optionMarshaller(Marshaller.stringToEntity());

    Supplier<Route> emptyHandler =
        () -> rejectEmptyResponse(() -> complete(StatusCodes.OK, Optional.empty(), marshaller));

    Supplier<Route> notEmptyHandler =
        () -> rejectEmptyResponse(() -> complete(StatusCodes.OK, Optional.of("foo"), marshaller));

    TestRoute route =
        testRoute(
            get(() -> concat(path("notempty", notEmptyHandler), path("empty", emptyHandler))));

    route.run(HttpRequest.GET("/notempty")).assertStatusCode(StatusCodes.OK).assertEntity("foo");

    route.run(HttpRequest.GET("/empty")).assertStatusCode(StatusCodes.NOT_FOUND);
  }
}
