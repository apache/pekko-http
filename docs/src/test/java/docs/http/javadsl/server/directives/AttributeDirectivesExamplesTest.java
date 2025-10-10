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

import org.apache.pekko.http.javadsl.model.AttributeKey;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.http.javadsl.testkit.JUnitRouteTest;
import org.junit.Test;

public class AttributeDirectivesExamplesTest extends JUnitRouteTest {

  @Test
  public void attribute() {
    // #attribute
    AttributeKey<String> userId = AttributeKey.create("user-id", String.class);

    final Route route = attribute(userId, id -> complete("The user is " + id));

    // tests:
    testRoute(route)
        .run(HttpRequest.GET("/").addAttribute(userId, "Joe42"))
        .assertEntity("The user is Joe42");

    testRoute(route).run(HttpRequest.GET("/")).assertStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
    // #attribute
  }

  @Test
  public void testOptionalAttribute() {
    // #optionalAttribute
    AttributeKey<String> userId = AttributeKey.create("user-id", String.class);

    final Route route =
        optionalAttribute(
            userId,
            id -> {
              if (id.isPresent()) {
                return complete("The user is " + id.get());
              } else {
                return complete("No user was provided");
              }
            });

    // tests:
    testRoute(route)
        .run(HttpRequest.GET("/").addAttribute(userId, "Joe42"))
        .assertEntity("The user is Joe42");

    testRoute(route).run(HttpRequest.GET("/")).assertEntity("No user was provided");
    // #optionalAttribute
  }
}
