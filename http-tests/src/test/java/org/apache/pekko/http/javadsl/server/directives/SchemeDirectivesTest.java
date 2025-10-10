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
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.model.Uri;
import org.apache.pekko.http.javadsl.server.Directives;
import org.apache.pekko.http.javadsl.testkit.JUnitRouteTest;
import org.apache.pekko.http.javadsl.testkit.TestRoute;
import org.junit.Test;

import static org.apache.pekko.http.javadsl.server.Directives.*;

public class SchemeDirectivesTest extends JUnitRouteTest {
  @Test
  public void testSchemeFilter() {
    TestRoute route = testRoute(scheme("http", () -> complete("OK!")));

    route
        .run(HttpRequest.create().withUri(Uri.create("http://example.org")))
        .assertStatusCode(StatusCodes.OK)
        .assertEntity("OK!");

    route
        .run(HttpRequest.create().withUri(Uri.create("https://example.org")))
        .assertStatusCode(StatusCodes.BAD_REQUEST)
        .assertEntity("Uri scheme not allowed, supported schemes: http");
  }

  @Test
  public void testSchemeExtraction() {
    TestRoute route = testRoute(extractScheme(Directives::complete));

    route
        .run(HttpRequest.create().withUri(Uri.create("http://example.org")))
        .assertStatusCode(StatusCodes.OK)
        .assertEntity("http");

    route
        .run(HttpRequest.create().withUri(Uri.create("https://example.org")))
        .assertStatusCode(StatusCodes.OK)
        .assertEntity("https");
  }
}
