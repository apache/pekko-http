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

package docs.http.javadsl.server.testkit;

// #simple-app-testing
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.testkit.JUnitRouteTest;
import org.apache.pekko.http.javadsl.testkit.TestRoute;
import org.junit.Test;

public class TestkitExampleTest extends JUnitRouteTest {
  TestRoute appRoute = testRoute(new MyAppService().createRoute());

  @Test
  public void testCalculatorAdd() {
    // test happy path
    appRoute
        .run(HttpRequest.GET("/calculator/add?x=4.2&y=2.3"))
        .assertStatusCode(200)
        .assertEntity("x + y = 6.5");

    // test responses to potential errors
    appRoute
        .run(HttpRequest.GET("/calculator/add?x=3.2"))
        .assertStatusCode(StatusCodes.NOT_FOUND) // 404
        .assertEntity("Request is missing required query parameter 'y'");

    // test responses to potential errors
    appRoute
        .run(HttpRequest.GET("/calculator/add?x=3.2&y=three"))
        .assertStatusCode(StatusCodes.BAD_REQUEST)
        .assertEntity(
            "The query parameter 'y' was malformed:\n"
                + "'three' is not a valid 64-bit floating point value");
  }
}
// #simple-app-testing
