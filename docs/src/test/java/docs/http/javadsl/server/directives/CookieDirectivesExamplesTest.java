/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.http.javadsl.server.directives;

import org.apache.pekko.http.javadsl.model.HttpHeader;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.headers.Cookie;
import org.apache.pekko.http.javadsl.model.headers.HttpCookie;
import org.apache.pekko.http.javadsl.model.headers.SetCookie;
import org.apache.pekko.http.javadsl.server.Rejections;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.http.javadsl.testkit.JUnitRouteTest;
import org.apache.pekko.http.scaladsl.model.DateTime;
import org.junit.Test;

import java.util.Optional;
import java.util.OptionalLong;

// #cookie
import static org.apache.pekko.http.javadsl.server.Directives.complete;
import static org.apache.pekko.http.javadsl.server.Directives.cookie;

// #cookie
// #optionalCookie
import static org.apache.pekko.http.javadsl.server.Directives.complete;
import static org.apache.pekko.http.javadsl.server.Directives.optionalCookie;

// #optionalCookie
// #deleteCookie
import static org.apache.pekko.http.javadsl.server.Directives.complete;
import static org.apache.pekko.http.javadsl.server.Directives.deleteCookie;

// #deleteCookie
// #setCookie
import static org.apache.pekko.http.javadsl.server.Directives.complete;
import static org.apache.pekko.http.javadsl.server.Directives.setCookie;

// #setCookie
public class CookieDirectivesExamplesTest extends JUnitRouteTest {

  @Test
  public void testCookie() {
    // #cookie
    final Route route =
        cookie(
            "userName",
            nameCookie -> complete("The logged in user is '" + nameCookie.value() + "'"));

    // tests:
    testRoute(route)
        .run(HttpRequest.GET("/").addHeader(Cookie.create("userName", "paul")))
        .assertEntity("The logged in user is 'paul'");
    // missing cookie
    runRouteUnSealed(route, HttpRequest.GET("/"))
        .assertRejections(Rejections.missingCookie("userName"));
    testRoute(route)
        .run(HttpRequest.GET("/"))
        .assertEntity("Request is missing required cookie 'userName'");
    // #cookie
  }

  @Test
  public void testOptionalCookie() {
    // #optionalCookie
    final Route route =
        optionalCookie(
            "userName",
            optNameCookie -> {
              if (optNameCookie.isPresent()) {
                return complete("The logged in user is '" + optNameCookie.get().value() + "'");
              } else {
                return complete("No user logged in");
              }
            });

    // tests:
    testRoute(route)
        .run(HttpRequest.GET("/").addHeader(Cookie.create("userName", "paul")))
        .assertEntity("The logged in user is 'paul'");
    testRoute(route).run(HttpRequest.GET("/")).assertEntity("No user logged in");
    // #optionalCookie
  }

  @Test
  public void testDeleteCookie() {
    // #deleteCookie
    final Route route = deleteCookie("userName", () -> complete("The user was logged out"));

    // tests:
    final HttpHeader expected =
        SetCookie.create(
            HttpCookie.create(
                "userName",
                "deleted",
                Optional.of(DateTime.MinValue()),
                OptionalLong.empty(),
                Optional.empty(),
                Optional.empty(),
                false,
                false,
                Optional.empty(),
                Optional.empty()));

    testRoute(route)
        .run(HttpRequest.GET("/"))
        .assertEntity("The user was logged out")
        .assertHeaderExists(expected);
    // #deleteCookie
  }

  @Test
  public void testSetCookie() {
    // #setCookie
    final Route route =
        setCookie(HttpCookie.create("userName", "paul"), () -> complete("The user was logged in"));

    // tests:
    final HttpHeader expected = SetCookie.create(HttpCookie.create("userName", "paul"));

    testRoute(route)
        .run(HttpRequest.GET("/"))
        .assertEntity("The user was logged in")
        .assertHeaderExists(expected);
    // #setCookie
  }
}
