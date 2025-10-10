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

package docs.http.javadsl.server.directives;

import org.apache.pekko.http.javadsl.model.HttpHeader;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.headers.HttpOrigin;
import org.apache.pekko.http.javadsl.model.headers.Origin;
import org.apache.pekko.http.javadsl.model.headers.RawHeader;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.http.javadsl.testkit.JUnitRouteTest;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

// #multiple-headers
import static org.apache.pekko.http.javadsl.server.Directives.complete;
import static org.apache.pekko.http.javadsl.server.Directives.respondWithDefaultHeaders;

// #multiple-headers
// #respondWithHeader
import static org.apache.pekko.http.javadsl.server.Directives.complete;
import static org.apache.pekko.http.javadsl.server.Directives.path;
import static org.apache.pekko.http.javadsl.server.Directives.respondWithDefaultHeader;

// #respondWithHeader
// #respondWithDefaultHeader
import static org.apache.pekko.http.javadsl.server.Directives.complete;
import static org.apache.pekko.http.javadsl.server.Directives.path;
import static org.apache.pekko.http.javadsl.server.Directives.respondWithDefaultHeader;
import static org.apache.pekko.http.javadsl.server.Directives.respondWithHeader;

// #respondWithDefaultHeader
// #respondWithHeaders
import static org.apache.pekko.http.javadsl.server.Directives.complete;
import static org.apache.pekko.http.javadsl.server.Directives.path;
import static org.apache.pekko.http.javadsl.server.Directives.respondWithDefaultHeaders;
import static org.apache.pekko.http.javadsl.server.Directives.respondWithHeaders;

// #respondWithHeaders
// #respondWithDefaultHeaders
import static org.apache.pekko.http.javadsl.server.Directives.complete;
import static org.apache.pekko.http.javadsl.server.Directives.path;
import static org.apache.pekko.http.javadsl.server.Directives.respondWithDefaultHeaders;
import static org.apache.pekko.http.javadsl.server.Directives.respondWithHeader;

// #respondWithDefaultHeaders
public class RespondWithDirectivesExamplesTest extends JUnitRouteTest {

  @Test
  public void testMultipleHeaders() {
    // #multiple-headers
    final List<HttpHeader> headers =
        Arrays.asList(
            Origin.create(HttpOrigin.parse("http://pekko.apache.org")),
            RawHeader.create("X-Fish-Name", "Blippy"));
    respondWithDefaultHeaders(
        headers,
        () ->
            /*...*/
            complete("Blip!"));
    // #multiple-headers
  }

  @Test
  public void testRespondWithHeader() {
    // #respondWithHeader
    final Route route =
        path(
            "foo",
            () ->
                respondWithHeader(
                    RawHeader.create("Funky-Muppet", "gonzo"), () -> complete("beep")));

    testRoute(route)
        .run(HttpRequest.GET("/foo"))
        .assertHeaderExists("Funky-Muppet", "gonzo")
        .assertEntity("beep");
    // #respondWithHeader
  }

  @Test
  public void testRespondWithDefaultHeader() {
    // #respondWithDefaultHeader
    // custom headers
    final RawHeader blippy = RawHeader.create("X-Fish-Name", "Blippy");
    final RawHeader elTonno = RawHeader.create("X-Fish-Name", "El Tonno");

    // format: OFF
    // by default always include the Blippy header,
    // unless a more specific X-Fish-Name is given by the inner route
    final Route route =
        respondWithDefaultHeader(
            blippy,
            () -> // blippy
            respondWithHeader(
                        elTonno,
                        () -> // / el tonno
                        path(
                                    "el-tonno",
                                    () -> // | /
                                    complete("¡Ay blippy!") // | |- el tonno
                                    )
                                .orElse( // | |
                                    path(
                                        "los-tonnos",
                                        () -> // | |
                                        complete("¡Ay ay blippy!") // | |- el tonno
                                        ) // | |
                                    ) // | |
                        )
                    .orElse( // | x
                        complete("Blip!") // |- blippy
                        ) // x
            );
    // format: ON

    testRoute(route)
        .run(HttpRequest.GET("/"))
        .assertHeaderExists("X-Fish-Name", "Blippy")
        .assertEntity("Blip!");

    testRoute(route)
        .run(HttpRequest.GET("/el-tonno"))
        .assertHeaderExists("X-Fish-Name", "El Tonno")
        .assertEntity("¡Ay blippy!");

    testRoute(route)
        .run(HttpRequest.GET("/los-tonnos"))
        .assertHeaderExists("X-Fish-Name", "El Tonno")
        .assertEntity("¡Ay ay blippy!");
    // #respondWithDefaultHeader
  }

  @Test
  public void testRespondWithHeaders() {
    // #respondWithHeaders
    final HttpHeader gonzo = RawHeader.create("Funky-Muppet", "gonzo");
    final HttpHeader pekko = Origin.create(HttpOrigin.parse("http://pekko.apache.org"));

    final Route route =
        path("foo", () -> respondWithHeaders(Arrays.asList(gonzo, pekko), () -> complete("beep")));

    testRoute(route)
        .run(HttpRequest.GET("/foo"))
        .assertHeaderExists("Funky-Muppet", "gonzo")
        .assertHeaderExists("Origin", "http://pekko.apache.org")
        .assertEntity("beep");

    // #respondWithHeaders
  }

  @Test
  public void testRespondWithDefaultHeaders() {
    // #respondWithDefaultHeaders
    // custom headers
    final RawHeader blippy = RawHeader.create("X-Fish-Name", "Blippy");
    final HttpHeader pekko = Origin.create(HttpOrigin.parse("http://pekko.apache.org"));
    final List<HttpHeader> defaultHeaders = Arrays.asList(blippy, pekko);
    final RawHeader elTonno = RawHeader.create("X-Fish-Name", "El Tonno");

    // format: OFF
    // by default always include the Blippy and Pekko headers,
    // unless a more specific X-Fish-Name is given by the inner route
    final Route route =
        respondWithDefaultHeaders(
            defaultHeaders,
            () -> // blippy and akka
            respondWithHeader(
                        elTonno,
                        () -> // / el tonno
                        path(
                                    "el-tonno",
                                    () -> // | /
                                    complete("¡Ay blippy!") // | |- el tonno
                                    )
                                .orElse( // | |
                                    path(
                                        "los-tonnos",
                                        () -> // | |
                                        complete("¡Ay ay blippy!") // | |- el tonno
                                        ) // | |
                                    ) // | |
                        )
                    .orElse( // | x
                        complete("Blip!") // |- blippy and akka
                        ) // x
            );
    // format: ON

    testRoute(route)
        .run(HttpRequest.GET("/"))
        .assertHeaderExists("X-Fish-Name", "Blippy")
        .assertHeaderExists("Origin", "http://pekko.apache.org")
        .assertEntity("Blip!");

    testRoute(route)
        .run(HttpRequest.GET("/el-tonno"))
        .assertHeaderExists("X-Fish-Name", "El Tonno")
        .assertEntity("¡Ay blippy!");

    testRoute(route)
        .run(HttpRequest.GET("/los-tonnos"))
        .assertHeaderExists("X-Fish-Name", "El Tonno")
        .assertEntity("¡Ay ay blippy!");
    // #respondWithDefaultHeaders
  }
}
