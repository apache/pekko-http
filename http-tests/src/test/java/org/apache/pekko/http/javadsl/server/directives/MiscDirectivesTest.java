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
import org.apache.pekko.http.javadsl.model.RemoteAddress;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.model.Uri;
import org.apache.pekko.http.javadsl.model.headers.XForwardedFor;
import org.apache.pekko.http.javadsl.model.headers.XRealIp;
import org.apache.pekko.http.javadsl.unmarshalling.Unmarshaller;
import org.apache.pekko.http.javadsl.testkit.JUnitRouteTest;
import org.apache.pekko.http.javadsl.testkit.TestRoute;
import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

import static org.apache.pekko.http.javadsl.server.Directives.*;

public class MiscDirectivesTest extends JUnitRouteTest {

  static boolean isShort(String str) {
    return str.length() < 5;
  }

  static boolean hasShortPath(Uri uri) {
    return uri.path().toString().length() < 5;
  }

  @Test
  public void testValidateUri() {
    TestRoute route =
        testRoute(
            extractUri(
                uri -> validate(() -> hasShortPath(uri), "Path too long!", () -> complete("OK!"))));

    route
        .run(HttpRequest.create().withUri(Uri.create("/abc")))
        .assertStatusCode(StatusCodes.OK)
        .assertEntity("OK!");

    route
        .run(HttpRequest.create().withUri(Uri.create("/abcdefghijkl")))
        .assertStatusCode(StatusCodes.BAD_REQUEST)
        .assertEntity("Path too long!");
  }

  @Test
  public void testClientIpExtraction() throws UnknownHostException {
    TestRoute route = testRoute(extractClientIP(ip -> complete(ip.toString())));

    route
        .run(
            HttpRequest.create()
                .addHeader(
                    XForwardedFor.create(RemoteAddress.create(InetAddress.getByName("127.0.0.2")))))
        .assertStatusCode(StatusCodes.OK)
        .assertEntity("127.0.0.2");

    route
        .run(
            HttpRequest.create()
                .addHeader(
                    XRealIp.create(RemoteAddress.create(InetAddress.getByName("127.0.0.4")))))
        .assertStatusCode(StatusCodes.OK)
        .assertEntity("127.0.0.4");

    route.run(HttpRequest.create()).assertStatusCode(StatusCodes.OK);
  }

  @Test
  public void testWithSizeLimit() {
    TestRoute route =
        testRoute(
            withSizeLimit(
                500, () -> entity(Unmarshaller.entityToString(), (entity) -> complete("ok"))));

    route.run(withEntityOfSize(500)).assertStatusCode(StatusCodes.OK);

    route.run(withEntityOfSize(501)).assertStatusCode(StatusCodes.CONTENT_TOO_LARGE);
  }

  private HttpRequest withEntityOfSize(int sizeLimit) {
    char[] charArray = new char[sizeLimit];
    Arrays.fill(charArray, '0');
    return HttpRequest.POST("/").withEntity(new String(charArray));
  }
}
