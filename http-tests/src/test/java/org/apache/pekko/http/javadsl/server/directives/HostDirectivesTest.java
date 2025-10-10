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

import java.util.ArrayList;
import java.util.regex.Pattern;

import static org.apache.pekko.http.javadsl.server.Directives.*;

public class HostDirectivesTest extends JUnitRouteTest {
  @Test
  public void testHostFilterBySingleName() {
    TestRoute route = testRoute(host("example.org", () -> complete("OK!")));

    route
        .run(HttpRequest.create().withUri(Uri.create("http://example.org")))
        .assertStatusCode(StatusCodes.OK)
        .assertEntity("OK!");

    route
        .run(HttpRequest.create().withUri(Uri.create("https://other.org")))
        .assertStatusCode(StatusCodes.NOT_FOUND);
  }

  @Test
  public void testHostFilterByNames() {
    ArrayList<String> hosts = new ArrayList<String>();
    hosts.add("example.org");
    hosts.add("example2.org");
    TestRoute route = testRoute(host(hosts, () -> complete("OK!")));

    route
        .run(HttpRequest.create().withUri(Uri.create("http://example.org")))
        .assertStatusCode(StatusCodes.OK)
        .assertEntity("OK!");

    route
        .run(HttpRequest.create().withUri(Uri.create("http://example2.org")))
        .assertStatusCode(StatusCodes.OK)
        .assertEntity("OK!");

    route.run(HttpRequest.create().withUri(Uri.create("https://other.org"))).assertStatusCode(404);
  }

  @Test
  public void testHostFilterByPredicate() {
    TestRoute route =
        testRoute(host(hostName -> hostName.contains("ample"), () -> complete("OK!")));

    route
        .run(HttpRequest.create().withUri(Uri.create("http://example.org")))
        .assertStatusCode(StatusCodes.OK)
        .assertEntity("OK!");

    route
        .run(HttpRequest.create().withUri(Uri.create("https://other.org")))
        .assertStatusCode(StatusCodes.NOT_FOUND);
  }

  @Test
  public void testHostExtraction() {
    TestRoute route = testRoute(extractHost(Directives::complete));

    route
        .run(HttpRequest.create().withUri(Uri.create("http://example.org")))
        .assertStatusCode(StatusCodes.OK)
        .assertEntity("example.org");
  }

  @Test
  public void testHostPatternExtraction() {
    TestRoute route = testRoute(host(Pattern.compile(".*\\.([^.]*)"), Directives::complete));

    route
        .run(HttpRequest.create().withUri(Uri.create("http://example.org")))
        .assertStatusCode(StatusCodes.OK)
        .assertEntity("org");

    route
        .run(HttpRequest.create().withUri(Uri.create("http://example.de")))
        .assertStatusCode(StatusCodes.OK)
        .assertEntity("de");
  }
}
