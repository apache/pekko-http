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

import org.apache.pekko.http.javadsl.coding.Coder;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.headers.AcceptEncoding;
import org.apache.pekko.http.javadsl.model.headers.ContentEncoding;
import org.apache.pekko.http.javadsl.model.headers.HttpEncodings;
import org.apache.pekko.util.ByteString;

import org.junit.*;

import org.apache.pekko.http.javadsl.testkit.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.apache.pekko.http.javadsl.server.Directives.*;

public class CodingDirectivesTest extends JUnitRouteTest {

  @Test
  public void testAutomaticEncodingWhenNoEncodingRequested() throws Exception {
    TestRoute route = testRoute(encodeResponse(() -> complete("TestString")));

    TestRouteResult response = route.run(HttpRequest.create());
    response.assertStatusCode(200);

    Assert.assertEquals("TestString", response.entityBytes().utf8String());
  }

  @Test
  public void testAutomaticEncodingWhenDeflateRequested() throws Exception {
    TestRoute route = testRoute(encodeResponse(() -> complete("tester")));

    HttpRequest request =
        HttpRequest.create().addHeader(AcceptEncoding.create(HttpEncodings.DEFLATE));
    TestRouteResult response = route.run(request);
    response
        .assertStatusCode(200)
        .assertHeaderExists(ContentEncoding.create(HttpEncodings.DEFLATE));

    ByteString decompressed =
        Coder.Deflate.decode(response.entityBytes(), materializer())
            .toCompletableFuture()
            .get(3, TimeUnit.SECONDS);
    Assert.assertEquals("tester", decompressed.utf8String());
  }

  @Test
  public void testEncodingWhenDeflateRequestedAndGzipSupported() {
    TestRoute route =
        testRoute(encodeResponseWith(Arrays.asList(Coder.Gzip), () -> complete("tester")));

    HttpRequest request =
        HttpRequest.create().addHeader(AcceptEncoding.create(HttpEncodings.DEFLATE));
    route
        .run(request)
        .assertStatusCode(406)
        .assertEntity(
            "Resource representation is only available with these Content-Encodings:\ngzip");
  }

  @Test
  public void testAutomaticDecoding() {
    TestRoute route = testRoute(decodeRequest(() -> extractEntity(entity -> complete(entity))));

    HttpRequest deflateRequest =
        HttpRequest.POST("/")
            .addHeader(ContentEncoding.create(HttpEncodings.DEFLATE))
            .withEntity(Coder.Deflate.encode(ByteString.fromString("abcdef")));
    route.run(deflateRequest).assertStatusCode(200).assertEntity("abcdef");

    HttpRequest gzipRequest =
        HttpRequest.POST("/")
            .addHeader(ContentEncoding.create(HttpEncodings.GZIP))
            .withEntity(Coder.Gzip.encode(ByteString.fromString("hijklmnopq")));
    route.run(gzipRequest).assertStatusCode(200).assertEntity("hijklmnopq");
  }

  @Test
  public void testGzipDecoding() {
    TestRoute route =
        testRoute(
            decodeRequestWith(
                Collections.singleton(Coder.Gzip),
                () -> extractEntity(entity -> complete(entity))));

    HttpRequest gzipRequest =
        HttpRequest.POST("/")
            .addHeader(ContentEncoding.create(HttpEncodings.GZIP))
            .withEntity(Coder.Gzip.encode(ByteString.fromString("hijklmnopq")));
    route.run(gzipRequest).assertStatusCode(200).assertEntity("hijklmnopq");

    HttpRequest deflateRequest =
        HttpRequest.POST("/")
            .addHeader(ContentEncoding.create(HttpEncodings.DEFLATE))
            .withEntity(Coder.Deflate.encode(ByteString.fromString("abcdef")));
    route
        .run(deflateRequest)
        .assertStatusCode(400)
        .assertEntity("The request's Content-Encoding is not supported. Expected:\ngzip");
  }
}
