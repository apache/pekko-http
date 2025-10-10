/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.http.javadsl.server.directives;

import org.junit.Test;

import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.http.javadsl.testkit.JUnitRouteTest;
import org.apache.pekko.http.javadsl.model.headers.Location;

// #extractScheme
import static org.apache.pekko.http.javadsl.server.Directives.complete;
import static org.apache.pekko.http.javadsl.server.Directives.extractScheme;

// #extractScheme
// #scheme
import static org.apache.pekko.http.javadsl.server.Directives.complete;
import static org.apache.pekko.http.javadsl.server.Directives.extract;
import static org.apache.pekko.http.javadsl.server.Directives.redirect;
import static org.apache.pekko.http.javadsl.server.Directives.scheme;

// #scheme

public class SchemeDirectivesExamplesTest extends JUnitRouteTest {

  @Test
  public void testScheme() {
    // #extractScheme
    final Route route =
        extractScheme((scheme) -> complete(String.format("The scheme is '%s'", scheme)));
    testRoute(route)
        .run(HttpRequest.GET("https://www.example.com/"))
        .assertEntity("The scheme is 'https'");
    // #extractScheme
  }

  @Test
  public void testRedirection() {
    // #scheme
    final Route route =
        concat(
            scheme(
                "http",
                () ->
                    extract(
                        (ctx) -> ctx.getRequest().getUri(),
                        (uri) -> redirect(uri.scheme("https"), StatusCodes.MOVED_PERMANENTLY))),
            scheme("https", () -> complete("Safe and secure!")));

    testRoute(route)
        .run(HttpRequest.GET("http://www.example.com/hello"))
        .assertStatusCode(StatusCodes.MOVED_PERMANENTLY)
        .assertHeaderExists(Location.create("https://www.example.com/hello"));

    testRoute(route)
        .run(HttpRequest.GET("https://www.example.com/hello"))
        .assertEntity("Safe and secure!");
    // #scheme
  }
}
