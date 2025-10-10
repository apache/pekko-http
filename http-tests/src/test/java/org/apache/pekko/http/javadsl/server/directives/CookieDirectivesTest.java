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

import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.headers.HttpCookie;
import org.apache.pekko.http.javadsl.testkit.JUnitRouteTest;
import org.apache.pekko.http.javadsl.testkit.TestRoute;
import org.junit.Test;

import static org.apache.pekko.http.javadsl.server.Directives.*;

public class CookieDirectivesTest extends JUnitRouteTest {
  @Test
  public void testCookieValue() {
    TestRoute route = testRoute(cookie("userId", userId -> complete(userId.value())));

    route
        .run(HttpRequest.create())
        .assertStatusCode(400)
        .assertEntity("Request is missing required cookie 'userId'");

    route
        .run(
            HttpRequest.create()
                .addHeader(
                    org.apache.pekko.http.javadsl.model.headers.Cookie.create("userId", "12345")))
        .assertStatusCode(200)
        .assertEntity("12345");
  }

  @Test
  public void testCookieOptionalValue() {
    TestRoute route = testRoute(optionalCookie("userId", opt -> complete(opt.toString())));

    route.run(HttpRequest.create()).assertStatusCode(200).assertEntity("Optional.empty");

    route
        .run(
            HttpRequest.create()
                .addHeader(
                    org.apache.pekko.http.javadsl.model.headers.Cookie.create("userId", "12345")))
        .assertStatusCode(200)
        .assertEntity("Optional[userId=12345]");
  }

  @Test
  public void testCookieSet() {
    TestRoute route =
        testRoute(setCookie(HttpCookie.create("userId", "12"), () -> complete("OK!")));

    route
        .run(HttpRequest.create())
        .assertStatusCode(200)
        .assertHeaderExists("Set-Cookie", "userId=12")
        .assertEntity("OK!");
  }

  @Test
  public void testDeleteCookie() {
    TestRoute route = testRoute(deleteCookie("userId", () -> complete("OK!")));

    route
        .run(HttpRequest.create())
        .assertStatusCode(200)
        .assertHeaderExists("Set-Cookie", "userId=deleted; Expires=Wed, 01 Jan 1800 00:00:00 GMT")
        .assertEntity("OK!");
  }
}
