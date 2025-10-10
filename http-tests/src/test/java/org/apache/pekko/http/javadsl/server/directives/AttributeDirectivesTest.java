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

import org.apache.pekko.http.javadsl.model.AttributeKey;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.testkit.JUnitRouteTest;
import org.apache.pekko.http.javadsl.testkit.TestRoute;

import org.junit.Test;

public class AttributeDirectivesTest extends JUnitRouteTest {
  AttributeKey<String> key = AttributeKey.create("my-key", String.class);

  @Test
  public void testAttribute() {
    TestRoute route =
        testRoute(attribute(key, value -> complete("Completed with value [" + value + "]")));

    route
        .run(HttpRequest.create().addAttribute(key, "the-value"))
        .assertStatusCode(StatusCodes.OK)
        .assertEntity("Completed with value [the-value]");

    // A missing attribute is a programming error:
    route.run(HttpRequest.create()).assertStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
  }

  @Test
  public void testOptionalHeaderValueByName() {
    TestRoute route = testRoute(optionalAttribute(key, (opt) -> complete(opt.toString())));

    route
        .run(HttpRequest.create().addAttribute(key, "the-value"))
        .assertStatusCode(StatusCodes.OK)
        .assertEntity("Optional[the-value]");

    route.run(HttpRequest.create()).assertStatusCode(StatusCodes.OK).assertEntity("Optional.empty");
  }
}
